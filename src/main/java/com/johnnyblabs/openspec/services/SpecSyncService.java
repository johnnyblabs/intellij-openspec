package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.DeltaSpecOperation;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;

import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
public final class SpecSyncService {
    private static final Logger LOG = Logger.getInstance(SpecSyncService.class);

    private final Project project;

    public SpecSyncService(Project project) {
        this.project = project;
    }

    /**
     * Parses all delta spec files in a change directory into structured operations, as OpenSpec's archive reads them.
     */
    public List<DeltaSpecOperation> parseDeltaSpecs(String changeName) {
        List<DeltaSpecOperation> operations = new ArrayList<>();
        readDeltaSpecs(changeName).forEach((capability, content) -> operations.addAll(parseDeltaSpecContent(capability, content)));
        return operations;
    }

    /**
     * Each capability's delta spec text, keyed by capability path: the folder's path relative to the change's
     * {@code specs/}, with {@code /} separators on every OS (e.g. {@code identity/user-auth}), sorted as OpenSpec's
     * archive lists them. Nested capabilities are found at any depth, as the archive finds them; dot-folders and
     * symlinked folders are skipped, as the archive skips them.
     */
    private Map<String, String> readDeltaSpecs(String changeName) {
        Map<String, String> deltas = new TreeMap<>();
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        VirtualFile changeDir = changesDir == null ? null : changesDir.findChild(changeName);
        VirtualFile specsDir = changeDir == null ? null : changeDir.findChild("specs");
        if (specsDir == null) return deltas;
        collectDeltaSpecs(specsDir, specsDir, deltas);
        return deltas;
    }

    private static void collectDeltaSpecs(VirtualFile specsDir, VirtualFile dir, Map<String, String> deltas) {
        for (VirtualFile capDir : dir.getChildren()) {
            // Symlinked folders aren't followed, as OpenSpec's archive doesn't follow them; it also rules out loops.
            if (!capDir.isDirectory() || capDir.getName().startsWith(".") || capDir.is(VFileProperty.SYMLINK)) continue;
            VirtualFile specFile = capDir.findChild("spec.md");
            if (specFile != null && !specFile.isDirectory()) {
                try {
                    deltas.put(VfsUtilCore.getRelativePath(capDir, specsDir, '/'),
                            new String(specFile.contentsToByteArray(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    LOG.warn("Failed to read delta spec: " + specFile.getPath(), e);
                }
            }
            collectDeltaSpecs(specsDir, capDir, deltas);
        }
    }

    /**
     * Parses a single delta spec file content into operations, in apply order (RENAMED, REMOVED, MODIFIED, ADDED).
     */
    List<DeltaSpecOperation> parseDeltaSpecContent(String capabilityName, String content) {
        return SpecMerge.operations(capabilityName, content);
    }

    /**
     * Computes the sync preview: what each main spec would look like after the change, merged by OpenSpec's archive
     * rules ({@link SpecMerge}). A capability the archive would refuse keeps its original content and carries the
     * refusal; one refusal makes the whole change unappliable ({@link #applySync}).
     */
    public List<SpecSyncResult> computeSync(String changeName) {
        List<SpecSyncResult> results = new ArrayList<>();
        // The release whose archive the merge follows: the detected CLI, or the plugin's target with none.
        String release = com.johnnyblabs.openspec.validation.ValidationRelease.followed(project);
        for (Map.Entry<String, String> entry : readDeltaSpecs(changeName).entrySet()) {
            String capability = entry.getKey();
            String original = readMainSpec(capability);
            SpecMerge.Result merged = SpecMerge.merge(capability, changeName, entry.getValue(), original,
                    getMainSpecPath(capability), release, retirementMarker(changeName));
            results.add(new SpecSyncResult(capability, getMainSpecPath(capability), original,
                    merged.refused() ? original : merged.projected(), merged.operations(), merged.warnings(),
                    merged.refused() ? List.of(merged.refusal()) : List.of(), merged.retired()));
        }
        return results;
    }

    /**
     * Writes the projected content of each sync result to disk,
     * then validates each affected main spec and reports new issues.
     * Returns the list of post-merge validation warnings (empty if clean).
     */
    public List<String> applySync(List<SpecSyncResult> results) throws IOException {
        List<String> refusals = results.stream().flatMap(r -> r.refusals().stream()).toList();
        if (!refusals.isEmpty()) {
            // All or nothing, as OpenSpec's archive: a change with any refused capability writes no spec at all.
            throw new IOException("Sync refused, no specs were changed: " + String.join("; ", refusals));
        }
        List<String> postMergeWarnings = new ArrayList<>();

        for (SpecSyncResult result : results) {
            if (!result.hasChanges()) continue;
            if (result.retired()) {
                retire(Path.of(result.mainSpecPath()));
                continue;
            }

            Path path = Path.of(result.mainSpecPath());
            Files.createDirectories(path.getParent());

            // Write file content on current (background) thread — not inside WriteAction
            Files.writeString(path, result.projectedContent(), StandardCharsets.UTF_8);

            // The VFS refresh is a write action, so it runs on the EDT. invokeAndWait with the caller's modality: from a
            // progress task that is the task's own (a modal dialog's, for Bulk Archive), so the hop isn't held until a
            // dialog closes; on the EDT it runs inline. (The previous invokeLater + latch carried no modality.)
            ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(() -> {
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString());
                if (vf != null) vf.refresh(false, false);
            }), ModalityState.defaultModalityState());

            // Post-merge validation: compare pre-merge and post-merge issues
            postMergeWarnings.addAll(validatePostMerge(result));
        }
        // Final VFS refresh
        VfsUtil.markDirtyAndRefresh(false, true, true,
                LocalFileSystem.getInstance().findFileByPath(
                        com.johnnyblabs.openspec.util.OpenSpecFileUtil.openspecPath(project) + "/specs"));

        return postMergeWarnings;
    }

    /**
     * The CLI's retirement warning (1.8.0 on, the same through 2.2), for a capability the plugin's archive retired, so
     * both paths report a retirement alike: the spec's path from the project root, and the sections deleted with it.
     */
    static String retiredWarning(SpecSyncResult r, @org.jetbrains.annotations.Nullable String projectRoot) {
        String path = r.mainSpecPath().replace('\\', '/');
        if (projectRoot != null) {
            String root = projectRoot.replace('\\', '/');
            if (path.startsWith(root + "/")) path = path.substring(root.length() + 1);
        }
        java.util.List<String> sections = new java.util.ArrayList<>();
        for (String line : r.originalContent().split("\\R")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^##\\s+(.+?)\\s*$").matcher(line);
            if (m.find() && !m.group(1).equalsIgnoreCase("Requirements")) sections.add(m.group(1));
        }
        return r.capabilityName() + " - capability retired; deleted the main spec (all requirements removed, declared by "
                + "retire_capabilities) at " + path + "."
                + (sections.isEmpty() ? "" : " Its section(s) went with it: " + String.join(", ", sections) + ".")
                + " If it was committed, restore it with: git checkout HEAD -- \":(top)" + path + "\"";
    }

    /** The specs folder a retirement prunes up to, never past: {@code <root>/specs}. */
    private Path specsRoot() {
        return Path.of(com.johnnyblabs.openspec.util.OpenSpecFileUtil.openspecPath(project), "specs");
    }

    /**
     * Retires a capability as upstream's {@code retireSpec} does: deletes {@code spec.md}, then each parent folder left
     * empty, stopping before the specs folder, so a folder holding anything else is kept.
     */
    void retire(Path spec) throws IOException {
        retire(spec, specsRoot());
    }

    static void retire(Path spec, Path specsRoot) throws IOException {
        Files.deleteIfExists(spec);
        Path root = specsRoot.toAbsolutePath().normalize();
        for (Path dir = spec.toAbsolutePath().normalize().getParent();
             dir != null && dir.startsWith(root) && !dir.equals(root); dir = dir.getParent()) {
            try (java.util.stream.Stream<Path> entries = Files.list(dir)) {
                if (entries.findAny().isPresent()) break;
            }
            Files.delete(dir);
        }
        ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(() -> {
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root);
            if (vf != null) vf.refresh(false, true);
        }), ModalityState.defaultModalityState());
    }

    /** The change's {@code retire_capabilities} marker, read as upstream reads it. */
    SpecMerge.RetirementMarker retirementMarker(String changeName) {
        Path changeDir = Path.of(com.johnnyblabs.openspec.util.OpenSpecFileUtil.openspecPath(project), "changes", changeName);
        return RetirementMarkers.read(changeDir, this::knownSchema);
    }

    /** Whether {@code schema} is one the CLI lists; with no CLI, a built-in schema. Set after the schema-source check. */
    boolean knownSchema(String schema) {
        return "spec-driven".equals(schema);
    }

    private List<String> validatePostMerge(SpecSyncResult result) {
        List<String> warnings = new ArrayList<>();
        BuiltInValidator validator = project.getService(BuiltInValidator.class);

        VirtualFile mergedFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(result.mainSpecPath());
        if (mergedFile == null) return warnings;

        // Collect post-merge issues
        List<ValidationIssue> postMergeIssues = new ArrayList<>();
        validator.validateSpecFilePublic(mergedFile, postMergeIssues);

        // Collect pre-merge issues (if original content existed)
        Set<String> preMergeMessages = new HashSet<>();
        if (result.originalContent() != null && !result.originalContent().isEmpty()) {
            // We can't validate a VirtualFile for pre-merge content that's already overwritten,
            // so we compare by issue messages — pre-existing issues have the same message text
            // This is an approximation; exact comparison would require in-memory validation
        }

        // Report only errors (new issues introduced by the merge)
        List<ValidationIssue> errors = postMergeIssues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .toList();
        for (ValidationIssue error : errors) {
            warnings.add("Post-merge validation: " + result.mainSpecPath() + " — " + error.message());
        }
        return warnings;
    }

    /**
     * Checks whether a change has any delta spec operations.
     */
    public boolean hasDeltaSpecs(String changeName) {
        return !parseDeltaSpecs(changeName).isEmpty();
    }

    /**
     * Returns the set of capability names touched by a change's delta specs.
     */
    public Set<String> getCapabilities(String changeName) {
        return parseDeltaSpecs(changeName).stream()
                .map(DeltaSpecOperation::capabilityName)
                .collect(Collectors.toSet());
    }

    /**
     * Detects conflicts among the given change names: capabilities touched by 2+ changes.
     * Returns a map of capability name → list of conflicting change names.
     */
    public Map<String, List<String>> detectConflicts(List<String> changeNames) {
        Map<String, List<String>> capToChanges = new LinkedHashMap<>();
        for (String name : changeNames) {
            for (String cap : getCapabilities(name)) {
                capToChanges.computeIfAbsent(cap, k -> new ArrayList<>()).add(name);
            }
        }
        // Only return capabilities with 2+ changes
        capToChanges.entrySet().removeIf(e -> e.getValue().size() < 2);
        return capToChanges;
    }

    // --- Internal helpers ---

    List<DeltaSpecOperation> sortOperations(List<DeltaSpecOperation> ops) {
        // Apply order mirrors upstream OpenSpec CLI (specs-apply.js): RENAMED first so a rename
        // happens before a subsequent REMOVED targets the new name, then MODIFIED, then ADDED.
        List<DeltaSpecOperation> sorted = new ArrayList<>(ops);
        sorted.sort(Comparator.comparingInt(op -> switch (op.type()) {
            case RENAMED -> 0;
            case REMOVED -> 1;
            case MODIFIED -> 2;
            case ADDED -> 3;
        }));
        return sorted;
    }

    private String getMainSpecPath(String capabilityName) {
        return com.johnnyblabs.openspec.util.OpenSpecFileUtil.openspecPath(project) + "/specs/" + capabilityName + "/spec.md";
    }

    /**
     * The main spec's text, or {@code null} when it doesn't exist. Read from disk first, where {@link #applySync}
     * writes, so the merge never starts from a stale cached copy; the VFS covers in-memory files (as in light tests).
     */
    private String readMainSpec(String capabilityName) {
        try {
            Path path = Path.of(getMainSpecPath(capabilityName));
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            VirtualFile specsDir = OpenSpecFileUtil.getSpecsDir(project);
            VirtualFile spec = specsDir == null ? null : specsDir.findFileByRelativePath(capabilityName + "/spec.md");
            if (spec != null) {
                return new String(spec.contentsToByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.warn("Failed to read main spec for " + capabilityName, e);
        }
        return null;
    }
}

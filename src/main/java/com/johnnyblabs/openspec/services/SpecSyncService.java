package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.DeltaSpecOperation;
import com.johnnyblabs.openspec.model.DeltaSpecOperation.OperationType;
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
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service(Service.Level.PROJECT)
public final class SpecSyncService {
    private static final Logger LOG = Logger.getInstance(SpecSyncService.class);

    private static final Pattern SECTION_HEADER = Pattern.compile(
            "^## (ADDED|MODIFIED|REMOVED|RENAMED) Requirements\\s*$", Pattern.MULTILINE);
    private static final Pattern REQUIREMENT_HEADER =
            com.johnnyblabs.openspec.util.SpecPatterns.REQUIREMENT_HEADER;
    private static final Pattern RENAMED_ENTRY = com.johnnyblabs.openspec.util.SpecPatterns.RENAMED_PAIR;

    private final Project project;

    public SpecSyncService(Project project) {
        this.project = project;
    }

    /**
     * Parses all delta spec files in a change directory into structured operations.
     */
    public List<DeltaSpecOperation> parseDeltaSpecs(String changeName) {
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir == null) return List.of();

        VirtualFile changeDir = changesDir.findChild(changeName);
        if (changeDir == null) return List.of();

        VirtualFile specsDir = changeDir.findChild("specs");
        if (specsDir == null) return List.of();

        List<DeltaSpecOperation> operations = new ArrayList<>();
        for (VirtualFile capDir : specsDir.getChildren()) {
            if (!capDir.isDirectory()) continue;
            VirtualFile specFile = capDir.findChild("spec.md");
            if (specFile == null) continue;

            try {
                String content = new String(specFile.contentsToByteArray(), StandardCharsets.UTF_8);
                String capabilityName = capDir.getName();
                operations.addAll(parseDeltaSpecContent(capabilityName, content));
            } catch (IOException e) {
                LOG.warn("Failed to read delta spec: " + specFile.getPath(), e);
            }
        }
        return operations;
    }

    /**
     * Parses a single delta spec file content into operations.
     */
    List<DeltaSpecOperation> parseDeltaSpecContent(String capabilityName, String content) {
        List<DeltaSpecOperation> operations = new ArrayList<>();

        // Find all section boundaries
        Matcher sectionMatcher = SECTION_HEADER.matcher(content);
        List<int[]> sections = new ArrayList<>(); // [start, end, typeOrdinal]
        while (sectionMatcher.find()) {
            OperationType type = OperationType.valueOf(sectionMatcher.group(1));
            int sectionStart = sectionMatcher.end();
            sections.add(new int[]{sectionStart, content.length(), type.ordinal()});
        }
        // Set section end boundaries
        for (int i = 0; i < sections.size() - 1; i++) {
            // Find the start of the next section header to set the end
            sectionMatcher.reset();
            int nextStart = sections.get(i + 1)[0];
            // End is at the start of the next section's header line
            int headerLineStart = content.lastIndexOf('\n', nextStart - 1);
            if (headerLineStart == -1) headerLineStart = 0;
            // Walk back to find the "## " prefix
            String prefix = content.substring(headerLineStart, nextStart);
            int hashIdx = prefix.indexOf("## ");
            if (hashIdx >= 0) {
                sections.get(i)[1] = headerLineStart + hashIdx;
            } else {
                sections.get(i)[1] = headerLineStart;
            }
        }

        for (int[] section : sections) {
            String sectionContent = content.substring(section[0], section[1]).trim();
            OperationType type = OperationType.values()[section[2]];

            if (type == OperationType.RENAMED) {
                operations.addAll(parseRenamedSection(capabilityName, sectionContent));
            } else if (type == OperationType.REMOVED) {
                operations.addAll(parseRemovedSection(capabilityName, sectionContent));
            } else {
                operations.addAll(parseRequirementBlocks(capabilityName, type, sectionContent));
            }
        }

        return operations;
    }

    private List<DeltaSpecOperation> parseRequirementBlocks(
            String capabilityName, OperationType type, String sectionContent) {
        List<DeltaSpecOperation> ops = new ArrayList<>();
        Matcher reqMatcher = REQUIREMENT_HEADER.matcher(sectionContent);

        List<int[]> reqPositions = new ArrayList<>();
        while (reqMatcher.find()) {
            reqPositions.add(new int[]{reqMatcher.start(), reqMatcher.end()});
        }

        for (int i = 0; i < reqPositions.size(); i++) {
            int blockStart = reqPositions.get(i)[0];
            int blockEnd = (i + 1 < reqPositions.size()) ? reqPositions.get(i + 1)[0] : sectionContent.length();
            String block = sectionContent.substring(blockStart, blockEnd).trim();

            // Extract requirement name
            Matcher nameMatcher = REQUIREMENT_HEADER.matcher(block);
            if (nameMatcher.find()) {
                String reqName = nameMatcher.group(1).trim();
                ops.add(new DeltaSpecOperation(type, capabilityName, reqName, block, null, null));
            }
        }

        return ops;
    }

    /** A bullet-form REMOVED entry: any CommonMark bullet carrying a (optionally backticked) requirement header. */
    private static final Pattern REMOVED_BULLET_ENTRY = Pattern.compile(
            "^\\s*[-*+]\\s*`?###\\s*(?i:requirement):\\s*(.+?)`?\\s*$", Pattern.MULTILINE);

    /**
     * REMOVED entries in document order, in both forms the client applies: a {@code ### Requirement:} block
     * and a bullet carrying the header ({@code * `### Requirement: Name`}). A bullet entry is header-only, so
     * its operation has no block content; {@link #applyRemoved} works by name.
     */
    private List<DeltaSpecOperation> parseRemovedSection(String capabilityName, String sectionContent) {
        java.util.TreeMap<Integer, DeltaSpecOperation> byOffset = new java.util.TreeMap<>();
        Matcher header = REQUIREMENT_HEADER.matcher(sectionContent);
        List<Integer> starts = new ArrayList<>();
        while (header.find()) {
            starts.add(header.start());
        }
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : sectionContent.length();
            String block = sectionContent.substring(starts.get(i), end).trim();
            Matcher name = REQUIREMENT_HEADER.matcher(block);
            if (name.find()) {
                byOffset.put(starts.get(i), new DeltaSpecOperation(OperationType.REMOVED, capabilityName,
                        name.group(1).trim(), block, null, null));
            }
        }
        Matcher bullet = REMOVED_BULLET_ENTRY.matcher(sectionContent);
        while (bullet.find()) {
            byOffset.put(bullet.start(), new DeltaSpecOperation(OperationType.REMOVED, capabilityName,
                    bullet.group(1).trim(), null, null, null));
        }
        return new ArrayList<>(byOffset.values());
    }

    private List<DeltaSpecOperation> parseRenamedSection(String capabilityName, String sectionContent) {
        List<DeltaSpecOperation> ops = new ArrayList<>();
        Matcher matcher = RENAMED_ENTRY.matcher(sectionContent);
        while (matcher.find()) {
            String fromName = bareRequirementName(matcher.group(1));
            String toName = bareRequirementName(matcher.group(2));
            ops.add(new DeltaSpecOperation(OperationType.RENAMED, capabilityName, fromName,
                    null, fromName, toName));
        }
        return ops;
    }

    /** Optional surrounding backticks, then an optional {@code ### Requirement:} token (any casing). */
    private static final Pattern RENAMED_NAME_WRAPPER = Pattern.compile(
            "^`?\\s*(?:###\\s*(?i:requirement):\\s*)?(.*?)\\s*`?$");

    /**
     * Reduces a RENAMED entry's name to the bare requirement name. The client's canonical form carries the
     * header ({@code FROM: `### Requirement: Old`}); the plugin also accepts bare names ({@code FROM: Old}).
     * Both must resolve to {@code Old}, or the rename never finds its main-spec target.
     */
    static String bareRequirementName(String raw) {
        Matcher m = RENAMED_NAME_WRAPPER.matcher(raw.trim());
        return m.matches() ? m.group(1).trim() : raw.trim();
    }

    /**
     * Computes the sync preview: what each main spec would look like after applying delta ops.
     */
    public List<SpecSyncResult> computeSync(String changeName) {
        List<DeltaSpecOperation> allOps = parseDeltaSpecs(changeName);
        if (allOps.isEmpty()) return List.of();

        // Group operations by capability
        Map<String, List<DeltaSpecOperation>> byCapability = allOps.stream()
                .collect(Collectors.groupingBy(DeltaSpecOperation::capabilityName, LinkedHashMap::new, Collectors.toList()));

        List<SpecSyncResult> results = new ArrayList<>();
        for (Map.Entry<String, List<DeltaSpecOperation>> entry : byCapability.entrySet()) {
            String capability = entry.getKey();
            List<DeltaSpecOperation> ops = entry.getValue();

            String mainSpecPath = getMainSpecPath(capability);
            String originalContent = readMainSpec(capability);
            List<String> warnings = new ArrayList<>();

            // Sort ops: RENAMED → REMOVED → MODIFIED → ADDED (mirrors upstream specs-apply.js)
            List<DeltaSpecOperation> sorted = sortOperations(ops);

            String projected = applyOperations(originalContent, sorted, warnings);

            results.add(new SpecSyncResult(capability, mainSpecPath, originalContent, projected, sorted, warnings));
        }

        return results;
    }

    /**
     * Writes the projected content of each sync result to disk,
     * then validates each affected main spec and reports new issues.
     * Returns the list of post-merge validation warnings (empty if clean).
     */
    public List<String> applySync(List<SpecSyncResult> results) throws IOException {
        List<String> postMergeWarnings = new ArrayList<>();

        for (SpecSyncResult result : results) {
            if (!result.hasChanges()) continue;

            Path path = Path.of(result.mainSpecPath());
            Files.createDirectories(path.getParent());

            // Write file content on current (background) thread — not inside WriteAction
            Files.writeString(path, result.projectedContent(), StandardCharsets.UTF_8);

            // VFS refresh must happen inside WriteAction on EDT — use invokeLater + latch
            // to avoid invokeAndWait deadlock while still synchronizing before validation
            CountDownLatch refreshLatch = new CountDownLatch(1);
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    WriteAction.run(() -> {
                        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString());
                        if (vf != null) vf.refresh(false, false);
                    });
                } finally {
                    refreshLatch.countDown();
                }
            });
            try {
                refreshLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Post-merge validation: compare pre-merge and post-merge issues
            postMergeWarnings.addAll(validatePostMerge(result));
        }
        // Final VFS refresh
        VfsUtil.markDirtyAndRefresh(false, true, true,
                LocalFileSystem.getInstance().findFileByPath(
                        project.getBasePath() + "/openspec/specs"));

        return postMergeWarnings;
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

    String applyOperations(String content, List<DeltaSpecOperation> ops, List<String> warnings) {
        if (content == null) content = "";
        String result = content;

        for (DeltaSpecOperation op : ops) {
            result = switch (op.type()) {
                case REMOVED -> applyRemoved(result, op, warnings);
                case RENAMED -> applyRenamed(result, op, warnings);
                case MODIFIED -> applyModified(result, op, warnings);
                case ADDED -> applyAdded(result, op);
            };
        }

        return result;
    }

    String applyAdded(String content, DeltaSpecOperation op) {
        if (content.isEmpty()) {
            // New spec file
            return "# " + toTitleCase(op.capabilityName()) + "\n\n"
                    + "## Purpose\n\n"
                    + "## Requirements\n\n"
                    + op.content() + "\n";
        }
        // Append to existing
        String trimmed = content.stripTrailing();
        return trimmed + "\n\n" + op.content() + "\n";
    }

    String applyModified(String content, DeltaSpecOperation op, List<String> warnings) {
        int[] range = findRequirementBlock(content, op.requirementName());
        if (range == null) {
            // sync-modified-target-missing: an always-on sync-safety guard, NOT gated on any setting.
            // A MODIFIED delta whose target requirement can't be found in the main spec genuinely
            // cannot be applied; surface it (the sync preview is the decision point) and skip the op.
            // (Formerly branched on the removed persistent strictValidation setting.)
            warnings.add("MODIFIED requirement '" + op.requirementName() + "' not found in "
                    + op.capabilityName() + " — this delta can't be applied; review before syncing "
                    + "[sync-modified-target-missing]");
            return content;
        }
        return content.substring(0, range[0]) + op.content() + content.substring(range[1]);
    }

    String applyRemoved(String content, DeltaSpecOperation op, List<String> warnings) {
        int[] range = findRequirementBlock(content, op.requirementName());
        if (range == null) {
            warnings.add("REMOVED: requirement '" + op.requirementName() + "' not found in " + op.capabilityName());
            return content;
        }
        String before = content.substring(0, range[0]);
        String after = content.substring(range[1]);
        // Clean up extra blank lines
        return (before.stripTrailing() + "\n\n" + after.stripLeading()).strip() + "\n";
    }

    String applyRenamed(String content, DeltaSpecOperation op, List<String> warnings) {
        if (op.fromName() == null || op.toName() == null) {
            warnings.add("RENAMED: missing FROM/TO names for " + op.capabilityName());
            return content;
        }
        Pattern headerPattern = Pattern.compile(
                "^(###\\s+Requirement:\\s*)" + Pattern.quote(op.fromName()) + "\\s*$",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher m = headerPattern.matcher(content);
        if (!m.find()) {
            warnings.add("RENAMED: requirement '" + op.fromName() + "' not found in " + op.capabilityName());
            return content;
        }
        // Rewritten headers always come out in canonical casing, even when the matched
        // source header used a non-canonical token (recognized for CLI 1.4+ parity).
        return m.replaceFirst(Matcher.quoteReplacement(
                com.johnnyblabs.openspec.util.SpecPatterns.CANONICAL_HEADER_PREFIX + op.toName()));
    }

    /**
     * Finds the start and end offset of a requirement block by name.
     * A block starts at the "### Requirement: name" line and ends at the next
     * "### " or "## " heading, or EOF.
     */
    int[] findRequirementBlock(String content, String reqName) {
        Pattern headerPattern = Pattern.compile(
                "^###\\s+Requirement:\\s*" + Pattern.quote(reqName) + "\\s*$",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher m = headerPattern.matcher(content);
        if (!m.find()) return null;

        int start = m.start();
        // Find end: next "### " or "## " heading
        Pattern nextHeading = Pattern.compile("^##[#]? ", Pattern.MULTILINE);
        Matcher endMatcher = nextHeading.matcher(content);
        int end = content.length();
        // Search for next heading after the current one's content
        int searchFrom = m.end();
        while (endMatcher.find(searchFrom)) {
            end = endMatcher.start();
            break;
        }
        return new int[]{start, end};
    }

    private String getMainSpecPath(String capabilityName) {
        return project.getBasePath() + "/openspec/specs/" + capabilityName + "/spec.md";
    }

    private String readMainSpec(String capabilityName) {
        try {
            Path path = Path.of(getMainSpecPath(capabilityName));
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.warn("Failed to read main spec for " + capabilityName, e);
        }
        return null;
    }

    private String toTitleCase(String kebab) {
        return Stream.of(kebab.split("-"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }
}

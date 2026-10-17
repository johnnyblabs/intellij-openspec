package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ChangeMetadata;
import com.johnnyblabs.openspec.model.ChangeMetadataParser;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import com.johnnyblabs.openspec.version.VersionSupport;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service(Service.Level.PROJECT)
public final class ChangeService {
    private static final Logger LOG = Logger.getInstance(ChangeService.class);

    private final Project project;

    public ChangeService(Project project) {
        this.project = project;
    }

    public List<Change> getActiveChanges() {
        return getChangesFromDir(OpenSpecFileUtil.getChangesDir(project));
    }

    public List<Change> getArchivedChanges() {
        return getChangesFromDir(OpenSpecFileUtil.getArchiveDir(project));
    }

    public String archiveFirstActive() throws IOException {
        List<Change> active = getActiveChanges();
        if (active.isEmpty()) return null;
        return archiveChange(active.getFirst());
    }

    private static final java.util.regex.Pattern DATED_NAME = java.util.regex.Pattern.compile("^\\d{4}-\\d{2}-\\d{2}-");

    private java.time.Clock clock = java.time.Clock.systemDefaultZone();

    @org.jetbrains.annotations.TestOnly
    public void setClockForTest(java.time.Clock clock) {
        this.clock = clock;
    }

    /**
     * The folder name a change archives to, as {@code openspec archive} names it: {@code YYYY-MM-DD-<name>} in local
     * time, or the name unchanged when it already starts with a date.
     */
    public String archiveTargetName(String changeName) {
        return archiveTargetName(changeName, java.time.LocalDate.now(clock));
    }

    /** {@link #archiveTargetName(String)} for a given date. */
    public static String archiveTargetName(String changeName, java.time.LocalDate date) {
        return DATED_NAME.matcher(changeName).find() ? changeName : date + "-" + changeName;
    }

    /** OpenSpec's refusal when the archive folder already exists. */
    public static String archiveExistsMessage(String target) {
        return "Archive '" + target + "' already exists.";
    }

    /** OpenSpec's refusal when the change's archive folder already exists, or {@code null} when it can archive. */
    public @org.jetbrains.annotations.Nullable String archiveConflict(String changeName) {
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir == null) return null;
        String target = archiveTargetName(changeName);
        VirtualFile archive = changesDir.findChild("archive");
        // The disk too, not only the VFS cache: a folder the CLI or git just created may not be refreshed yet.
        java.nio.file.Path onDisk = changesDir.getFileSystem().getNioPath(changesDir); // null for in-memory files
        boolean exists = archive != null && archive.findChild(target) != null
                || onDisk != null && java.nio.file.Files.exists(onDisk.resolve("archive").resolve(target));
        return exists ? archiveExistsMessage(target) : null;
    }

    /**
     * Moves a change to {@code openspec/changes/archive/<archiveTargetName>}, creating {@code changes/archive/} when
     * missing. Refuses when the target exists. Never creates the legacy {@code openspec/archive/}, which is only read.
     * Returns the archived folder name.
     */
    public String archiveChange(Change change) throws IOException {
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir == null) throw new IOException("Changes directory not found");

        VirtualFile changeDir = changesDir.findChild(change.getName());
        if (changeDir == null) throw new IOException("Change directory not found: " + change.getName());

        String conflict = archiveConflict(change.getName());
        if (conflict != null) throw new IOException(conflict);

        String target = archiveTargetName(change.getName());
        WriteAction.run(() -> {
            VirtualFile archive = changesDir.findChild("archive");
            if (archive == null) {
                archive = changesDir.createChildDirectory(this, "archive");
            }
            changeDir.move(this, archive);
            if (!target.equals(changeDir.getName())) {
                changeDir.rename(this, target);
            }
        });

        // Archived-ness is directory location (changes/archive/), not a metadata field — the invented
        // status: write is retired. (This line previously did an unguarded getMetadata().setStatus on
        // possibly-null metadata AFTER the move committed, NPE-ing into a half-succeeded archive.)
        return target;
    }

    public List<String> getMissingArtifacts(Change change) {
        String version = OpenSpecSettings.getInstance(project).getEffectiveVersion(project);
        VersionSupport vs = VersionSupport.fromString(version);
        Set<String> required = vs.getRequiredArtifacts();
        List<String> missing = new ArrayList<>();
        for (String artifact : required) {
            if (!change.getArtifactFiles().contains(artifact)) {
                missing.add(artifact);
            }
        }
        return missing;
    }

    public List<String> getDeltaSpecNames(Change change) {
        List<String> names = new ArrayList<>();
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir == null) return names;
        VirtualFile changeDir = changesDir.findChild(change.getName());
        if (changeDir == null) return names;
        VirtualFile specsDir = changeDir.findChild("specs");
        if (specsDir == null) return names;
        for (VirtualFile domainDir : specsDir.getChildren()) {
            if (!domainDir.isDirectory()) continue;
            VirtualFile specFile = domainDir.findChild("spec.md");
            if (specFile != null) {
                names.add(domainDir.getName());
            }
        }
        return names;
    }

    private static final Set<String> RESERVED_DIRS = Set.of("archive");

    private List<Change> getChangesFromDir(VirtualFile dir) {
        List<Change> changes = new ArrayList<>();
        if (dir == null || !dir.exists()) return changes;

        for (VirtualFile changeDir : dir.getChildren()) {
            if (!changeDir.isDirectory()) continue;
            if (RESERVED_DIRS.contains(changeDir.getName())) continue;
            Change change = new Change(changeDir.getName(), changeDir.getPath());

            VirtualFile metaFile = changeDir.findChild(".openspec.yaml");
            if (metaFile != null) {
                try {
                    // Read the .openspec.yaml text inside the appropriate threading context, then hand
                    // it to the pure, lenient parser. Unknown/newer keys are ignored (upstream strip
                    // contract); the warning is reserved for genuinely malformed YAML.
                    String content;
                    if (ApplicationManager.getApplication() == null
                            || ApplicationManager.getApplication().isDispatchThread()) {
                        try (InputStream is = metaFile.getInputStream()) {
                            content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    } else {
                        content = ApplicationManager.getApplication().runReadAction((ThrowableComputable<String, IOException>) () -> {
                            try (InputStream is = metaFile.getInputStream()) {
                                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            }
                        });
                    }
                    ChangeMetadataParser.ParseResult result = ChangeMetadataParser.parse(content);
                    if (result.isMalformed()) {
                        String problem = result.problem() != null ? result.problem() : "invalid YAML";
                        LOG.warn("Failed to parse change metadata: " + metaFile.getPath() + " — " + problem);
                        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "Configuration",
                                ".openspec.yaml parse error in '" + changeDir.getName() + "': " + problem,
                                com.intellij.notification.NotificationType.WARNING);
                    } else {
                        change.setMetadata(result.metadata());
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to read change metadata: " + metaFile.getPath(), e);
                }
            }

            for (VirtualFile child : changeDir.getChildren()) {
                if (child.isDirectory() && "specs".equals(child.getName())) {
                    continue;
                }
                if (!child.isDirectory() && !child.getName().startsWith(".")) {
                    change.getArtifactFiles().add(child.getName());
                }
            }

            changes.add(change);
        }
        return changes;
    }
}

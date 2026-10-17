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

    private static final java.util.regex.Pattern DATED_NAME = java.util.regex.Pattern.compile("^\\d{4}-\\d{2}-\\d{2}-");

    private java.time.Clock clock = java.time.Clock.systemDefaultZone();

    /** Replaces the clock used for dated archive names; {@code null} restores the system clock. */
    @org.jetbrains.annotations.TestOnly
    public void setClockForTest(@org.jetbrains.annotations.Nullable java.time.Clock clock) {
        this.clock = clock == null ? java.time.Clock.systemDefaultZone() : clock;
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

    /** The first CLI version whose {@code archive --json} the plugin delegates to (1.4 has no {@code --json}). */
    static final String CLI_ARCHIVE_FLOOR = "1.5.0";

    /** Runs the OpenSpec CLI; a seam so tests can feed captured output and check the arguments. */
    public interface ArchiveCli {
        com.johnnyblabs.openspec.util.CliRunner.CliResult run(Project project, String... args)
                throws com.johnnyblabs.openspec.util.CliRunner.CliException;
    }

    private static final ArchiveCli REAL_CLI = (p, args) -> com.johnnyblabs.openspec.util.CliRunner.run(p, 120_000, args);

    private ArchiveCli archiveCli = REAL_CLI;

    /** Replaces the CLI runner; {@code null} restores the real one. */
    @org.jetbrains.annotations.TestOnly
    public void setArchiveCliForTest(@org.jetbrains.annotations.Nullable ArchiveCli cli) {
        this.archiveCli = cli == null ? REAL_CLI : cli;
    }

    /**
     * Archives a change. With OpenSpec CLI 1.5 or later this runs {@code openspec archive <name> --yes --json}, adding
     * {@code --skip-specs} when {@code syncSpecs} is false, so the user's CLI applies its own validation and rules.
     * Otherwise the plugin archives the change itself: spec sync first when asked, then the dated move.
     * Must be called off the UI thread (it may run a CLI process). Throws {@link ArchiveRefusedException} with
     * OpenSpec's reason when the archive is refused; the change then stays active.
     */
    public ArchiveJson.Outcome archiveChange(Change change, boolean syncSpecs) throws IOException {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            throw new IllegalStateException("archiveChange must not run on the UI thread: it may run the OpenSpec CLI");
        }
        CliDetectionService cli = project.getService(CliDetectionService.class);
        String version = cli != null && cli.isAvailable() ? cli.getDetectedVersion() : null;
        if (version != null && com.johnnyblabs.openspec.util.CliVersion.atLeast(version, CLI_ARCHIVE_FLOOR)) {
            return archiveWithCli(change, syncSpecs);
        }
        return archiveItself(change, syncSpecs);
    }

    private ArchiveJson.Outcome archiveWithCli(Change change, boolean syncSpecs) throws IOException {
        List<String> args = new ArrayList<>(List.of("archive", change.getName(), "--yes", "--json"));
        if (!syncSpecs) args.add("--skip-specs");
        com.johnnyblabs.openspec.util.CliRunner.CliResult result;
        try {
            result = archiveCli.run(project, args.toArray(String[]::new));
        } catch (com.johnnyblabs.openspec.util.CliRunner.CliException e) {
            throw new IOException("Could not run openspec archive: " + e.getMessage(), e);
        }
        try {
            return ArchiveJson.parse(result.stdout(), result.stderr(), result.exitCode());
        } finally {
            // Whatever the CLI did on disk (a move, spec edits, or nothing), the tree and editors must see it.
            VirtualFile root = OpenSpecFileUtil.getOpenSpecRoot(project);
            if (root != null) {
                com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, root);
            }
        }
    }

    /**
     * The plugin's own archive (no CLI, or one older than 1.5): refuse an existing target before writing anything,
     * sync specs when asked (all-or-nothing, as {@code openspec archive}), then move the change to its dated folder.
     */
    private ArchiveJson.Outcome archiveItself(Change change, boolean syncSpecs) throws IOException {
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir == null) throw new IOException("Changes directory not found");
        VirtualFile changeDir = changesDir.findChild(change.getName());
        if (changeDir == null) throw new IOException("Change directory not found: " + change.getName());

        String conflict = archiveConflict(change.getName());
        if (conflict != null) throw new ArchiveRefusedException(conflict, null);

        boolean specsUpdated = false;
        List<String> warnings = new ArrayList<>();
        if (syncSpecs) {
            SpecSyncService sync = project.getService(SpecSyncService.class);
            List<com.johnnyblabs.openspec.model.SpecSyncResult> results = sync.computeSync(change.getName());
            for (com.johnnyblabs.openspec.model.SpecSyncResult r : results) {
                if (r.refused()) throw new ArchiveRefusedException(r.refusals().get(0), null);
                warnings.addAll(r.warnings());
            }
            if (results.stream().anyMatch(com.johnnyblabs.openspec.model.SpecSyncResult::hasChanges)) {
                sync.applySync(results);
                specsUpdated = true;
            }
        }

        String target = archiveTargetName(change.getName());
        IOException[] failure = new IOException[1];
        // The move is a write action, so it runs on the UI thread, carrying the caller's modality (a progress task's
        // own inside a modal dialog, as for Bulk Archive), like SpecSyncService.applySync.
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
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
            } catch (IOException e) {
                failure[0] = e;
            }
        }, com.intellij.openapi.application.ModalityState.defaultModalityState());
        if (failure[0] != null) throw failure[0];

        // Archived-ness is directory location (changes/archive/), not a metadata field — the invented
        // status: write is retired. (This line previously did an unguarded getMetadata().setStatus on
        // possibly-null metadata AFTER the move committed, NPE-ing into a half-succeeded archive.)
        return new ArchiveJson.Outcome(target, specsUpdated, List.copyOf(warnings));
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

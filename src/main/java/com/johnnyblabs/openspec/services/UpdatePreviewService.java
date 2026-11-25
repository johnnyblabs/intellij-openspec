package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.UpdatePreviewDialog;
import com.johnnyblabs.openspec.toolwindow.OpenSpecConsolePanel;
import com.johnnyblabs.openspec.toolwindow.OpenSpecConsoleService;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.CliVersion;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import com.johnnyblabs.openspec.util.UpdateOutputParser;
import com.johnnyblabs.openspec.util.UpdatePlanParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The one preview-and-apply path for every plugin-started {@code openspec update} on CLI 1.15.0+.
 *
 * <p>Off the UI thread it runs {@code update --dry-run --json}, which writes nothing and reports the exact plan the
 * non-interactive run will carry out. A failed or unreadable dry run is reported as a failure, never as "up to date",
 * and nothing is applied. An empty plan is reported as up to date and not run. Otherwise the plan is shown in
 * {@link UpdatePreviewDialog}; Apply runs the plain {@code update} once and hands its result to the caller. When the
 * applied plan reported legacy artifacts, the files to remove come from the read-only
 * {@code update --dry-run --force --json} and go to {@link UpdateLegacyCleanupService}. The plugin never runs
 * {@code --force} without {@code --dry-run}.
 *
 * <p>Below {@link #DRY_RUN_FLOOR} the CLI has no {@code --dry-run}; {@link #supportsPreview()} is false and each caller
 * keeps its own direct path.
 */
@Service(Service.Level.PROJECT)
public final class UpdatePreviewService {

    private static final Logger LOG = Logger.getInstance(UpdatePreviewService.class);

    /** The first CLI with {@code update --dry-run --json}. A CLI-capability gate, not the config-format axis. */
    public static final String DRY_RUN_FLOOR = "1.15.0";

    /** Where the preview was opened from. */
    public enum Origin {ACTION, PROFILE_SWITCH, MANAGE_TOOLS}

    interface UpdateCli {
        CliRunner.CliResult run(Project project, com.johnnyblabs.openspec.util.CliArgs args) throws CliRunner.CliException;
    }

    /** Shows the preview on the EDT and answers whether the user applied it. */
    interface Decider {
        boolean apply(UpdatePreviewModel model);
    }

    interface Background {
        void run(String title, Runnable task);
    }

    interface Ui {
        void run(ModalityState modality, Runnable task);
    }

    /** Reads the files at risk from the project; a seam so tests can see which thread does it. */
    interface AtRiskReader {
        List<UpdateMigration.AtRisk> read(java.nio.file.Path root, UpdatePlanParser.Plan plan);
    }

    private final Project project;
    private UpdateCli cli = CliRunner::run;
    private Decider decider;
    private Background background;
    private Ui ui = (modality, task) -> ApplicationManager.getApplication().invokeLater(task, modality);
    private @Nullable String versionForTest;
    private java.nio.file.@Nullable Path rootForTest;
    private AtRiskReader atRiskReader = UpdateMigration::atRiskFiles;

    public UpdatePreviewService(Project project) {
        this.project = project;
        this.decider = model -> new UpdatePreviewDialog(project, model).showAndGet();
        this.background = (title, task) -> new Task.Backgroundable(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                task.run();
            }
        }.queue();
    }

    public static UpdatePreviewService getInstance(Project project) {
        return project.getService(UpdatePreviewService.class);
    }

    /** The detected CLI's version, or {@code null} when none is detected. Package-private for tests. */
    @Nullable String detectedVersion() {
        if (versionForTest != null) return versionForTest;
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null && detection.isAvailable() ? detection.getDetectedVersion() : null;
    }

    /** Whether the detected CLI can preview an update; false when it is missing or older than {@link #DRY_RUN_FLOOR}. */
    public boolean supportsPreview() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null && detection.isAvailable()
                && CliVersion.atLeast(detection.getDetectedVersion(), DRY_RUN_FLOOR);
    }

    /**
     * Previews the update, and on Apply runs it and passes its result to {@code onApplied} on the EDT. Call on the EDT
     * after {@link #supportsPreview()}; {@code focusTool} (a tool id) is listed first and expanded.
     */
    public void previewAndApply(Origin origin, @Nullable String focusTool, Consumer<CliRunner.CliResult> onApplied) {
        previewAndApply(origin, focusTool, onApplied, () -> { });
    }

    /**
     * As {@link #previewAndApply(Origin, String, Consumer)}, and then runs {@code onFinished} on the EDT exactly once,
     * whatever the outcome: applied (after {@code onApplied}), cancelled, failed, or nothing to update. A caller that
     * shows a busy state restores it there.
     */
    public void previewAndApply(Origin origin, @Nullable String focusTool, Consumer<CliRunner.CliResult> onApplied,
                                Runnable onFinished) {
        ModalityState modality = ModalityState.current();
        background.run("Previewing openspec update", () -> {
            UpdatePlanParser.Result result = dryRun(false);
            List<UpdateMigration.AtRisk> atRisk = result instanceof UpdatePlanParser.Plan plan
                    ? atRiskFiles(plan) : List.of();
            ui.run(modality, () -> decide(result, atRisk, focusTool, onApplied, onFinished, modality));
        });
    }

    /** The planned files OpenSpec can't confirm as its own, read from the project. Off the EDT. */
    private List<UpdateMigration.AtRisk> atRiskFiles(UpdatePlanParser.Plan plan) {
        java.nio.file.Path root = projectRoot();
        if (root == null) return List.of();
        try {
            List<UpdateMigration.AtRisk> found = atRiskReader.read(root, plan);
            if (!found.isEmpty()) LOG.info("Update preview: " + found.size() + " file(s) at risk");
            return found;
        } catch (RuntimeException e) {
            LOG.warn("Couldn't check the planned skill files", e);
            return List.of();
        }
    }

    private @Nullable java.nio.file.Path projectRoot() {
        if (rootForTest != null) return rootForTest;
        String base = project.getBasePath();
        return base != null ? java.nio.file.Path.of(base) : null;
    }

    private void decide(UpdatePlanParser.Result result, List<UpdateMigration.AtRisk> atRisk, @Nullable String focusTool,
                        Consumer<CliRunner.CliResult> onApplied, Runnable onFinished, ModalityState modality) {
        if (result instanceof UpdatePlanParser.Failed failed) {
            LOG.info("Update preview failed: " + failed.message());
            reportFailure(com.johnnyblabs.openspec.util.CliCommands.updatePlan(false).display(), "The update preview failed: " + failed.message());
            onFinished.run();
            return;
        }
        UpdatePlanParser.Plan plan = (UpdatePlanParser.Plan) result;
        if (plan.isEmpty()) {
            LOG.info("Update preview: nothing to update");
            OpenSpecNotifier.info(project, "OpenSpec update",
                    "Already up to date: openspec update has nothing to change.");
            onFinished.run();
            return;
        }
        LOG.info("Update preview: " + plan.changes().size() + " change(s), " + plan.notes().size() + " note(s), "
                + plan.failures().size() + " failure(s)" + (focusTool != null ? ", focus " + focusTool : ""));
        if (!decider.apply(UpdatePreviewModel.of(plan, focusTool, detectedVersion(), atRisk))) {
            LOG.info("Update preview cancelled");
            onFinished.run();
            return;
        }
        LOG.info("Update preview applied: running openspec update");
        background.run("Running openspec update", () -> {
            CliRunner.CliResult applied;
            try {
                applied = cli.run(project, com.johnnyblabs.openspec.util.CliCommands.updateWithoutPrompts());
            } catch (CliRunner.CliException e) {
                LOG.info("openspec update could not run: " + e.getMessage());
                ui.run(modality, () -> {
                    reportFailure("openspec update", "openspec update failed: " + e.getMessage());
                    onFinished.run();
                });
                return;
            }
            LOG.info("openspec update finished with exit code " + applied.exitCode());
            KeptNotice kept = keptNotice(applied);
            ui.run(modality, () -> {
                onApplied.accept(applied);
                onFinished.run();
                if (kept != null) kept.show(project);
            });
            if (applied.isSuccess() && plan.hasNote(UpdatePlanParser.LEGACY_ARTIFACTS)) {
                offerLegacyCleanup(plan, modality);
            }
        });
    }

    /** At most this many files get their own Open action; every path is still listed in the text. */
    static final int MAX_OPEN_ACTIONS = 5;
    static final String KEPT_TITLE = "OpenSpec update kept or replaced files";

    /** What the applied update reported keeping, leaving or replacing, with the in-project files found on disk. */
    record KeptNotice(List<com.johnnyblabs.openspec.util.UpdateOutputParser.KeptReport> reports,
                      List<com.intellij.openapi.vfs.VirtualFile> files) {
        boolean replaced() {
            return reports.stream().anyMatch(r -> r.kind() == com.johnnyblabs.openspec.util.UpdateOutputParser.KeptKind.REPLACED);
        }

        String content() {
            StringBuilder html = new StringBuilder();
            for (var r : reports) {
                if (html.length() > 0) html.append("<br><br>");
                html.append(com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(r.header()));
                for (String path : r.paths()) {
                    html.append("<br>&nbsp;&nbsp;").append(com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(path));
                }
                if (r.guidance() != null) {
                    html.append("<br>").append(com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(r.guidance()));
                }
            }
            return html.toString();
        }

        void show(Project project) {
            var actions = files.stream().limit(MAX_OPEN_ACTIONS).map(f -> (com.intellij.notification.NotificationAction)
                    new com.intellij.notification.NotificationAction("Open " + f.getName()) {
                        @Override
                        public void actionPerformed(@NotNull com.intellij.openapi.actionSystem.AnActionEvent e,
                                                    @NotNull com.intellij.notification.Notification n) {
                            new com.intellij.openapi.fileEditor.OpenFileDescriptor(project, f).navigate(true);
                        }
                    }).toArray(com.intellij.notification.NotificationAction[]::new);
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, KEPT_TITLE, content(),
                    replaced() ? com.intellij.notification.NotificationType.WARNING
                            : com.intellij.notification.NotificationType.INFORMATION, actions);
        }
    }

    /**
     * The kept-files notice for an applied run, or {@code null} when its output reports nothing kept, left or replaced.
     * Off the EDT: it refreshes the in-project files it names so they can be opened.
     */
    @Nullable KeptNotice keptNotice(CliRunner.CliResult applied) {
        var reports = com.johnnyblabs.openspec.util.UpdateOutputParser.parseKept(applied.stdout());
        if (reports.isEmpty()) return null;
        LOG.info("Update kept or replaced: " + reports.size() + " report(s)");
        java.nio.file.Path root = projectRoot();
        List<com.intellij.openapi.vfs.VirtualFile> files = new java.util.ArrayList<>();
        if (root != null) {
            for (var r : reports) {
                for (String path : r.paths()) {
                    if (path.startsWith("/") || path.startsWith("~")) continue;
                    java.nio.file.Path file = root.resolve(path).normalize();
                    if (!file.startsWith(root)) continue;
                    var vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
                    if (vf != null && !vf.isDirectory()) files.add(vf);
                }
            }
        }
        return new KeptNotice(reports, List.copyOf(files));
    }

    /** Called off the EDT after an applied update that left legacy artifacts: reads their removal from the forced plan. */
    private void offerLegacyCleanup(UpdatePlanParser.Plan applied, ModalityState modality) {
        LegacyPending pending = pendingLegacyFiles();
        String guidance = applied.notes().stream()
                .filter(n -> UpdatePlanParser.LEGACY_ARTIFACTS.equals(n.code()))
                .map(UpdatePlanParser.Note::message).findFirst().orElse(null);
        ui.run(modality, () -> {
            if (pending.failure() != null) {
                LOG.info("Legacy cleanup list couldn't be read: " + pending.failure());
                OpenSpecNotifier.warn(project, "OpenSpec update",
                        "The legacy cleanup list couldn't be read: " + pending.failure());
                return;
            }
            List<String> files = pending.files();
            LOG.info("Legacy cleanup: " + files.size() + " file(s) offered from the forced plan");
            if (files.isEmpty()) {
                return;
            }
            UpdateLegacyCleanupService cleanup = project.getService(UpdateLegacyCleanupService.class);
            if (cleanup != null) {
                cleanup.offerCleanup(new UpdateOutputParser.LegacyCleanup(files, guidance));
            }
        });
    }

    /** The plan tool value of OpenSpec's own legacy cleanup; other tools' deletes are ordinary update work. */
    static final String LEGACY_CLEANUP_TOOL = "openspec";

    /**
     * The in-project deletes OpenSpec's legacy cleanup lists in a forced plan: the only files the cleanup flow may
     * offer. Deletes of other tools (stale skills, the earlier-release sweep) are not legacy files, and neither is
     * anything in an OpenSpec skill folder or the shared-skills marker: CLI 2.1 tags those {@code openspec} too, and the
     * update itself manages them (upstream's legacy cleanup never targets a {@code skills/} path). Package-private for
     * tests.
     */
    static List<String> legacyDeletes(UpdatePlanParser.Plan forced) {
        return forced.changes().stream()
                .filter(c -> c.action().equals("delete") && !c.outsideProject()
                        && LEGACY_CLEANUP_TOOL.equals(c.tool())
                        && !UpdateMigration.inSkillFolder(c.path()) && !UpdateMigration.MARKER.equals(c.path()))
                .map(UpdatePlanParser.Change::path)
                .toList();
    }

    /** The legacy files the read-only forced plan would remove, or why that plan couldn't be read. */
    record LegacyPending(List<String> files, @Nullable String failure) {
    }

    /** Reads the pending legacy files from {@code update --dry-run --force --json}, which writes nothing. Off the EDT. */
    LegacyPending pendingLegacyFiles() {
        UpdatePlanParser.Result forced = dryRun(true);
        if (forced instanceof UpdatePlanParser.Failed failed) {
            return new LegacyPending(List.of(), failed.message());
        }
        return new LegacyPending(legacyDeletes((UpdatePlanParser.Plan) forced), null);
    }

    /**
     * What a read-only cleanup verification established. {@code unverifiedReason} is non-null when a dry run failed.
     * Otherwise {@code noteRemains} says whether the plan still carries the legacy-artifacts note, {@code pending}
     * lists the legacy files the forced plan would still remove, and {@code updateRemains} says whether a note-free
     * plan still has anything to do (changes, notes or failures).
     */
    record CleanupCheck(List<String> pending, boolean noteRemains, boolean updateRemains,
                        @Nullable String unverifiedReason) {
        static CleanupCheck unverified(String reason) {
            return new CleanupCheck(List.of(), false, false, reason);
        }
    }

    /**
     * Verifies a legacy-file cleanup without writing anything (CLI 1.15.0+). Runs {@code update --dry-run --json}; a
     * plan without the legacy-artifacts note has nothing pending. When the note remains, the pending files come from
     * {@link #pendingLegacyFiles()}, as when the cleanup was first offered. Never runs {@code update} without
     * {@code --dry-run}. Call off the EDT.
     */
    CleanupCheck verifyCleanupReadOnly() {
        UpdatePlanParser.Result result = dryRun(false);
        if (result instanceof UpdatePlanParser.Failed failed) {
            return CleanupCheck.unverified(failed.message());
        }
        UpdatePlanParser.Plan plan = (UpdatePlanParser.Plan) result;
        if (!plan.hasNote(UpdatePlanParser.LEGACY_ARTIFACTS)) {
            return new CleanupCheck(List.of(), false, !plan.isEmpty(), null);
        }
        LegacyPending pending = pendingLegacyFiles();
        if (pending.failure() != null) {
            return CleanupCheck.unverified(pending.failure());
        }
        return new CleanupCheck(pending.files(), true, false, null);
    }

    private UpdatePlanParser.Result dryRun(boolean force) {
        try {
            return UpdatePlanParser.parse(cli.run(project, com.johnnyblabs.openspec.util.CliCommands.updatePlan(force)));
        } catch (CliRunner.CliException e) {
            return new UpdatePlanParser.Failed(e.getMessage() == null ? "the OpenSpec CLI could not be run" : e.getMessage());
        }
    }

    private void reportFailure(String command, String message) {
        OpenSpecConsoleService consoles = project.getService(OpenSpecConsoleService.class);
        OpenSpecConsolePanel console = consoles != null ? consoles.getAndActivate() : null;
        if (console != null) {
            console.printCommand(command);
            console.printError(message + "\n");
        }
        OpenSpecNotifier.warn(project, "OpenSpec update", message);
    }

    @TestOnly
    void setThreadingForTest(Background background, Ui ui) {
        this.background = background;
        this.ui = ui;
    }

    @TestOnly
    void setAtRiskReaderForTest(AtRiskReader reader) {
        this.atRiskReader = reader;
    }

    @TestOnly
    void setProjectRootForTest(java.nio.file.@Nullable Path root) {
        this.rootForTest = root;
    }

    @TestOnly
    void setCliVersionForTest(@Nullable String version) {
        this.versionForTest = version;
    }

    @TestOnly
    void setSeamsForTest(UpdateCli cli, Decider decider) {
        this.cli = cli;
        this.decider = decider;
        this.background = (title, task) -> task.run();
        this.ui = (modality, task) -> task.run();
    }
}

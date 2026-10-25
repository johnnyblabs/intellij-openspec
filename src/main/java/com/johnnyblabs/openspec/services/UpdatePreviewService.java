package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
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

    /** The first CLI with {@code update --dry-run --json}. A CLI-capability gate, not the config-format axis. */
    public static final String DRY_RUN_FLOOR = "1.15.0";

    /** Makes the CLI take the non-interactive path the dry run plans, even if a runner ever gives it a terminal. */
    static final Map<String, String> NON_INTERACTIVE = Map.of("OPEN_SPEC_INTERACTIVE", "0");

    /** Where the preview was opened from. */
    public enum Origin {ACTION, PROFILE_SWITCH, MANAGE_TOOLS}

    interface UpdateCli {
        CliRunner.CliResult run(Project project, Map<String, String> env, String... args) throws CliRunner.CliException;
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

    private final Project project;
    private UpdateCli cli = CliRunner::run;
    private Decider decider;
    private Background background;
    private Ui ui = (modality, task) -> ApplicationManager.getApplication().invokeLater(task, modality);

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
        ModalityState modality = ModalityState.current();
        background.run("Previewing openspec update", () -> {
            UpdatePlanParser.Result result = dryRun("update", "--dry-run", "--json");
            ui.run(modality, () -> decide(result, focusTool, onApplied, modality));
        });
    }

    private void decide(UpdatePlanParser.Result result, @Nullable String focusTool,
                        Consumer<CliRunner.CliResult> onApplied, ModalityState modality) {
        if (result instanceof UpdatePlanParser.Failed failed) {
            reportFailure("openspec update --dry-run --json", "The update preview failed: " + failed.message());
            return;
        }
        UpdatePlanParser.Plan plan = (UpdatePlanParser.Plan) result;
        if (plan.isEmpty()) {
            OpenSpecNotifier.info(project, "OpenSpec update",
                    "Already up to date: openspec update has nothing to change.");
            return;
        }
        if (!decider.apply(UpdatePreviewModel.of(plan, focusTool))) {
            return;
        }
        background.run("Running openspec update", () -> {
            CliRunner.CliResult applied;
            try {
                applied = cli.run(project, NON_INTERACTIVE, "update");
            } catch (CliRunner.CliException e) {
                ui.run(modality, () -> reportFailure("openspec update", "openspec update failed: " + e.getMessage()));
                return;
            }
            ui.run(modality, () -> onApplied.accept(applied));
            if (applied.isSuccess() && plan.hasNote(UpdatePlanParser.LEGACY_ARTIFACTS)) {
                offerLegacyCleanup(plan, modality);
            }
        });
    }

    /** Called off the EDT after an applied update that left legacy artifacts: reads their removal from the forced plan. */
    private void offerLegacyCleanup(UpdatePlanParser.Plan applied, ModalityState modality) {
        UpdatePlanParser.Result forced = dryRun("update", "--dry-run", "--force", "--json");
        String guidance = applied.notes().stream()
                .filter(n -> UpdatePlanParser.LEGACY_ARTIFACTS.equals(n.code()))
                .map(UpdatePlanParser.Note::message).findFirst().orElse(null);
        ui.run(modality, () -> {
            if (forced instanceof UpdatePlanParser.Failed failed) {
                OpenSpecNotifier.warn(project, "OpenSpec update",
                        "The legacy cleanup list couldn't be read: " + failed.message());
                return;
            }
            List<String> files = legacyDeletes((UpdatePlanParser.Plan) forced);
            if (files.isEmpty()) {
                return;
            }
            UpdateLegacyCleanupService cleanup = project.getService(UpdateLegacyCleanupService.class);
            if (cleanup != null) {
                cleanup.offerCleanup(new UpdateOutputParser.LegacyCleanup(files, guidance));
            }
        });
    }

    /** The in-project deletes of a forced plan: the only files the cleanup flow may offer. Package-private for tests. */
    static List<String> legacyDeletes(UpdatePlanParser.Plan forced) {
        return forced.changes().stream()
                .filter(c -> c.action().equals("delete") && !c.outsideProject())
                .map(UpdatePlanParser.Change::path)
                .toList();
    }

    private UpdatePlanParser.Result dryRun(String... args) {
        try {
            return UpdatePlanParser.parse(cli.run(project, NON_INTERACTIVE, args));
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
    void setSeamsForTest(UpdateCli cli, Decider decider) {
        this.cli = cli;
        this.decider = decider;
        this.background = (title, task) -> task.run();
        this.ui = (modality, task) -> task.run();
    }
}

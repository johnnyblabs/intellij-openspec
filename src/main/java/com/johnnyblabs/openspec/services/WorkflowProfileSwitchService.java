package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Consumer;

/**
 * Centralizes the OpenSpec workflow profile switch flow so the Settings panel
 * (`OpenSpecConfigurable.applyProfileChange`) and the status bar widget popup
 * share the same code path. The two-step OpenSpec profile change process is:
 * <ol>
 *   <li>{@code openspec config profile <preset>} — updates the workflow set</li>
 *   <li>{@code openspec update} — installs the corresponding skills/commands
 *       for the user's AI tools (Cursor, Claude Code, Copilot, etc.)</li>
 * </ol>
 *
 * <p>This service performs step 1 and offers a prompt-then-run flow for step 2.
 * Callers are responsible for any UI-specific reactions (e.g., reverting a
 * combo selection on CLI failure).
 */
@Service(Service.Level.PROJECT)
public final class WorkflowProfileSwitchService {
    private static final Logger LOG = Logger.getInstance(WorkflowProfileSwitchService.class);

    public enum Outcome {
        /** CLI succeeded; the new profile is persisted and the workflows cache refreshed. */
        SWITCHED,
        /** CLI returned an error or threw; nothing was persisted. */
        CLI_FAILURE,
        /** CLI not detected; persisted locally, no CLI side effects. */
        CLI_UNAVAILABLE
    }

    public record SwitchResult(Outcome outcome, @Nullable String error) {}

    private final Project project;

    /** The profile a background switch is moving to, or {@code null}. */
    private volatile @Nullable String inFlight;

    /** The profile a background switch is moving to right now, or {@code null}: what Settings should compare against. */
    public @Nullable String profileInFlight() {
        return inFlight;
    }

    /** Runs the {@code config profile} command; a seam so tests can see which thread it runs on. */
    private volatile CliCall cli = CliRunner::run;

    @FunctionalInterface
    interface CliCall {
        CliRunner.CliResult run(Project project, com.johnnyblabs.openspec.util.CliArgs args) throws CliRunner.CliException;
    }

    public WorkflowProfileSwitchService(Project project) {
        this.project = project;
    }

    /**
     * Switches the profile off the EDT and hands the result to {@code onDone} on the EDT. Call from the EDT: the
     * modality there is captured, so a caller inside a modal dialog (Settings) gets its result while the dialog is
     * still open, to revert its selection on failure. The CLI can take seconds to start, or hang until the CLI
     * timeout, so the switch never runs on the EDT.
     */
    public void switchProfileInBackground(String newProfile, Consumer<SwitchResult> onDone) {
        // One switch at a time: Apply then OK, or two quick picks in the status bar, must not run the CLI twice and
        // offer the update twice. A request while one is running is dropped; the running one decides the profile.
        synchronized (this) {
            if (inFlight != null) {
                LOG.info("Profile switch to '" + newProfile + "' ignored: a switch to '" + inFlight + "' is running");
                return;
            }
            inFlight = newProfile;
        }
        ModalityState modality = ModalityState.defaultModalityState();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            SwitchResult result;
            try {
                result = switchProfile(newProfile);
            } catch (RuntimeException e) {
                LOG.warn("Profile switch failed", e);
                result = new SwitchResult(Outcome.CLI_FAILURE, e.getMessage());
            }
            SwitchResult done = result;
            ApplicationManager.getApplication().invokeLater(() -> {
                inFlight = null;
                if (!project.isDisposed()) onDone.accept(done);
            }, modality);
        });
    }

    /**
     * Switches the workflow profile via CLI delegation. On success, persists the
     * new profile to {@link OpenSpecSettings} and refreshes {@link WorkflowProfileService}.
     * Caller should follow up with {@link #promptAndRunUpdateIfConfirmed(String)} when
     * the outcome is {@link Outcome#SWITCHED}. Runs the CLI, so never call it on the EDT; UI callers use
     * {@link #switchProfileInBackground}.
     */
    public SwitchResult switchProfile(String newProfile) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        CliDetectionService detection = project.getService(CliDetectionService.class);
        WorkflowProfileService wps = project.getService(WorkflowProfileService.class);

        if (detection == null || !detection.isAvailable()) {
            settings.setProfile(newProfile);
            if (wps != null) wps.refresh();
            OpenSpecNotifier.info(project, "Profile",
                    "Profile set to '" + newProfile + "' locally. Install OpenSpec CLI to sync globally.");
            return new SwitchResult(Outcome.CLI_UNAVAILABLE, null);
        }

        Application app = ApplicationManager.getApplication();
        if (app != null && app.isDispatchThread()) {
            // A programming error: the CLI can block for up to its timeout. Use switchProfileInBackground.
            LOG.error("switchProfile runs the OpenSpec CLI and must not be called on the EDT");
        }
        try {
            CliRunner.CliResult result = cli.run(project, com.johnnyblabs.openspec.util.CliCommands.configProfile(newProfile));
            if (result.isSuccess()) {
                settings.setProfile(newProfile);
                if (wps != null) wps.refresh();
                return new SwitchResult(Outcome.SWITCHED, null);
            }
            String errorMsg = result.stderr().isEmpty()
                    ? "exit code " + result.exitCode()
                    : result.stderr().trim();
            OpenSpecNotifier.warn(project, "Profile Switch",
                    "Failed to switch profile via CLI: " + errorMsg);
            return new SwitchResult(Outcome.CLI_FAILURE, errorMsg);
        } catch (CliRunner.CliException e) {
            LOG.warn("CLI profile switch failed", e);
            OpenSpecNotifier.warn(project, "Profile Switch",
                    "Failed to switch profile: " + e.getMessage());
            return new SwitchResult(Outcome.CLI_FAILURE, e.getMessage());
        }
    }

    /**
     * Shows the OpenSpec two-step profile change prompt: <em>"Profile changed to X.
     * Run openspec update now to install skills for your AI tools?"</em>. If the user
     * confirms, runs {@code openspec update} asynchronously and reports completion via
     * the {@code OpenSpec.System} notification group. The "Later" choice is a no-op.
     *
     * <p>Should only be called after a {@link Outcome#SWITCHED} outcome.
     */
    public void promptAndRunUpdateIfConfirmed(String newProfile) {
        UpdatePreviewService preview = UpdatePreviewService.getInstance(project);
        if (preview != null && preview.supportsPreview()) {
            // CLI 1.15+: the preview of what the update will change is the question; applying it is the consent.
            preview.previewAndApply(UpdatePreviewService.Origin.PROFILE_SWITCH, null, this::reportUpdate);
            return;
        }
        int choice = Messages.showYesNoDialog(project,
                "Profile changed to '" + newProfile + "'.\n\n" +
                        "Run `openspec update` now to install skills for your AI tools " +
                        "(Cursor, Claude Code, GitHub Copilot, etc.)?",
                "OpenSpec Profile Updated",
                "Yes, update now",
                "Later",
                Messages.getQuestionIcon());
        if (choice == Messages.YES) {
            runUpdate(ModalityState.defaultModalityState());
        }
    }

    /**
     * Runs {@code openspec update} on a background thread and reports success or
     * failure via notifications. Package-private so tests can invoke directly.
     */
    void runUpdate(ModalityState modality) {
        // The report comes back under the caller's modality, so it shows while Settings is still open.
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                CliRunner.CliResult result = CliRunner.run(project, com.johnnyblabs.openspec.util.CliCommands.update());
                ApplicationManager.getApplication().invokeLater(() -> reportUpdate(result), modality);
            } catch (CliRunner.CliException e) {
                LOG.warn("openspec update failed", e);
                ApplicationManager.getApplication().invokeLater(() ->
                        OpenSpecNotifier.warn(project, "Profile",
                                "openspec update failed: " + e.getMessage()), modality);
            }
        });
    }

    /** Reports a finished profile-switch update. EDT. */
    void reportUpdate(CliRunner.CliResult result) {
        if (result.isSuccess()) {
            OpenSpecNotifier.info(project, "Profile",
                    "Skills updated. Run your AI tool to pick up the new commands.");
        } else {
            String err = result.stderr().isEmpty()
                    ? "exit code " + result.exitCode()
                    : result.stderr().trim();
            OpenSpecNotifier.warn(project, "Profile",
                    "openspec update failed: " + err);
        }
    }

    @TestOnly
    void setCliForTest(@Nullable CliCall call) {
        cli = call == null ? CliRunner::run : call;
    }
}

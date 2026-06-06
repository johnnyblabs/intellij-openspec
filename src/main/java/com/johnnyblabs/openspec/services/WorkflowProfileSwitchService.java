package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.Nullable;

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

    public WorkflowProfileSwitchService(Project project) {
        this.project = project;
    }

    /**
     * Switches the workflow profile via CLI delegation. On success, persists the
     * new profile to {@link OpenSpecSettings} and refreshes {@link WorkflowProfileService}.
     * Caller should follow up with {@link #promptAndRunUpdateIfConfirmed(String)} when
     * the outcome is {@link Outcome#SWITCHED}.
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

        try {
            CliRunner.CliResult result = CliRunner.run(project, "config", "profile", newProfile);
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
        int choice = Messages.showYesNoDialog(project,
                "Profile changed to '" + newProfile + "'.\n\n" +
                        "Run `openspec update` now to install skills for your AI tools " +
                        "(Cursor, Claude Code, GitHub Copilot, etc.)?",
                "OpenSpec Profile Updated",
                "Yes, update now",
                "Later",
                Messages.getQuestionIcon());
        if (choice == Messages.YES) {
            runUpdate();
        }
    }

    /**
     * Runs {@code openspec update} on a background thread and reports success or
     * failure via notifications. Package-private so tests can invoke directly.
     */
    void runUpdate() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                CliRunner.CliResult result = CliRunner.run(project, "update");
                ApplicationManager.getApplication().invokeLater(() -> {
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
                });
            } catch (CliRunner.CliException e) {
                LOG.warn("openspec update failed", e);
                ApplicationManager.getApplication().invokeLater(() ->
                        OpenSpecNotifier.warn(project, "Profile",
                                "openspec update failed: " + e.getMessage()));
            }
        });
    }
}

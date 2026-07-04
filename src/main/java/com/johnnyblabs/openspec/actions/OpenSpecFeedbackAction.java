package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.dialogs.FeedbackDialog;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

/**
 * Send OpenSpec Feedback — collects a message (and optional body) and delegates to
 * {@code openspec feedback <message> [--body <body>]} on a background thread.
 *
 * <p>Feedback submission is a pure CLI capability with no built-in fallback and no
 * version gate (the command predates the plugin's CLI floor), so the action is
 * <b>hidden</b> — not merely disabled — when no CLI is detected.</p>
 */
public class OpenSpecFeedbackAction extends OpenSpecBaseAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || !OpenSpecFileUtil.isOpenSpecProject(project)) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        CliDetectionService detection = project.getService(CliDetectionService.class);
        boolean cliAvailable = detection != null && detection.isAvailable();
        e.getPresentation().setEnabledAndVisible(cliAvailable);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        FeedbackDialog dialog = new FeedbackDialog(project);
        if (!dialog.showAndGet()) return;

        String message = dialog.getMessage();
        String body = dialog.getBody();
        if (message.isEmpty()) return; // dialog validation already blocks this

        new Task.Backgroundable(project, "Sending OpenSpec feedback", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    CliRunner.CliResult result = CliRunner.run(project, buildCliArgs(message, body));
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (result.isSuccess()) {
                            OpenSpecNotifier.info(project, "Feedback",
                                    "Feedback sent to the OpenSpec maintainers. Thank you!");
                        } else {
                            OpenSpecNotifier.error(project,
                                    "Sending feedback failed: " + result.stderr());
                        }
                    });
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            OpenSpecNotifier.error(project, "Sending feedback failed: " + ex.getMessage()));
                }
            }
        }.queue();
    }

    /**
     * CLI argument construction: {@code feedback <message>} plus {@code --body <body>}
     * only when a non-blank body was provided.
     */
    static String[] buildCliArgs(String message, String body) {
        if (body == null || body.isBlank()) {
            return new String[]{"feedback", message};
        }
        return new String[]{"feedback", message, "--body", body};
    }
}

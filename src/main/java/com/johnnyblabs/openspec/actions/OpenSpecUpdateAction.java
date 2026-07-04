package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Runs {@code openspec update} to refresh agent instruction files.
 *
 * <p>This action delegates entirely to the CLI — there is no built-in fallback
 * because agent instruction refresh is a CLI-only capability. When the CLI is
 * not detected, the action is disabled with an explanatory tooltip.</p>
 */
public class OpenSpecUpdateAction extends OpenSpecCliAction {

    private static final String CLI_REQUIRED_DESCRIPTION = "Install OpenSpec CLI to use this action";
    private static final String STANDARD_DESCRIPTION = "Refresh agent instruction files via openspec update";

    @Override
    protected void showOutput(Project project, com.johnnyblabs.openspec.util.CliRunner.CliResult result) {
        super.showOutput(project, result);
        // The CLI reports pending skills-migration cleanup with exit 0 and remediations
        // (interactive run / --force) that a non-interactive console can't provide —
        // hand the outcome to the graceful cleanup flow instead of ending on bare success.
        com.johnnyblabs.openspec.services.UpdateLegacyCleanupService cleanup =
                project.getService(com.johnnyblabs.openspec.services.UpdateLegacyCleanupService.class);
        if (cleanup != null) {
            cleanup.handleUpdateResult(result.stdout());
        }
    }

    @Override
    protected String[] getCliArgs() {
        return new String[]{"update"};
    }

    @Override
    protected String getCommandLabel() {
        return "update";
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        // Hide for non-OpenSpec projects
        if (project == null || !OpenSpecFileUtil.isOpenSpecProject(project)) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        // Visible but potentially disabled
        e.getPresentation().setVisible(true);

        CliDetectionService detection = project.getService(CliDetectionService.class);
        if (detection == null || !detection.isAvailable()) {
            e.getPresentation().setEnabled(false);
            e.getPresentation().setDescription(CLI_REQUIRED_DESCRIPTION);
        } else {
            e.getPresentation().setEnabled(true);
            e.getPresentation().setDescription(STANDARD_DESCRIPTION);
        }
    }
}

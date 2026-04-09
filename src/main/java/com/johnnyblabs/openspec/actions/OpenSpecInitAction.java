package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.scaffolding.ScaffoldingService;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes an OpenSpec project structure.
 *
 * <p><b>Strategy: Built-in only.</b> Init is a write operation that creates the
 * {@code openspec/} directory tree (config.yaml, specs/, changes/, archive/).
 * The built-in scaffolding ensures a predictable structure regardless of CLI
 * availability or CLI version differences. The CLI's {@code openspec init} may
 * produce a slightly different layout; using built-in avoids that divergence.</p>
 */
public class OpenSpecInitAction extends OpenSpecBaseAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        // Init is available even in non-OpenSpec projects
        e.getPresentation().setEnabledAndVisible(project != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        if (OpenSpecFileUtil.isOpenSpecProject(project)) {
            OpenSpecNotifier.info(project, "Initialize", "OpenSpec is already initialized in this project");
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Initializing OpenSpec", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // initOpenSpec() uses WriteAction internally, so dispatch to EDT via invokeLater
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        ScaffoldingService scaffolding = project.getService(ScaffoldingService.class);
                        scaffolding.initOpenSpec();
                        OpenSpecNotifier.info(project, "Initialize", "OpenSpec initialized");
                        refreshToolWindow(project);
                    } catch (Exception ex) {
                        OpenSpecNotifier.error(project, "Initialize",
                                "Failed to initialize OpenSpec: " + ex.getMessage());
                    }
                });
            }
        });
    }
}

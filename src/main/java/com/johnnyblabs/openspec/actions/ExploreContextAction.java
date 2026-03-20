package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.ExploreContextService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * Assembles project context and copies it to the clipboard for use with an AI tool in explore mode.
 * Delegates context assembly to {@link ExploreContextService}.
 */
public class ExploreContextAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ExploreContextService service = project.getService(ExploreContextService.class);
            if (service == null) return;

            String contextText = service.assembleContext();

            // Copy to clipboard on EDT
            ApplicationManager.getApplication().invokeLater(() -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(contextText), null);
                OpenSpecNotifier.info(project, "Explore Context",
                        "Context copied \u2014 paste into your AI tool to start exploring.");
            });
        });
    }
}

package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.johnnyb.openspec.dialogs.SetupWizardDialog;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class OpenSpecToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        GettingStartedPanel gettingStarted = new GettingStartedPanel(project, toolWindow);
        GettingStartedPanel.State state = gettingStarted.detectState();

        if (state == GettingStartedPanel.State.READY) {
            createNormalContent(project, toolWindow);
        } else {
            createGettingStartedContent(project, toolWindow, gettingStarted);
        }

        // Auto-launch wizard on first open if setup has never been completed
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (!settings.isSetupCompleted()) {
            ApplicationManager.getApplication().invokeLater(() -> {
                SetupWizardDialog dialog = new SetupWizardDialog(project);
                dialog.show();
                // Rebuild tool window after wizard completes
                toolWindow.getContentManager().removeAllContents(true);
                GettingStartedPanel refreshed = new GettingStartedPanel(project, toolWindow);
                if (refreshed.detectState() == GettingStartedPanel.State.READY) {
                    createNormalContent(project, toolWindow);
                } else {
                    createGettingStartedContent(project, toolWindow, refreshed);
                }
            });
        }
    }

    static void createNormalContent(Project project, ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.getInstance();

        // Browse tab (tree view)
        OpenSpecToolWindowPanel browsePanel = new OpenSpecToolWindowPanel(project);
        Content browseContent = contentFactory.createContent(browsePanel, "Browse", false);
        toolWindow.getContentManager().addContent(browseContent);

        // Console tab (CLI output)
        OpenSpecConsolePanel consolePanel = new OpenSpecConsolePanel(project);
        Content consoleContent = contentFactory.createContent(consolePanel, "Console", false);
        toolWindow.getContentManager().addContent(consoleContent);

        // Register with service for shared access
        OpenSpecConsoleService consoleService = project.getService(OpenSpecConsoleService.class);
        if (consoleService != null) {
            consoleService.register(consolePanel);
        }
    }

    private void createGettingStartedContent(Project project, ToolWindow toolWindow, GettingStartedPanel panel) {
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "Get Started", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
}

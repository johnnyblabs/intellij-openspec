package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

public class OpenSpecToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
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

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return OpenSpecFileUtil.isOpenSpecProject(project);
    }
}

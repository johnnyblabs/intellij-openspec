package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.johnnyb.openspec.scaffolding.ScaffoldingService;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class OpenSpecToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        if (OpenSpecFileUtil.isOpenSpecProject(project)) {
            createNormalContent(project, toolWindow);
        } else {
            createWelcomeContent(project, toolWindow);
        }
    }

    private void createNormalContent(Project project, ToolWindow toolWindow) {
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

    private void createWelcomeContent(Project project, ToolWindow toolWindow) {
        JPanel welcomePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = JBUI.insets(8);

        JBLabel title = new JBLabel("OpenSpec");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        welcomePanel.add(title, gbc);

        gbc.gridy++;
        JBLabel description = new JBLabel(
                "<html><body style='width:260px; text-align:center;'>" +
                "Spec-driven development for your project. " +
                "Define requirements, generate artifacts, and track changes." +
                "</body></html>");
        description.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        welcomePanel.add(description, gbc);

        gbc.gridy++;
        gbc.insets = JBUI.insets(16, 8, 8, 8);
        JButton initButton = new JButton("Initialize OpenSpec");
        initButton.addActionListener(e -> {
            try {
                ScaffoldingService scaffolding = project.getService(ScaffoldingService.class);
                scaffolding.initOpenSpec();
                OpenSpecNotifier.info(project, "OpenSpec initialized");

                // Rebuild the tool window with normal content
                VirtualFileManager.getInstance().asyncRefresh(() ->
                    ApplicationManager.getApplication().invokeLater(() -> {
                        toolWindow.getContentManager().removeAllContents(true);
                        createNormalContent(project, toolWindow);
                    })
                );
            } catch (Exception ex) {
                OpenSpecNotifier.error(project, "Failed to initialize: " + ex.getMessage());
            }
        });
        welcomePanel.add(initButton, gbc);

        gbc.gridy++;
        gbc.insets = JBUI.insets(4, 8, 8, 8);
        JBLabel hint = new JBLabel(
                "<html><small>Creates openspec/ directory with config, specs, and changes.</small></html>");
        hint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        welcomePanel.add(hint, gbc);

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(welcomePanel, "Get Started", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
}

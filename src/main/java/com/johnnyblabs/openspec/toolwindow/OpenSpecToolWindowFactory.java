package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.dialogs.SetupWizardDialog;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class OpenSpecToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        GettingStartedPanel gettingStarted = new GettingStartedPanel(project, toolWindow);
        GettingStartedPanel.State state = gettingStarted.detectState();

        if (state == GettingStartedPanel.State.NOT_INITIALIZED) {
            // Project has no openspec/ directory — show Getting Started only
            createGettingStartedContent(project, toolWindow, gettingStarted);
        } else {
            // Project is initialized — always show the tree view so users can
            // browse specs even without changes or AI configured
            createNormalContent(project, toolWindow);
        }

        // Re-detect CLI when the tool window is shown (throttled). Also runs a D3 fallback
        // profile refresh so any drift from a manual CLI switch or terminal customize
        // handshake the user dismissed without confirming is picked up on next focus.
        project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            @Override
            public void toolWindowShown(@NotNull ToolWindow tw) {
                if (!"OpenSpec".equals(tw.getId())) return;
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    CliDetectionService detection = project.getService(CliDetectionService.class);
                    if (detection != null && detection.detectIfStale()) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            Content browseContent = tw.getContentManager().findContent("Browse");
                            if (browseContent != null) {
                                Component component = browseContent.getComponent();
                                if (component instanceof OpenSpecToolWindowPanel panel) {
                                    panel.updateCliStatus();
                                }
                            }
                        });
                    }
                    WorkflowProfileService profileService = project.getService(WorkflowProfileService.class);
                    if (profileService != null) {
                        try {
                            profileService.refresh();
                        } catch (Throwable ignored) {
                            // Best-effort; silent failure is acceptable for fallback triggers.
                        }
                    }
                });
            }
        });

        // Auto-launch wizard on first open if setup has never been completed
        // Skip for any initialized project — the tree view is more valuable
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (!settings.isSetupCompleted()) {
            if (state == GettingStartedPanel.State.NOT_INITIALIZED) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    SetupWizardDialog dialog = new SetupWizardDialog(project);
                    dialog.show();
                    // Rebuild tool window after wizard completes
                    toolWindow.getContentManager().removeAllContents(true);
                    GettingStartedPanel refreshed = new GettingStartedPanel(project, toolWindow);
                    if (refreshed.detectState() == GettingStartedPanel.State.NOT_INITIALIZED) {
                        createGettingStartedContent(project, toolWindow, refreshed);
                    } else {
                        createNormalContent(project, toolWindow);
                    }
                });
            } else {
                settings.setSetupCompleted(true);
            }
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

        // Explore tab — only when Direct API is configured (inline input requires it)
        DirectApiService apiService = project.getService(DirectApiService.class);
        if (apiService != null && apiService.isConfigured()) {
            ExplorePanel explorePanel = new ExplorePanel(project);
            Content exploreContent = contentFactory.createContent(explorePanel, "Explore", false);
            exploreContent.setDisposer(explorePanel);
            toolWindow.getContentManager().addContent(exploreContent);

            ExplorePanelService explorePanelService = project.getService(ExplorePanelService.class);
            if (explorePanelService != null) {
                explorePanelService.register(explorePanel);
            }
        }

        // Register with services for shared access
        OpenSpecConsoleService consoleService = project.getService(OpenSpecConsoleService.class);
        if (consoleService != null) {
            consoleService.register(consolePanel);
        }
    }

    private void createGettingStartedContent(Project project, ToolWindow toolWindow, GettingStartedPanel panel) {
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, "Get Started", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
}

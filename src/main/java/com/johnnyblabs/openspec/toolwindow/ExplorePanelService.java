package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.johnnyblabs.openspec.ai.DirectApiService;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class ExplorePanelService {

    private final Project project;
    private ExplorePanel explorePanel;

    public ExplorePanelService(Project project) {
        this.project = project;
    }

    public void register(ExplorePanel panel) {
        this.explorePanel = panel;
    }

    @Nullable
    public ExplorePanel getExplorePanel() {
        return explorePanel;
    }

    /**
     * Returns the explore panel and activates the Explore tab in the tool window.
     * Lazily creates the tab if Direct API is now configured but the tab wasn't
     * created at startup.
     */
    @Nullable
    public ExplorePanel getAndActivate() {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
        if (toolWindow == null) return null;

        // Lazy creation: if no panel yet but Direct API is now configured, create the tab
        if (explorePanel == null) {
            DirectApiService apiService = project.getService(DirectApiService.class);
            if (apiService == null || !apiService.isConfigured()) return null;

            ExplorePanel panel = new ExplorePanel(project);
            Content exploreContent = ContentFactory.getInstance().createContent(panel, "Explore", false);
            exploreContent.setDisposer(panel);
            addExploreTab(toolWindow.getContentManager(), exploreContent);
            this.explorePanel = panel;
        }

        toolWindow.activate(() -> {
            var contentManager = toolWindow.getContentManager();
            Content exploreContent = contentManager.findContent("Explore");
            if (exploreContent != null) {
                contentManager.setSelectedContent(exploreContent);
            }
        });
        return explorePanel;
    }

    /**
     * Adds the Explore tab right after Browse, before Console and Coordination: Explore is the first step, so it sits
     * beside the tab it starts from, whenever it's created. Without a Browse tab it's appended.
     */
    static void addExploreTab(ContentManager contentManager, Content exploreContent) {
        Content browse = contentManager.findContent("Browse");
        if (browse == null) {
            contentManager.addContent(exploreContent);
        } else {
            contentManager.addContent(exploreContent, contentManager.getIndexOfContent(browse) + 1);
        }
    }
}

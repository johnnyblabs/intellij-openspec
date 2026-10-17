package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.coordination.CoordinationData;
import com.johnnyblabs.openspec.coordination.CoordinationService;
import com.johnnyblabs.openspec.coordination.CoordinationTier;
import com.johnnyblabs.openspec.dialogs.SetupWizardDialog;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.services.WorkflowSchemaContextService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class OpenSpecToolWindowFactory implements ToolWindowFactory, DumbAware {

    /**
     * Whether an IDE-window activation should refresh {@code project}'s CLI status: only when the window that
     * regained focus is this project's (another project's window, or the welcome frame, refreshes nothing here).
     */
    static boolean refreshesOnActivation(IdeFrame ideFrame, Project project) {
        return ideFrame != null && project != null && ideFrame.getProject() == project;
    }

    /**
     * Subscribes to the IDE window regaining focus on the application message bus, for {@code parent}'s lifetime,
     * running {@code refresh} when the activated window is {@code project}'s.
     */
    static void subscribeActivation(Project project, com.intellij.openapi.Disposable parent, Runnable refresh) {
        ApplicationManager.getApplication().getMessageBus().connect(parent)
                .subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
                    @Override
                    public void applicationActivated(@NotNull IdeFrame ideFrame) {
                        if (refreshesOnActivation(ideFrame, project)) {
                            refresh.run();
                        }
                    }
                });
    }

    /**
     * Re-detects the CLI off the EDT (throttled to once per 30s by {@code detectIfStale}); a detection that runs
     * announces itself and the Browse panel redraws its CLI status from that. Also a best-effort workflow-profile refresh. Shared by the
     * tool-window-shown and IDE-focus-regained triggers so the two paths cannot drift.
     */
    private static void refreshCliStatus(Project project, ToolWindow tw) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CliDetectionService detection = project.getService(CliDetectionService.class);
            if (detection != null) {
                // A detection that runs announces itself on CliDetectionService.TOPIC; the Browse panel redraws its
                // CLI status from that, so nothing to repaint here.
                detection.detectIfStale();
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

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Detect via the static form so no throwaway, Disposer-registering panel is built (and then
        // leaked) on the initialized path; construct the panel only when it's actually shown.
        GettingStartedPanel.State state = GettingStartedPanel.detectState(project);

        if (state == GettingStartedPanel.State.NOT_INITIALIZED) {
            // Project has no openspec/ directory — show Getting Started only
            createGettingStartedContent(project, toolWindow, new GettingStartedPanel(project, toolWindow));
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
                refreshCliStatus(project, tw);
            }
        });

        // Also re-detect when this project's IDE window regains focus: a tool window that stays visible never
        // fires toolWindowShown again, so a CLI upgraded mid-session would otherwise go unnoticed. Same routine,
        // same 30s throttle (inside detectIfStale), so window switching never spawns repeated CLI runs. Scoped to
        // the tool window's lifetime.
        subscribeActivation(project, toolWindow.getDisposable(), () -> refreshCliStatus(project, toolWindow));

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
                    if (GettingStartedPanel.detectState(project) == GettingStartedPanel.State.NOT_INITIALIZED) {
                        createGettingStartedContent(project, toolWindow, new GettingStartedPanel(project, toolWindow));
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
        browseContent.setDisposer(browsePanel);
        toolWindow.getContentManager().addContent(browseContent);

        // Console tab (CLI output)
        OpenSpecConsolePanel consolePanel = new OpenSpecConsolePanel(project);
        Content consoleContent = contentFactory.createContent(consolePanel, "Console", false);
        consoleContent.setDisposer(consolePanel);
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

        // Coordination tab — added asynchronously only when 1.4 coordination state/mode is
        // detected (tier != HIDDEN). Resolution touches the CLI/disk, so it runs off the EDT.
        maybeAddCoordinationTab(project, toolWindow);
    }

    private static void maybeAddCoordinationTab(Project project, ToolWindow toolWindow) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            CoordinationService service = project.getService(CoordinationService.class);
            if (service == null) return;
            WorkflowSchemaContextService schemaCtx = project.getService(WorkflowSchemaContextService.class);
            boolean coordinationMode = schemaCtx != null && schemaCtx.hasNonDefaultModeCached();
            CoordinationData data = service.getCoordinationData(coordinationMode);
            if (data.tier() == CoordinationTier.HIDDEN) return;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (toolWindow.isDisposed()) return;
                if (toolWindow.getContentManager().findContent("Coordination") != null) return;
                ContentFactory contentFactory = ContentFactory.getInstance();
                CoordinationPanel panel = new CoordinationPanel(project, data);
                Content content = contentFactory.createContent(panel, "Coordination", false);
                toolWindow.getContentManager().addContent(content);
            });
        });
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

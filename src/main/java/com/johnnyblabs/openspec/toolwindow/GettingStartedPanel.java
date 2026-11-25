package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.johnnyblabs.openspec.dialogs.SetupWizardDialog;
import com.johnnyblabs.openspec.scaffolding.ScaffoldingService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.root.OpenSpecRootResolver;
import com.johnnyblabs.openspec.root.RootResolution;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GettingStartedPanel extends JPanel implements Disposable {

    private static final Icon BRAND_ICON = IconLoader.getIcon("/icons/openspec-brand.svg", GettingStartedPanel.class);

    public enum State {
        /** The first root resolution hasn't finished; a store-backed or ancestor root may still turn up. */
        RESOLVING,
        NOT_INITIALIZED,
        NO_AI_CONFIGURED,
        NO_CHANGES,
        READY
    }

    private final Project project;
    private final @Nullable ToolWindow toolWindow;
    private final Alarm refreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private State lastState;

    public GettingStartedPanel(Project project) {
        this(project, null);
    }

    public GettingStartedPanel(Project project, @Nullable ToolWindow toolWindow) {
        super(new GridBagLayout());
        this.project = project;
        this.toolWindow = toolWindow;
        // Subscribe before reading the state, so a root that resolves in between (a store, an ancestor) isn't missed.
        // A root that resolves later, or a pointer that breaks, moves this panel on.
        project.getMessageBus().connect(this).subscribe(OpenSpecRootResolver.TOPIC,
                (OpenSpecRootResolver.Listener) snapshot -> {
                    refreshAlarm.cancelAllRequests();
                    refreshAlarm.addRequest(this::onFileSystemChanged, 0);
                });
        this.lastState = detectState();
        registerFileListener();
        resolveRealAgents();
        rebuild();
    }

    @Override
    public void dispose() {
        // Alarm is disposed automatically via Disposer parent (this)
    }

    /** Instance convenience — delegates to the static form. */
    public State detectState() {
        return detectState(project);
    }

    /**
     * Detects tool-window state from the project alone, so callers that only need the state (the
     * tool-window factory) don't construct a Disposer-registering panel that is then discarded and
     * leaks. Reads only {@code project}.
     */
    public static State detectState(Project project) {
        if (!OpenSpecFileUtil.isOpenSpecProject(project)) {
            return OpenSpecRootResolver.getInstance(project).snapshot().pending()
                    ? State.RESOLVING : State.NOT_INITIALIZED;
        }
        // Check for active or archived changes first — if the project has been
        // used, skip onboarding even if AI isn't configured yet
        var changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir != null) {
            for (var child : changesDir.getChildren()) {
                if (child.isDirectory() && !"archive".equals(child.getName())) {
                    return State.READY;
                }
            }
            var archiveDir = changesDir.findChild("archive");
            if (archiveDir != null && archiveDir.getChildren().length > 0) {
                return State.READY;
            }
        }
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (settings.getPreferredDeliveryMethod() == null || settings.getPreferredDeliveryMethod().isEmpty()) {
            return State.NO_AI_CONFIGURED;
        }
        return State.NO_CHANGES;
    }

    public void rebuild() {
        removeAll();
        State state = detectState();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = JBUI.insets(8);

        // Brand icon
        gbc.insets = JBUI.insets(12, 8, 4, 8);
        add(new JBLabel(BRAND_ICON), gbc);

        // Title
        gbc.gridy++;
        gbc.insets = JBUI.insets(2, 8, 0, 8);
        JBLabel title = new JBLabel("OpenSpec");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, gbc);

        // Tagline
        gbc.gridy++;
        gbc.insets = JBUI.insets(0, 8, 8, 8);
        JBLabel tagline = new JBLabel("Spec-Driven Development");
        tagline.setFont(tagline.getFont().deriveFont(Font.PLAIN, 12f));
        tagline.setForeground(com.intellij.ui.JBColor.GRAY);
        add(tagline, gbc);

        gbc.gridy++;
        gbc.insets = JBUI.insets(8);

        switch (state) {
            case RESOLVING -> add(createCard("Finding the OpenSpec root\u2026",
                    "Checking this project, its parent folders and any OpenSpec store it points at.", null), gbc);
            case NOT_INITIALIZED -> {
                RootResolution.Problem problem = OpenSpecRootResolver.getInstance(project).snapshot().problem();
                if (problem != null) {
                    // The project points at a store that can't be used: say why, instead of offering a fresh init.
                    add(createCard(com.johnnyblabs.openspec.root.RootProvenance.problemTitle(problem), problemText(problem),
                            null), gbc);
                    break;
                }
                add(createCard(
                        "Initialize your project",
                        "Create the openspec/ directory to get started with spec-driven development.",
                        createInitButton()
                ), gbc);
                gbc.gridy++;
                add(createWizardLink(), gbc);
            }
            case NO_AI_CONFIGURED -> {
                add(createCard(
                        "Configure your AI tool",
                        "Set up your preferred AI tool and delivery method for artifact generation.",
                        createConfigureButton()
                ), gbc);
                gbc.gridy++;
                add(createManageToolsLink(), gbc);
                gbc.gridy++;
                add(createWizardLink(), gbc);
            }
            case NO_CHANGES -> {
                add(createCard(
                        "Create your first change",
                        "A change is a scoped unit of work — one feature, fix, or improvement. " +
                        "Describe what you want to build, and Propose creates the change with every artifact " +
                        "needed before you implement. Keep it focused: one change per feature.",
                        createProposeButton()
                ), gbc);
                gbc.gridy++;
                add(createExploreLink(), gbc);
            }
            case READY -> {
                // Should not be shown — caller should display normal tool window
            }
        }

        revalidate();
        repaint();
    }

    private void registerFileListener() {
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    var file = event.getFile();
                    if (file == null) continue;
                    // Watch openspec/ changes and tool directory creation (e.g., .claude/, .github/)
                    boolean relevant = OpenSpecFileUtil.isUnderOpenSpec(file, project)
                            || isToolDirectoryEvent(file);
                    if (relevant) {
                        refreshAlarm.cancelAllRequests();
                        refreshAlarm.addRequest(() -> onFileSystemChanged(), 300);
                        break;
                    }
                }
            }
        });
    }

    private void onFileSystemChanged() {
        if (project.isDisposed()) return;
        // Off the EDT: the state reads the file system and the project model, so under a read action.
        State newState = com.intellij.openapi.application.ApplicationManager.getApplication()
                .runReadAction((com.intellij.openapi.util.Computable<State>) this::detectState);
        if (newState == lastState) return;
        State oldState = lastState;
        lastState = newState;
        // An already-set-up root that finished resolving gets the normal tool window, as on a fresh open.
        boolean resolvedToProject = oldState == State.RESOLVING && newState != State.NOT_INITIALIZED;

        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
            if ((newState == State.READY || resolvedToProject) && toolWindow != null) {
                toolWindow.getContentManager().removeAllContents(true);
                OpenSpecToolWindowFactory.createNormalContent(project, toolWindow);
            } else {
                rebuild();
            }
        }, project.getDisposed());
    }

    /** The CLI's message, then its fix unless the message already ends with it (upstream does that for the doctor hint). */
    public static String problemText(RootResolution.Problem problem) {
        String fix = problem.fix();
        return fix == null || fix.isBlank() || problem.message().contains(fix)
                ? problem.message() : problem.message() + " " + fix;
    }

    private JPanel createCard(String title, String description, JButton button) {
        return EmptyStateFactory.createPanel(null, title, description, button);
    }

    private JButton createInitButton() {
        JButton btn = new JButton("Initialize OpenSpec");
        btn.addActionListener(e -> {
            btn.setEnabled(false);
            project.getService(ScaffoldingService.class).initOpenSpec(
                    com.intellij.openapi.application.ModalityState.stateForComponent(btn), dir -> rebuild(), ex -> {
                        btn.setEnabled(true);
                        com.johnnyblabs.openspec.util.OpenSpecNotifier.notify(project,
                                com.johnnyblabs.openspec.util.OpenSpecNotifier.GROUP_SYSTEM, "Initialize",
                                "Failed to initialize: " + ex.getMessage(),
                                com.intellij.notification.NotificationType.ERROR);
                    });
        });
        return btn;
    }

    private JButton createConfigureButton() {
        JButton btn = new JButton("Configure AI");
        btn.addActionListener(e -> {
            SetupWizardDialog dialog = new SetupWizardDialog(project);
            dialog.show();
            rebuild();
        });
        return btn;
    }

    private JButton createProposeButton() {
        JButton btn = new JButton("Propose a Change");
        btn.addActionListener(e -> {
            AnAction action = ActionManager.getInstance().getAction("OpenSpec.Propose");
            if (action != null) {
                com.johnnyblabs.openspec.actions.ActionLauncher.launch(action, btn, "GettingStartedPanel");
            }
            // Transition to tree view if a change was created
            if (toolWindow != null && detectState() == State.READY) {
                toolWindow.getContentManager().removeAllContents(true);
                OpenSpecToolWindowFactory.createNormalContent(project, toolWindow);
            }
        });
        return btn;
    }

    /** "Explore an idea first", beside Propose: the quickstart's first step, before a change exists. */
    HyperlinkLabel createExploreLink() {
        HyperlinkLabel link = new HyperlinkLabel("Explore an idea first");
        link.addHyperlinkListener(e -> {
            AnAction action = ActionManager.getInstance().getAction("OpenSpec.Explore");
            if (action != null) {
                com.johnnyblabs.openspec.actions.ActionLauncher.launch(action, link, "GettingStartedPanel");
            }
        });
        return link;
    }

    private HyperlinkLabel createWizardLink() {
        HyperlinkLabel link = new HyperlinkLabel("Run Setup Wizard");
        link.addHyperlinkListener(e -> {
            SetupWizardDialog dialog = new SetupWizardDialog(project);
            dialog.show();
            rebuild();
        });
        return link;
    }

    private HyperlinkLabel createManageToolsLink() {
        HyperlinkLabel link = new HyperlinkLabel("Manage AI Tools");
        link.addHyperlinkListener(e -> {
            com.johnnyblabs.openspec.dialogs.ManageAiToolsDialog dialog =
                    new com.johnnyblabs.openspec.dialogs.ManageAiToolsDialog(project);
            dialog.show();
            rebuild();
        });
        return link;
    }

    /** The real path of {@code .agents} when it is a link, resolved off the EDT ({@link #resolveRealAgents}). */
    private volatile @Nullable String realAgents;

    /** Resolves where {@code .agents} really is, on a pooled thread, so the file-event check never touches the disk. */
    private void resolveRealAgents() {
        String basePath = project.getBasePath();
        if (basePath == null) return;
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() ->
                realAgents = com.johnnyblabs.openspec.services.ToolFolderEvents.realAgents(java.nio.file.Path.of(basePath)));
    }

    private boolean isToolDirectoryEvent(com.intellij.openapi.vfs.VirtualFile file) {
        String basePath = project.getBasePath();
        if (basePath == null) return false;
        // .agents itself appeared, moved or became a link: where it really is may have changed.
        if (file.getPath().equals(basePath + "/.agents")) resolveRealAgents();
        String agents = realAgents;
        // Only needed when .agents is a link; a deleted file's canonical path isn't safe to ask for.
        String canonical = agents != null && file.isValid() ? file.getCanonicalPath() : null;
        return com.johnnyblabs.openspec.services.ToolFolderEvents.matches(basePath, file.getPath(), canonical, agents);
    }
}

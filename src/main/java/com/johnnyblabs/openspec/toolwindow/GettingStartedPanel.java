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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.johnnyblabs.openspec.dialogs.SetupWizardDialog;
import com.johnnyblabs.openspec.scaffolding.ScaffoldingService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GettingStartedPanel extends JPanel implements Disposable {

    private static final Icon OPENSPEC_ICON = IconLoader.getIcon("/icons/openspec.svg", GettingStartedPanel.class);

    public enum State {
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
        this.lastState = detectState();
        registerFileListener();
        rebuild();
    }

    @Override
    public void dispose() {
        // Alarm is disposed automatically via Disposer parent (this)
    }

    public State detectState() {
        if (!OpenSpecFileUtil.isOpenSpecProject(project)) {
            return State.NOT_INITIALIZED;
        }
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (settings.getPreferredDeliveryMethod() == null || settings.getPreferredDeliveryMethod().isEmpty()) {
            return State.NO_AI_CONFIGURED;
        }
        // Check for active changes
        var changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir != null) {
            for (var child : changesDir.getChildren()) {
                if (child.isDirectory() && !"archive".equals(child.getName())) {
                    return State.READY;
                }
            }
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

        // Title
        JBLabel title = new JBLabel("OpenSpec");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, gbc);

        gbc.gridy++;

        switch (state) {
            case NOT_INITIALIZED -> {
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
                add(createWizardLink(), gbc);
            }
            case NO_CHANGES -> {
                add(createCard(
                        "Create your first change",
                        "A change is a scoped unit of work — one feature, fix, or improvement. " +
                        "Give it a name, describe why it's needed, and OpenSpec will help you " +
                        "generate design docs, specs, and tasks. Keep it focused: one change per feature.",
                        createProposeButton()
                ), gbc);
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
                    if (file != null && OpenSpecFileUtil.isUnderOpenSpec(file, project)) {
                        refreshAlarm.cancelAllRequests();
                        refreshAlarm.addRequest(() -> onFileSystemChanged(), 300);
                        break;
                    }
                }
            }
        });
    }

    private void onFileSystemChanged() {
        State newState = detectState();
        if (newState == lastState) return;
        lastState = newState;

        SwingUtilities.invokeLater(() -> {
            if (newState == State.READY && toolWindow != null) {
                toolWindow.getContentManager().removeAllContents(true);
                OpenSpecToolWindowFactory.createNormalContent(project, toolWindow);
            } else {
                rebuild();
            }
        });
    }

    private JPanel createCard(String title, String description, JButton button) {
        return EmptyStateFactory.createPanel(OPENSPEC_ICON, title, description, button);
    }

    private JButton createInitButton() {
        JButton btn = new JButton("Initialize OpenSpec");
        btn.addActionListener(e -> {
            try {
                ScaffoldingService scaffolding = project.getService(ScaffoldingService.class);
                scaffolding.initOpenSpec();
                rebuild();
            } catch (java.io.IOException ex) {
                com.johnnyblabs.openspec.util.OpenSpecNotifier.notify(project,
                        com.johnnyblabs.openspec.util.OpenSpecNotifier.GROUP_SYSTEM, "Initialize",
                        "Failed to initialize: " + ex.getMessage(),
                        com.intellij.notification.NotificationType.ERROR);
            }
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
                DataContext context = dataId -> CommonDataKeys.PROJECT.is(dataId) ? project : null;
                Presentation presentation = action.getTemplatePresentation().clone();
                AnActionEvent event = new AnActionEvent(null, context, "GettingStartedPanel", presentation, ActionManager.getInstance(), 0);
                ActionUtil.performActionDumbAwareWithCallbacks(action, event);
            }
            // Transition to tree view if a change was created
            if (toolWindow != null && detectState() == State.READY) {
                toolWindow.getContentManager().removeAllContents(true);
                OpenSpecToolWindowFactory.createNormalContent(project, toolWindow);
            }
        });
        return btn;
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
}

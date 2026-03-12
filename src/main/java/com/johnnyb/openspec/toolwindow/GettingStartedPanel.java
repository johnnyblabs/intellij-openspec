package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.johnnyb.openspec.dialogs.SetupWizardDialog;
import com.johnnyb.openspec.scaffolding.ScaffoldingService;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.johnnyb.openspec.util.OpenSpecFileUtil;

import javax.swing.*;
import java.awt.*;

public class GettingStartedPanel extends JPanel {

    private static final Icon OPENSPEC_ICON = IconLoader.getIcon("/icons/openspec.svg", GettingStartedPanel.class);

    public enum State {
        NOT_INITIALIZED,
        NO_AI_CONFIGURED,
        NO_CHANGES,
        READY
    }

    private final Project project;

    public GettingStartedPanel(Project project) {
        super(new GridBagLayout());
        this.project = project;
        rebuild();
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
                        "Propose a new change to start defining requirements and generating artifacts.",
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
                com.johnnyb.openspec.util.OpenSpecNotifier.error(project,
                        "Failed to initialize: " + ex.getMessage());
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
                DataContext context = dataId -> com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.is(dataId) ? project : null;
                AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "GettingStartedPanel", context);
                action.actionPerformed(event);
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

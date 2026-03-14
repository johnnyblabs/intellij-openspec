package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ProposeChangeDialog extends DialogWrapper {

    private final Project project;
    private final JBTextField nameField;
    private final JBTextArea whyField;
    private final JBTextArea whatChangesField;

    public ProposeChangeDialog(Project project) {
        super(project, false);
        this.project = project;
        setTitle("Propose New Change");

        nameField = new JBTextField();
        nameField.getEmptyText().setText("e.g., add-user-auth, fix-login-redirect");
        whyField = new JBTextArea(3, 40);
        whyField.setLineWrap(true);
        whyField.setWrapStyleWord(true);
        whyField.getEmptyText().setText("e.g., Users can't reset passwords without contacting support");
        whatChangesField = new JBTextArea(4, 40);
        whatChangesField.setLineWrap(true);
        whatChangesField.setWrapStyleWord(true);
        whatChangesField.getEmptyText().setText("e.g., Add password reset endpoint and email notification");

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        FormBuilder builder = FormBuilder.createFormBuilder();

        if (!OpenSpecSettings.getInstance(project).isFirstProposalCompleted()) {
            JBLabel banner = new JBLabel("<html><body style='width:" + JBUI.scale(380) + "px'>" +
                    "<b>OpenSpec workflow:</b> Propose a change, then generate artifacts " +
                    "(design, specs, tasks), implement the tasks, and archive when done." +
                    "</body></html>");
            banner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                    JBUI.Borders.empty(0, 0, 8, 0)));
            builder.addComponent(banner);
        }

        builder.addLabeledComponent(new JBLabel("Change name:"), nameField)
                .addLabeledComponent(new JBLabel("Why:"), new JBScrollPane(whyField))
                .addLabeledComponent(new JBLabel("What Changes:"), new JBScrollPane(whatChangesField))
                .addComponentFillVertically(new JPanel(), 0);
        return builder.getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().isBlank()) {
            return new ValidationInfo("Change name is required", nameField);
        }
        return null;
    }

    public String getChangeName() {
        return nameField.getText().trim();
    }

    public String getWhy() {
        return whyField.getText().trim();
    }

    public String getWhatChanges() {
        return whatChangesField.getText().trim();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return nameField;
    }
}

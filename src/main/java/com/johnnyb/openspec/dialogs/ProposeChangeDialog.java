package com.johnnyb.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ProposeChangeDialog extends DialogWrapper {

    private final JBTextField nameField;
    private final JBTextArea whyField;
    private final JBTextArea whatChangesField;

    public ProposeChangeDialog(Project project) {
        super(project, false);
        setTitle("Propose New Change");

        nameField = new JBTextField();
        whyField = new JBTextArea(3, 40);
        whyField.setLineWrap(true);
        whyField.setWrapStyleWord(true);
        whatChangesField = new JBTextArea(4, 40);
        whatChangesField.setLineWrap(true);
        whatChangesField.setWrapStyleWord(true);

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Change name:"), nameField)
                .addLabeledComponent(new JBLabel("Why:"), new JBScrollPane(whyField))
                .addLabeledComponent(new JBLabel("What Changes:"), new JBScrollPane(whatChangesField))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
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

package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Asks for what OpenSpec's {@code new} workflow needs: a change name and, when the project has several schemas, a
 * schema. The change is created without any artifact.
 */
public class NewChangeDialog extends DialogWrapper {

    static final String HINT = "Creates the change without any artifact. Then create its artifacts one at a time, "
            + "starting with the first, from the workflow panel or with Continue.";

    private final JBTextField nameField;
    private final SchemaChoice schema;

    public NewChangeDialog(@NotNull Project project) {
        super(project, false);
        setTitle("New Change");
        nameField = new JBTextField();
        nameField.getEmptyText().setText("e.g., add-user-auth");
        nameField.getAccessibleContext().setAccessibleName("Change name");
        schema = SchemaChoice.forProject(project);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        FormBuilder builder = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Change name:"), nameField);
        if (schema.visible()) builder.addLabeledComponent(new JBLabel("Schema:"), schema.combo());
        JBLabel hint = new JBLabel("<html><body style='width:" + JBUI.scale(340) + "px'>" + HINT + "</body></html>");
        hint.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        builder.addComponent(hint);
        return builder.getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().isBlank()) return new ValidationInfo("Change name is required", nameField);
        return null;
    }

    public String getChangeName() {
        return nameField.getText().trim();
    }

    public @Nullable String getSelectedSchema() {
        return schema.selected();
    }

    public boolean isSchemaComboVisible() {
        return schema.visible();
    }

    /** Fills the name as a user would, for tests that can't show the dialog. */
    public void fillForTest(String name) {
        nameField.setText(name);
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return nameField;
    }
}

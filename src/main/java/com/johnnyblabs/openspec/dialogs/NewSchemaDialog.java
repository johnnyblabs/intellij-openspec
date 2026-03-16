package com.johnnyblabs.openspec.dialogs;

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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class NewSchemaDialog extends DialogWrapper {

    private static final Pattern KEBAB_CASE = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");

    private final JBTextField nameField;
    private final JBTextArea descriptionField;
    private final JCheckBox proposalCheckbox;
    private final JCheckBox designCheckbox;
    private final JCheckBox specsCheckbox;
    private final JCheckBox tasksCheckbox;

    public NewSchemaDialog(Project project) {
        super(project, false);
        setTitle("New Schema");

        nameField = new JBTextField();
        nameField.getEmptyText().setText("e.g., rapid-prototype, compliance-heavy");

        descriptionField = new JBTextArea(3, 40);
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);
        descriptionField.getEmptyText().setText("(optional) Brief description of this schema");

        proposalCheckbox = new JCheckBox("proposal", true);
        designCheckbox = new JCheckBox("design", true);
        specsCheckbox = new JCheckBox("specs", true);
        tasksCheckbox = new JCheckBox("tasks", true);

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel artifactPanel = new JPanel();
        artifactPanel.setLayout(new BoxLayout(artifactPanel, BoxLayout.Y_AXIS));
        artifactPanel.add(proposalCheckbox);
        artifactPanel.add(designCheckbox);
        artifactPanel.add(specsCheckbox);
        artifactPanel.add(tasksCheckbox);

        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Schema name:"), nameField)
                .addLabeledComponent(new JBLabel("Description:"), new JBScrollPane(descriptionField))
                .addLabeledComponent(new JBLabel("Artifacts:"), artifactPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            return new ValidationInfo("Schema name is required", nameField);
        }
        if (!KEBAB_CASE.matcher(name).matches()) {
            return new ValidationInfo("Schema name must be kebab-case (e.g., my-schema)", nameField);
        }
        return null;
    }

    public String getSchemaName() {
        return nameField.getText().trim();
    }

    public String getDescription() {
        return descriptionField.getText().trim();
    }

    public List<String> getSelectedArtifacts() {
        List<String> artifacts = new ArrayList<>();
        if (proposalCheckbox.isSelected()) artifacts.add("proposal");
        if (designCheckbox.isSelected()) artifacts.add("design");
        if (specsCheckbox.isSelected()) artifacts.add("specs");
        if (tasksCheckbox.isSelected()) artifacts.add("tasks");
        return artifacts;
    }

    /**
     * Returns the kebab-case validation pattern for testing.
     */
    static boolean isValidKebabCase(String name) {
        return name != null && KEBAB_CASE.matcher(name).matches();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return nameField;
    }
}

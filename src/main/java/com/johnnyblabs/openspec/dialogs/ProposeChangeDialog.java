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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Asks what to propose: a description of what the user wants to build, as OpenSpec's propose workflow takes it.
 *
 * <p>With Direct API the plugin creates the change itself, so the dialog also offers a change name (derived from the
 * description when left blank) and, when the project has several schemas, a schema; the description is required. In
 * a handoff (clipboard or editor tab) the agent's propose workflow picks the name and schema, so the dialog asks only
 * for the description, which may be empty (the agent then asks). A line under the description says which mode it is
 * in and what will happen.
 */
public class ProposeChangeDialog extends DialogWrapper {

    static final String DESCRIPTION_LABEL = "Describe what you want to build:";
    static final String FIRST_PROPOSAL_BANNER = "<b>OpenSpec workflow:</b> describe a change, and Propose creates it "
            + "with every artifact needed before you implement. Then implement the tasks, and archive when done.";

    private final Project project;
    private final boolean directApi;
    private final String modeLine;
    private final JBTextArea descriptionField;
    private final JBTextField nameField;
    private final SchemaChoice schema;

    /**
     * @param directApi whether the plugin creates and generates the change (Direct API) rather than handing off
     * @param title     the dialog title, naming the mode
     * @param modeLine  what will happen, shown under the description
     */
    public ProposeChangeDialog(@NotNull Project project, boolean directApi, @NotNull String title, @NotNull String modeLine) {
        super(project, false);
        this.project = project;
        this.directApi = directApi;
        this.modeLine = modeLine;
        setTitle(title);

        descriptionField = new JBTextArea(5, 44);
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);
        descriptionField.getEmptyText().setText("e.g., Let users reset their password by email");
        descriptionField.getAccessibleContext().setAccessibleName("Description");

        nameField = new JBTextField();
        nameField.getEmptyText().setText("Derived from the description when left blank");
        nameField.getAccessibleContext().setAccessibleName("Change name");

        schema = directApi ? SchemaChoice.forProject(project) : SchemaChoice.NONE;
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        FormBuilder builder = FormBuilder.createFormBuilder();
        if (!OpenSpecSettings.getInstance(project).isFirstProposalCompleted()) {
            JBLabel banner = new JBLabel("<html><body style='width:" + JBUI.scale(380) + "px'>"
                    + FIRST_PROPOSAL_BANNER + "</body></html>");
            banner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                    JBUI.Borders.empty(0, 0, 8, 0)));
            builder.addComponent(banner);
        }
        builder.addLabeledComponentFillVertically(DESCRIPTION_LABEL, new JBScrollPane(descriptionField));
        JBLabel mode = new JBLabel("<html><body style='width:" + JBUI.scale(380) + "px'>"
                + escape(modeLine) + "</body></html>");
        mode.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
        mode.getAccessibleContext().setAccessibleName("Mode");
        builder.addComponent(mode);
        if (directApi) {
            builder.addLabeledComponent(new JBLabel("Change name (optional):"), nameField);
            if (schema.visible()) builder.addLabeledComponent(new JBLabel("Schema:"), schema.combo());
        }
        return builder.getPanel();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (directApi && descriptionField.getText().isBlank()) {
            return new ValidationInfo("Describe what you want to build", descriptionField);
        }
        return null;
    }

    public String getDescription() {
        return descriptionField.getText().trim();
    }

    /** The name typed, or one derived from the description. Direct API only. */
    public String getChangeName() {
        String typed = nameField.getText().trim();
        return typed.isEmpty()
                ? com.johnnyblabs.openspec.toolwindow.WorkflowActionPanel.deriveKebabName(getDescription()) : typed;
    }

    /** The chosen schema, or null when the selector isn't shown. */
    public @Nullable String getSelectedSchema() {
        return schema.selected();
    }

    /** The mode line shown under the description. */
    public String getModeLine() {
        return modeLine;
    }

    /** Whether the name field is shown. */
    public boolean hasNameField() {
        return directApi;
    }

    /** Whether the schema selector is shown. */
    public boolean isSchemaComboVisible() {
        return schema.visible();
    }

    /** Fills the form as a user would, for tests that can't show the dialog. */
    public void fillForTest(String description, String name) {
        descriptionField.setText(description);
        nameField.setText(name);
    }

    /** {@link #doValidate()}, for tests. */
    public @Nullable ValidationInfo validateForTest() {
        return doValidate();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return descriptionField;
    }
}

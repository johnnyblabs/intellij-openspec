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

/**
 * Collects a feedback message (required) and an optional longer body for
 * {@code openspec feedback <message> [--body <body>]}. Submission is blocked with
 * inline validation while the message is empty — no CLI call happens for an empty message.
 */
public class FeedbackDialog extends DialogWrapper {

    private final JBTextField messageField;
    private final JBTextArea bodyArea;

    public FeedbackDialog(Project project) {
        super(project, false);
        setTitle("Send OpenSpec Feedback");
        setOKButtonText("Send");

        messageField = new JBTextField();
        messageField.getEmptyText().setText("One-line summary of your feedback");

        bodyArea = new JBTextArea(5, 40);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.getEmptyText().setText("(optional) Details, context, reproduction steps");

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Message:"), messageField, true)
                .addLabeledComponent(new JBLabel("Details:"), new JBScrollPane(bodyArea), true)
                .addComponentToRightColumn(new JBLabel(
                        "Sent to the OpenSpec maintainers via 'openspec feedback'."))
                .getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        String error = validateMessage(getMessage());
        return error == null ? null : new ValidationInfo(error, messageField);
    }

    /**
     * The pure validation rule {@link #doValidate()} delegates to: a feedback message
     * must be non-blank. Returns the error text, or null when submittable.
     */
    static String validateMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Feedback message must not be empty";
        }
        return null;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return messageField;
    }

    public String getMessage() {
        return messageField.getText().trim();
    }

    /** The optional body; empty string when the user left it blank. */
    public String getBody() {
        return bodyArea.getText().trim();
    }
}

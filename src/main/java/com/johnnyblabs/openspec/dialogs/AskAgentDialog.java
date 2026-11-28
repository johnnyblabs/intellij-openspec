package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
 * One line of request for OpenSpec's router skill (CLI 2.2). The text is handed over as typed; the router decides what
 * it means. OK is enabled only once something is typed: upstream defines no empty request.
 */
public class AskAgentDialog extends DialogWrapper {

    /** Upstream's own examples (the 2.2.0 release notes). */
    static final String PLACEHOLDER = "e.g. where does add-auth stand?  ·  verify add-auth, then archive it";

    private final JBTextField requestField;

    public AskAgentDialog(Project project) {
        super(project, false);
        setTitle("Ask the Agent");
        requestField = new JBTextField("", 48);
        requestField.getEmptyText().setText(PLACEHOLDER);
        requestField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                setOKActionEnabled(!getRequest().isEmpty());
            }
        });
        init();
        setOKActionEnabled(false);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Request:", requestField)
                .addComponent(new JLabel("<html><small>OpenSpec's router skill works out what you mean and runs the right "
                        + "OpenSpec skill.</small></html>"))
                .getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return requestField;
    }

    @org.jetbrains.annotations.TestOnly
    public void setRequestForTest(String text) {
        requestField.setText(text);
    }

    @org.jetbrains.annotations.TestOnly
    boolean okEnabledForTest() {
        return isOKActionEnabled();
    }

    /** The request as typed, trimmed; empty when blank. */
    public String getRequest() {
        String text = requestField.getText();
        return text == null ? "" : text.strip();
    }
}

package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Lightweight dialog prompting the user for an optional explore topic.
 */
public class ExploreTopicDialog extends DialogWrapper {

    private final JBTextField topicField;

    public ExploreTopicDialog(Project project) {
        super(project, false);
        setTitle("Explore");

        topicField = new JBTextField(40);
        topicField.getEmptyText().setText("What would you like to explore?");

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Topic:", topicField)
                .getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return topicField;
    }

    /**
     * Returns the entered topic text, or empty string if blank.
     */
    public String getTopic() {
        String text = topicField.getText();
        return text == null ? "" : text.trim();
    }
}

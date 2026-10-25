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
        this(project, "");
    }

    /** A dialog whose field starts with {@code initialTopic}, e.g. a change's name; the user can still edit it. */
    public ExploreTopicDialog(Project project, String initialTopic) {
        super(project, false);
        setTitle("Explore");

        topicField = new JBTextField(initialTopic == null ? "" : initialTopic, 40);
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

    /** Types {@code text} into the field, as a user would before clicking OK. */
    @org.jetbrains.annotations.TestOnly
    public void setTopicForTest(String text) {
        topicField.setText(text);
    }

    /**
     * Returns the entered topic text, or empty string if blank.
     */
    public String getTopic() {
        String text = topicField.getText();
        return text == null ? "" : text.trim();
    }
}

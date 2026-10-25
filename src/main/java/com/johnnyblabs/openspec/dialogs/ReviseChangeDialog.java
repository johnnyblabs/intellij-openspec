package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;

/**
 * Asks what should change in a change before Revise Change hands it to the agent. The note is optional: without one,
 * upstream's update workflow reviews the change's artifacts for coherence.
 */
public class ReviseChangeDialog extends DialogWrapper {

    static final String HINT = "Leave blank to have your agent review the whole change for coherence. "
            + "It shows each revision and asks before writing it.";

    private final JBTextArea noteArea;

    public ReviseChangeDialog(Project project, String changeName) {
        super(project, false);
        setTitle("Revise Change: " + changeName);
        noteArea = new JBTextArea(4, 48);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.getEmptyText().setText("Leave blank to review the whole change for coherence");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBLabel hint = new JBLabel(HINT);
        hint.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        hint.setFontColor(UIUtil.FontColor.BRIGHTER);
        return FormBuilder.createFormBuilder()
                .addLabeledComponentFillVertically("What should change?", new JBScrollPane(noteArea))
                .addComponent(hint)
                .getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return noteArea;
    }

    /** Types {@code text} into the note, as a user would before clicking OK. */
    @TestOnly
    public void setNoteForTest(String text) {
        noteArea.setText(text);
    }

    /** The note on one line, so the handoff stays a single line; empty when the user left it blank. */
    public String getNote() {
        return oneLine(noteArea.getText());
    }

    static String oneLine(@Nullable String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }
}

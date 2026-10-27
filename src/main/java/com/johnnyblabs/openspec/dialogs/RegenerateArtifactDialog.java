package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.List;

/**
 * Asks what should change before an artifact is regenerated. The note is optional and one-off: it is added to this
 * regeneration's prompt and never saved. When completed artifacts depend on this one, the dialog says so, so there is
 * one confirmation rather than two.
 */
public class RegenerateArtifactDialog extends DialogWrapper {

    static final String HINT = "Optional. It's added to this regeneration's prompt only and isn't saved.";

    private final JBTextArea noteArea;
    private final @Nullable String downstreamWarning;

    public RegenerateArtifactDialog(@Nullable Project project, @NotNull String artifactId, @NotNull List<String> downstream) {
        super(project, false);
        setTitle("Regenerate " + artifactId);
        setOKButtonText("Regenerate");
        noteArea = new JBTextArea(4, 48);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.getEmptyText().setText("Leave blank to regenerate as before");
        downstreamWarning = downstreamWarning(artifactId, downstream);
        init();
    }

    /** The warning about completed artifacts that depend on this one, or null when there are none. */
    static @Nullable String downstreamWarning(String artifactId, List<String> downstream) {
        if (downstream.isEmpty()) return null;
        return "Regenerating \"" + artifactId + "\" may make these artifacts inconsistent: " + String.join(", ", downstream);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBLabel hint = new JBLabel(HINT);
        hint.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        hint.setFontColor(UIUtil.FontColor.BRIGHTER);
        FormBuilder form = FormBuilder.createFormBuilder()
                .addLabeledComponentFillVertically("What should change?", new JBScrollPane(noteArea))
                .addComponent(hint);
        if (downstreamWarning != null) {
            JBLabel warning = new JBLabel(downstreamWarning, UIUtil.getWarningIcon(), SwingConstants.LEADING);
            form.addVerticalGap(8).addComponent(warning);
        }
        return form.getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return noteArea;
    }

    /** The warning the dialog shows, or null. */
    public @Nullable String getDownstreamWarning() {
        return downstreamWarning;
    }

    /** Types {@code text} into the note, as a user would before clicking Regenerate. */
    @TestOnly
    public void setNoteForTest(String text) {
        noteArea.setText(text);
    }

    /** What the dialog returns once closed: the note (possibly empty) when confirmed, or null when cancelled. */
    public @Nullable String outcome(boolean confirmed) {
        return confirmed ? getNote() : null;
    }

    /** The note with surrounding whitespace removed, keeping its line breaks; empty when left blank. */
    public String getNote() {
        return noteArea.getText() == null ? "" : noteArea.getText().strip();
    }
}

package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.util.Disposer;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;

import java.util.List;

/**
 * The Regenerate dialog: an optional note that starts empty every time, the downstream warning only when completed
 * artifacts depend on this one, and the note trimmed but with its line breaks kept.
 */
public class RegenerateArtifactDialogTest extends OpenSpecIntegrationTestBase {

    private RegenerateArtifactDialog dialog(List<String> downstream) {
        RegenerateArtifactDialog dialog = new RegenerateArtifactDialog(getProject(), "design", downstream);
        Disposer.register(getTestRootDisposable(), dialog.getDisposable());
        return dialog;
    }

    public void testItIsTitledForTheArtifact() {
        RegenerateArtifactDialog dialog = dialog(List.of());

        assertEquals("Regenerate design", dialog.getTitle());
    }

    public void testTheWarningNamesTheCompletedDownstreamArtifacts() {
        RegenerateArtifactDialog dialog = dialog(List.of("specs", "tasks"));

        assertEquals("Regenerating \"design\" may make these artifacts inconsistent: specs, tasks",
                dialog.getDownstreamWarning());
    }

    public void testNoWarningWithoutDownstreamArtifacts() {
        assertNull(dialog(List.of()).getDownstreamWarning());
    }

    public void testEveryDialogStartsWithAnEmptyNote() {
        RegenerateArtifactDialog first = dialog(List.of());
        first.setNoteForTest("drop the caching section");
        assertEquals("drop the caching section", first.getNote());

        assertEquals("a note is never carried over", "", dialog(List.of()).getNote());
    }

    public void testTheNoteIsTrimmedButKeepsItsLineBreaks() {
        RegenerateArtifactDialog dialog = dialog(List.of());
        dialog.setNoteForTest("  first line\nsecond line \n\n");

        assertEquals("first line\nsecond line", dialog.getNote());
    }

    public void testTheWordingSaysTheNoteIsNotSaved() {
        assertTrue(RegenerateArtifactDialog.HINT.contains("isn't saved"));
    }
}

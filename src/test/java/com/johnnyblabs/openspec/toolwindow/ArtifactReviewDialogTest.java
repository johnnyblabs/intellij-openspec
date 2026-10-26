package com.johnnyblabs.openspec.toolwindow;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.util.Disposer;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService.PlannedWrite;

import java.nio.file.Path;
import java.util.List;

/**
 * The review dialog lists every file a regeneration would write, marks new ones, and compares each with its current
 * version (or an empty side) read-only. Accept and Reject are the caller's: the dialog itself writes nothing.
 */
public class ArtifactReviewDialogTest extends OpenSpecIntegrationTestBase {

    private static final Path CHANGE_DIR = Path.of("/fixture/openspec/changes/add-auth");
    private static final PlannedWrite EXISTING =
            new PlannedWrite(CHANGE_DIR.resolve("specs/auth/spec.md"), "# Old auth\n", "# New auth\n");
    private static final PlannedWrite NEW =
            new PlannedWrite(CHANGE_DIR.resolve("specs/billing/spec.md"), null, "# Billing\n");

    public void testItListsEveryFileAndMarksNewOnes() {
        ArtifactReviewDialog dialog = new ArtifactReviewDialog(getProject(), "specs", CHANGE_DIR.toString(),
                List.of(EXISTING, NEW));
        Disposer.register(getTestRootDisposable(), dialog.getDisposable());

        assertEquals(List.of("specs/auth/spec.md", "specs/billing/spec.md (new)"), dialog.fileLabelsForTest());
        assertEquals("Review Regenerated specs", dialog.getTitle());
    }

    public void testAnExistingFileIsComparedWithItsCurrentTextReadOnly() {
        SimpleDiffRequest request = ArtifactReviewDialog.request(getProject(), EXISTING);

        assertEquals(Boolean.TRUE, request.getUserData(DiffUserDataKeys.FORCE_READ_ONLY));
        assertEquals(List.of(ArtifactReviewDialog.CURRENT_TITLE, ArtifactReviewDialog.GENERATED_TITLE),
                request.getContentTitles());
        assertEquals("# Old auth\n", ((DocumentContent) request.getContents().get(0)).getDocument().getText());
        assertEquals("# New auth\n", ((DocumentContent) request.getContents().get(1)).getDocument().getText());
    }

    public void testANewFileIsComparedWithAnEmptySide() {
        SimpleDiffRequest request = ArtifactReviewDialog.request(getProject(), NEW);

        assertTrue(request.getContents().get(0) instanceof EmptyContent);
        assertEquals(ArtifactReviewDialog.NEW_FILE_TITLE, request.getContentTitles().get(0));
        assertEquals("# Billing\n", ((DocumentContent) request.getContents().get(1)).getDocument().getText());
    }
}

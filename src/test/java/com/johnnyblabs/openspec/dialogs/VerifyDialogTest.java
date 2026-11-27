package com.johnnyblabs.openspec.dialogs;

import com.intellij.ui.JBColor;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Category;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@link VerifyDialog}'s archive-gate contract, tested through its pure decisions
 * ({@link VerifyDialog#okButtonText} + the model's {@link ArchiveReadinessResult#isHardBlocked}) so
 * it stays IDE-free. The load-bearing behavior: an IN_PROGRESS change is <b>archivable-anyway</b>
 * (OK enabled, "Archive anyway"), mirroring upstream's bypassable {@code --yes} task gate; only a
 * hard validation BLOCK disables OK.
 */
class VerifyDialogTest {

    @Test
    void inProgress_okIsArchiveAnyway_andEnabled() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("wip")
                .addError(Category.ARTIFACT_COMPLETENESS, "5 incomplete task(s) out of 8")
                .build();
        assertEquals("Archive anyway", VerifyDialog.okButtonText(result));
        assertFalse(result.isHardBlocked(), "in-progress must leave Archive enabled (bypassable)");
    }

    @Test
    void blocked_okIsArchive_andDisabled() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("bad")
                .addError(Category.VALIDATION, "Missing scenario")
                .build();
        assertEquals("Archive", VerifyDialog.okButtonText(result));
        assertTrue(result.isHardBlocked(), "a validation error blocks archive (OK disabled)");
    }

    @Test
    void ready_okIsArchive_andEnabled() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("done").build();
        assertEquals("Archive", VerifyDialog.okButtonText(result));
        assertFalse(result.isHardBlocked());
    }

    @Test
    void warningOnly_okIsArchive_andEnabled() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("warn")
                .addWarning(Category.SYNC_READINESS, "Target capability has no main spec")
                .build();
        assertEquals("Archive", VerifyDialog.okButtonText(result));
        assertFalse(result.isHardBlocked(), "warnings never block archive");
    }

    // --- The two forms: Verify reports (OK closes), Archive's pre-flight archives (OK archives) ---

    private static ArchiveReadinessResult ready() {
        return ArchiveReadinessResult.builder("done").build();
    }

    private static ArchiveReadinessResult inProgress() {
        return ArchiveReadinessResult.builder("wip").addError(Category.ARTIFACT_COMPLETENESS, "2 incomplete task(s) out of 5")
                .build();
    }

    private static ArchiveReadinessResult blocked() {
        return ArchiveReadinessResult.builder("bad").addError(Category.VALIDATION, "Missing scenario").build();
    }

    /** Upstream's verify never archives: the report's OK reads Close and is enabled in every state. */
    @Test
    void report_okIsClose_andEnabled_inEveryState() {
        for (ArchiveReadinessResult r : java.util.List.of(ready(), inProgress(), blocked())) {
            assertEquals("Close", VerifyDialog.okButtonText(VerifyDialog.Form.REPORT, r), r.getChangeName());
            assertTrue(VerifyDialog.okEnabled(VerifyDialog.Form.REPORT, r), r.getChangeName());
        }
    }

    @Test
    void report_archiveButton_followsTheState_whenArchiveIsOffered() {
        assertEquals("Archive…", VerifyDialog.archiveButtonText(VerifyDialog.Form.REPORT, ready(), true));
        assertEquals("Archive anyway…", VerifyDialog.archiveButtonText(VerifyDialog.Form.REPORT, inProgress(), true));
        assertEquals("Archive…", VerifyDialog.archiveButtonText(VerifyDialog.Form.REPORT, blocked(), true));
        assertTrue(VerifyDialog.archiveEnabled(ready()));
        assertTrue(VerifyDialog.archiveEnabled(inProgress()), "open tasks are bypassable");
        assertFalse(VerifyDialog.archiveEnabled(blocked()), "a validation error blocks archiving");
    }

    @Test
    void report_hasNoArchiveButton_withoutTheArchiveWorkflow() {
        for (ArchiveReadinessResult r : java.util.List.of(ready(), inProgress(), blocked())) {
            assertNull(VerifyDialog.archiveButtonText(VerifyDialog.Form.REPORT, r, false), r.getChangeName());
        }
    }

    /** The pre-flight keeps OK = Archive; it has no separate Archive… button. */
    @Test
    void preflight_okArchives_asBefore() {
        assertEquals("Archive", VerifyDialog.okButtonText(VerifyDialog.Form.ARCHIVE_PREFLIGHT, ready()));
        assertEquals("Archive anyway", VerifyDialog.okButtonText(VerifyDialog.Form.ARCHIVE_PREFLIGHT, inProgress()));
        assertTrue(VerifyDialog.okEnabled(VerifyDialog.Form.ARCHIVE_PREFLIGHT, ready()));
        assertTrue(VerifyDialog.okEnabled(VerifyDialog.Form.ARCHIVE_PREFLIGHT, inProgress()));
        assertFalse(VerifyDialog.okEnabled(VerifyDialog.Form.ARCHIVE_PREFLIGHT, blocked()));
        assertNull(VerifyDialog.archiveButtonText(VerifyDialog.Form.ARCHIVE_PREFLIGHT, ready(), true));
    }

    /** Only the Archive… exit code archives after a report: closing it (OK) or cancelling never does. */
    @Test
    void onlyTheArchiveExitCodeRequestsArchiving() {
        assertNotEquals(com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE, VerifyDialog.ARCHIVE_EXIT_CODE);
        assertNotEquals(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE, VerifyDialog.ARCHIVE_EXIT_CODE);
        assertTrue(VerifyDialog.archiveRequested(VerifyDialog.ARCHIVE_EXIT_CODE));
        assertFalse(VerifyDialog.archiveRequested(com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE));
        assertFalse(VerifyDialog.archiveRequested(com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE));
    }

    /** Archive… follows the profile: offered when it includes archive, never when it doesn't. */
    @Test
    void archiveIsOfferedOnlyWhenTheProfileIncludesArchive() {
        var with = org.mockito.Mockito.mock(com.johnnyblabs.openspec.services.WorkflowProfileService.class);
        org.mockito.Mockito.when(with.isWorkflowEnabled("archive")).thenReturn(true);
        var without = org.mockito.Mockito.mock(com.johnnyblabs.openspec.services.WorkflowProfileService.class);
        org.mockito.Mockito.when(without.isWorkflowEnabled("archive")).thenReturn(false);
        org.mockito.Mockito.when(without.isWorkflowEnabled("verify")).thenReturn(true);
        assertTrue(VerifyDialog.archiveOffered(with));
        assertFalse(VerifyDialog.archiveOffered(without), "a profile with verify but not archive");
    }

    /** With no profile service the archive workflow is offered, as an ungated action is. */
    @Test
    void archiveIsOfferedWithoutAProfileService() {
        assertTrue(VerifyDialog.archiveOffered((com.johnnyblabs.openspec.services.WorkflowProfileService) null));
    }

    // --- Header color per state: IN_PROGRESS must be neutral (never red) ---

    @Test
    void stateColor_inProgress_isNeutralNotRed() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("wip")
                .addError(Category.ARTIFACT_COMPLETENESS, "2 incomplete task(s) out of 5")
                .build();
        assertEquals(JBColor.foreground(), VerifyDialog.stateColor(result));
        assertNotEquals(Color.RED, VerifyDialog.stateColor(result),
                "the in-progress header must NOT be red — a valid-but-unfinished change is not a defect");
    }

    @Test
    void stateColor_blocked_isRed() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("bad")
                .addError(Category.VALIDATION, "Missing scenario")
                .build();
        assertEquals(JBColor.RED, VerifyDialog.stateColor(result));
    }

    @Test
    void stateColor_ready_isNotRed() {
        ArchiveReadinessResult result = ArchiveReadinessResult.builder("done").build();
        assertNotEquals(Color.RED, VerifyDialog.stateColor(result));
    }
}

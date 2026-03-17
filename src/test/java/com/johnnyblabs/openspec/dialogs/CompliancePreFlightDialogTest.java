package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.model.ComplianceResult;
import com.johnnyblabs.openspec.model.ComplianceResult.Category;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the compliance pre-flight gate logic:
 * - ERROR findings block archive (dialog would disable OK button)
 * - WARNING-only findings allow archive
 * - Clean compliance allows archive
 */
class CompliancePreFlightDialogTest {

    @Test
    void errorFindings_blockArchive() {
        ComplianceResult result = ComplianceResult.builder("test-change")
                .addError(Category.VALIDATION, "Missing scenario")
                .build();
        // Archive should be blocked: isCompliant() is false
        assertFalse(result.isCompliant(), "Archive should be blocked when errors exist");
        assertTrue(result.errorCount() > 0);
    }

    @Test
    void warningOnlyFindings_allowArchive() {
        ComplianceResult result = ComplianceResult.builder("test-change")
                .addWarning(Category.SYNC_READINESS, "Target capability has no main spec")
                .build();
        // Archive should be allowed: isCompliant() is true
        assertTrue(result.isCompliant(), "Archive should be allowed with warnings only");
        assertEquals(0, result.errorCount());
        assertEquals(1, result.warningCount());
    }

    @Test
    void cleanCompliance_allowsArchive() {
        ComplianceResult result = ComplianceResult.builder("test-change").build();
        assertTrue(result.isCompliant(), "Archive should be allowed when all checks pass");
        assertEquals(0, result.errorCount());
        assertEquals(0, result.warningCount());
    }

    @Test
    void mixedErrorAndWarning_blocksArchive() {
        ComplianceResult result = ComplianceResult.builder("test-change")
                .addError(Category.VALIDATION, "Missing RFC 2119 keyword")
                .addWarning(Category.ARTIFACT_COMPLETENESS, "design.md is scaffolded")
                .build();
        assertFalse(result.isCompliant(), "Archive should be blocked when any error exists");
    }

    @Test
    void multipleErrors_allReported() {
        ComplianceResult result = ComplianceResult.builder("test-change")
                .addError(Category.VALIDATION, "Error 1")
                .addError(Category.VALIDATION, "Error 2")
                .addError(Category.ARTIFACT_COMPLETENESS, "Error 3")
                .build();
        assertFalse(result.isCompliant());
        assertEquals(3, result.errorCount());
        assertEquals(2, result.getFindings(Category.VALIDATION).size());
        assertEquals(1, result.getFindings(Category.ARTIFACT_COMPLETENESS).size());
    }
}

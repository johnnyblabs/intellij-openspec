package com.johnnyblabs.openspec.model;

import com.johnnyblabs.openspec.model.ComplianceResult.Category;
import com.johnnyblabs.openspec.model.ComplianceResult.Severity;
import com.johnnyblabs.openspec.model.ComplianceResult.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComplianceResultTest {

    @Test
    void allPass_isCompliant() {
        ComplianceResult result = ComplianceResult.builder("test-change").build();
        assertTrue(result.isCompliant());
        assertEquals(Status.COMPLIANT, result.getStatus());
        assertEquals(0, result.errorCount());
        assertEquals(0, result.warningCount());
    }

    @Test
    void withError_isNotCompliant() {
        ComplianceResult result = ComplianceResult.builder("test-change")
                .addError(Category.VALIDATION, "Missing RFC 2119 keyword")
                .build();
        assertFalse(result.isCompliant());
        assertEquals(Status.NOT_COMPLIANT, result.getStatus());
        assertEquals(1, result.errorCount());
    }

    @Test
    void withWarningOnly_isCompliant() {
        ComplianceResult result = ComplianceResult.builder("test-change")
                .addWarning(Category.SYNC_READINESS, "Target capability has no main spec")
                .build();
        assertTrue(result.isCompliant());
        assertEquals(0, result.errorCount());
        assertEquals(1, result.warningCount());
    }

    @Test
    void mixedFindings_errorDominates() {
        ComplianceResult result = ComplianceResult.builder("test-change")
                .addWarning(Category.ARTIFACT_COMPLETENESS, "Missing design.md")
                .addError(Category.VALIDATION, "No scenarios")
                .build();
        assertFalse(result.isCompliant());
        assertEquals(1, result.errorCount());
        assertEquals(1, result.warningCount());
    }

    @Test
    void categoryPasses_withNoFindingsInCategory() {
        ComplianceResult result = ComplianceResult.builder("test-change")
                .addError(Category.VALIDATION, "Bad spec")
                .build();
        assertTrue(result.categoryPasses(Category.ARTIFACT_COMPLETENESS));
        assertTrue(result.categoryPasses(Category.SYNC_READINESS));
        assertFalse(result.categoryPasses(Category.VALIDATION));
    }

    @Test
    void getFindings_filtersByCategory() {
        ComplianceResult result = ComplianceResult.builder("test-change")
                .addError(Category.VALIDATION, "Error A")
                .addWarning(Category.ARTIFACT_COMPLETENESS, "Warning B")
                .addError(Category.VALIDATION, "Error C")
                .build();
        assertEquals(2, result.getFindings(Category.VALIDATION).size());
        assertEquals(1, result.getFindings(Category.ARTIFACT_COMPLETENESS).size());
        assertEquals(0, result.getFindings(Category.SYNC_READINESS).size());
    }

    @Test
    void changeName_isPreserved() {
        ComplianceResult result = ComplianceResult.builder("my-change").build();
        assertEquals("my-change", result.getChangeName());
    }
}

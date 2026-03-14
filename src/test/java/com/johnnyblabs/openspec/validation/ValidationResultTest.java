package com.johnnyblabs.openspec.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationResultTest {

    private static ValidationIssue error(String msg) {
        return new ValidationIssue(ValidationIssue.Severity.ERROR, "file.md", 1, msg, "rule");
    }

    private static ValidationIssue warning(String msg) {
        return new ValidationIssue(ValidationIssue.Severity.WARNING, "file.md", 1, msg, "rule");
    }

    private static ValidationIssue info(String msg) {
        return new ValidationIssue(ValidationIssue.Severity.INFO, "file.md", 1, msg, "rule");
    }

    @Test
    void errorCount_countsOnlyErrors() {
        var result = new ValidationResult(false,
                List.of(error("e1"), warning("w1"), error("e2"), info("i1")), "test");
        assertEquals(2, result.errorCount());
    }

    @Test
    void warningCount_countsOnlyWarnings() {
        var result = new ValidationResult(false,
                List.of(error("e1"), warning("w1"), warning("w2"), info("i1")), "test");
        assertEquals(2, result.warningCount());
    }

    @Test
    void emptyResult_hasZeroCounts() {
        var result = new ValidationResult(true, List.of(), "test");
        assertEquals(0, result.errorCount());
        assertEquals(0, result.warningCount());
    }

    // --- Merge ---

    @Test
    void merge_combinesIssues() {
        var builtIn = new ValidationResult(true,
                List.of(warning("w1")), "built-in");
        var cli = new ValidationResult(true,
                List.of(warning("w2")), "cli");

        var merged = ValidationResult.merge(builtIn, cli);
        assertEquals(2, merged.issues().size());
        assertEquals("merged", merged.source());
    }

    @Test
    void merge_failsIfEitherFails() {
        var pass = new ValidationResult(true, List.of(), "built-in");
        var fail = new ValidationResult(false, List.of(error("e1")), "cli");

        assertFalse(ValidationResult.merge(pass, fail).passed());
        assertFalse(ValidationResult.merge(fail, pass).passed());
    }

    @Test
    void merge_passesIfBothPass() {
        var a = new ValidationResult(true, List.of(), "built-in");
        var b = new ValidationResult(true, List.of(), "cli");

        assertTrue(ValidationResult.merge(a, b).passed());
    }

    @Test
    void merge_preservesIssueDetails() {
        var builtIn = new ValidationResult(false,
                List.of(error("config-missing")), "built-in");
        var cli = new ValidationResult(false,
                List.of(error("spec-invalid")), "cli");

        var merged = ValidationResult.merge(builtIn, cli);
        assertEquals(2, merged.errorCount());
        assertTrue(merged.issues().stream().anyMatch(i -> i.message().equals("config-missing")));
        assertTrue(merged.issues().stream().anyMatch(i -> i.message().equals("spec-invalid")));
    }
}

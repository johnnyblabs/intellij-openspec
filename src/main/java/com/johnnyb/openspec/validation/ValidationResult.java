package com.johnnyb.openspec.validation;

import java.util.ArrayList;
import java.util.List;

public record ValidationResult(
        boolean passed,
        List<ValidationIssue> issues,
        String source
) {
    public static ValidationResult merge(ValidationResult builtIn, ValidationResult cli) {
        List<ValidationIssue> allIssues = new ArrayList<>(builtIn.issues());
        allIssues.addAll(cli.issues());
        boolean allPassed = builtIn.passed() && cli.passed();
        return new ValidationResult(allPassed, allIssues, "merged");
    }

    public int errorCount() {
        return (int) issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .count();
    }

    public int warningCount() {
        return (int) issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.WARNING)
                .count();
    }
}

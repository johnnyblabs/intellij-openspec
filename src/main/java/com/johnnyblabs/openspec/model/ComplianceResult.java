package com.johnnyblabs.openspec.model;

import java.util.ArrayList;
import java.util.List;

public class ComplianceResult {

    public enum Status { COMPLIANT, NOT_COMPLIANT }
    public enum Severity { ERROR, WARNING }

    public enum Category {
        ARTIFACT_COMPLETENESS("Artifact Completeness"),
        VALIDATION("Validation"),
        SYNC_READINESS("Spec-Sync Readiness");

        private final String displayName;
        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public record Finding(Category category, Severity severity, String message) {}

    private final String changeName;
    private final List<Finding> findings;

    public ComplianceResult(String changeName, List<Finding> findings) {
        this.changeName = changeName;
        this.findings = List.copyOf(findings);
    }

    public String getChangeName() { return changeName; }

    public List<Finding> getFindings() { return findings; }

    public List<Finding> getFindings(Category category) {
        return findings.stream().filter(f -> f.category() == category).toList();
    }

    public Status getStatus() {
        return findings.stream().anyMatch(f -> f.severity() == Severity.ERROR)
                ? Status.NOT_COMPLIANT : Status.COMPLIANT;
    }

    public boolean isCompliant() { return getStatus() == Status.COMPLIANT; }

    public int errorCount() {
        return (int) findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
    }

    public int warningCount() {
        return (int) findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
    }

    public boolean categoryPasses(Category category) {
        return findings.stream()
                .filter(f -> f.category() == category)
                .noneMatch(f -> f.severity() == Severity.ERROR);
    }

    public static Builder builder(String changeName) {
        return new Builder(changeName);
    }

    public static class Builder {
        private final String changeName;
        private final List<Finding> findings = new ArrayList<>();

        private Builder(String changeName) { this.changeName = changeName; }

        public Builder addError(Category category, String message) {
            findings.add(new Finding(category, Severity.ERROR, message));
            return this;
        }

        public Builder addWarning(Category category, String message) {
            findings.add(new Finding(category, Severity.WARNING, message));
            return this;
        }

        public ComplianceResult build() {
            return new ComplianceResult(changeName, findings);
        }
    }
}

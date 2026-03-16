package com.johnnyblabs.openspec.model;

import java.util.ArrayList;
import java.util.List;

public class VerificationReport {

    private final String changeName;
    private final List<VerificationFinding> findings = new ArrayList<>();

    public VerificationReport(String changeName) {
        this.changeName = changeName;
    }

    public void addFinding(VerificationFinding finding) {
        findings.add(finding);
    }

    public String getChangeName() {
        return changeName;
    }

    public List<VerificationFinding> getFindings() {
        return List.copyOf(findings);
    }

    public List<VerificationFinding> getFindings(VerificationFinding.Dimension dimension) {
        return findings.stream()
                .filter(f -> f.dimension() == dimension)
                .toList();
    }

    public long countBySeverity(VerificationFinding.Severity severity) {
        return findings.stream()
                .filter(f -> f.severity() == severity)
                .count();
    }

    public boolean hasCritical() {
        return countBySeverity(VerificationFinding.Severity.CRITICAL) > 0;
    }

    public boolean isClean() {
        return findings.isEmpty();
    }

    public String getSummary() {
        if (isClean()) return "All clear — ready to archive";
        long critical = countBySeverity(VerificationFinding.Severity.CRITICAL);
        long warnings = countBySeverity(VerificationFinding.Severity.WARNING);
        long suggestions = countBySeverity(VerificationFinding.Severity.SUGGESTION);
        StringBuilder sb = new StringBuilder();
        if (critical > 0) sb.append(critical).append(" critical");
        if (warnings > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(warnings).append(" warning").append(warnings > 1 ? "s" : "");
        }
        if (suggestions > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(suggestions).append(" suggestion").append(suggestions > 1 ? "s" : "");
        }
        if (hasCritical()) sb.append(" — NOT ready to archive");
        return sb.toString();
    }
}

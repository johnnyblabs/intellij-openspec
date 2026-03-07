package com.johnnyb.openspec.validation;

public record ValidationIssue(
        Severity severity,
        String filePath,
        int line,
        String message,
        String rule
) {
    public enum Severity {
        ERROR, WARNING, INFO
    }
}

package com.johnnyblabs.openspec.model;

public record VerificationFinding(
        Severity severity,
        Dimension dimension,
        String description,
        String filePath,
        int lineNumber
) {
    public enum Severity {
        CRITICAL, WARNING, SUGGESTION
    }

    public enum Dimension {
        COMPLETENESS, CORRECTNESS, COHERENCE
    }

    public VerificationFinding(Severity severity, Dimension dimension, String description) {
        this(severity, dimension, description, null, -1);
    }
}

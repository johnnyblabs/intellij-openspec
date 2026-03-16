package com.johnnyblabs.openspec.model;

public record DeltaSpecOperation(
        OperationType type,
        String capabilityName,
        String requirementName,
        String content,
        String fromName,
        String toName
) {
    public enum OperationType {
        ADDED, MODIFIED, REMOVED, RENAMED
    }

    public DeltaSpecOperation {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (capabilityName == null) throw new IllegalArgumentException("capabilityName must not be null");
    }
}

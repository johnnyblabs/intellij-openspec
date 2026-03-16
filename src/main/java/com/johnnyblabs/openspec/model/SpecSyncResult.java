package com.johnnyblabs.openspec.model;

import java.util.List;

public record SpecSyncResult(
        String capabilityName,
        String mainSpecPath,
        String originalContent,
        String projectedContent,
        List<DeltaSpecOperation> appliedOperations,
        List<String> warnings
) {
    public SpecSyncResult {
        if (appliedOperations == null) appliedOperations = List.of();
        if (warnings == null) warnings = List.of();
    }

    public boolean hasChanges() {
        return !java.util.Objects.equals(originalContent, projectedContent);
    }
}

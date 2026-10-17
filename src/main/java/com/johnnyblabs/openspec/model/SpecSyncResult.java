package com.johnnyblabs.openspec.model;

import java.util.List;

/**
 * One capability's sync preview. {@code refusals} holds OpenSpec's reason when its archive would refuse the change;
 * a refused result keeps the original content, and a change with any refusal is not applied at all.
 */
public record SpecSyncResult(
        String capabilityName,
        String mainSpecPath,
        String originalContent,
        String projectedContent,
        List<DeltaSpecOperation> appliedOperations,
        List<String> warnings,
        List<String> refusals
) {
    public SpecSyncResult {
        if (appliedOperations == null) appliedOperations = List.of();
        if (warnings == null) warnings = List.of();
        if (refusals == null) refusals = List.of();
    }

    public SpecSyncResult(String capabilityName, String mainSpecPath, String originalContent, String projectedContent,
                          List<DeltaSpecOperation> appliedOperations, List<String> warnings) {
        this(capabilityName, mainSpecPath, originalContent, projectedContent, appliedOperations, warnings, List.of());
    }

    public boolean hasChanges() {
        return !java.util.Objects.equals(originalContent, projectedContent);
    }

    public boolean refused() {
        return !refusals.isEmpty();
    }
}

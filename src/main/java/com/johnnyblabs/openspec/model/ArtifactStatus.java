package com.johnnyblabs.openspec.model;

public enum ArtifactStatus {
    DONE, READY, BLOCKED, GENERATING, ERROR, UNKNOWN;

    public static ArtifactStatus fromString(String status) {
        if (status == null || status.isBlank()) return UNKNOWN;
        try {
            return valueOf(status.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}

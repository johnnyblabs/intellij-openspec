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

    public String toIcon() {
        return switch (this) {
            case DONE -> "\u2713";
            case READY -> "\u25CB";
            case BLOCKED -> "\u2212";
            case GENERATING -> "\u25CF";
            case ERROR -> "\u2717";
            case UNKNOWN -> "?";
        };
    }
}

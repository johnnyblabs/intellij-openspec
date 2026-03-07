package com.johnnyb.openspec.model;

public enum ChangeStatus {
    PROPOSED, APPLIED, ARCHIVED, UNKNOWN;

    public static ChangeStatus fromString(String status) {
        if (status == null || status.isBlank()) return UNKNOWN;
        try {
            return valueOf(status.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public String toLabel() {
        return "[" + name().toLowerCase() + "]";
    }
}

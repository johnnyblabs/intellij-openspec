package com.johnnyb.openspec.ai;

public enum DeliveryMode {
    CLIPBOARD("Copy to Clipboard"),
    EDITOR_TAB("Open in Editor Tab"),
    DIRECT_API("Generate via API");

    private final String displayName;

    DeliveryMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

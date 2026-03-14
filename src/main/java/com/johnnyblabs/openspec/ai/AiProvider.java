package com.johnnyblabs.openspec.ai;

import java.util.List;

public enum AiProvider {
    NONE("None", List.of()),
    CLAUDE("Claude", List.of("claude-sonnet-4-5-20250514", "claude-opus-4-20250514", "claude-haiku-4-5-20251001")),
    OPENAI("OpenAI", List.of("gpt-4o", "gpt-4o-mini", "o1-mini")),
    GEMINI("Gemini", List.of("gemini-2.5-pro", "gemini-2.5-flash"));

    private final String displayName;
    private final List<String> models;

    AiProvider(String displayName, List<String> models) {
        this.displayName = displayName;
        this.models = models;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getModels() {
        return models;
    }

    public String getDefaultModel() {
        return models.isEmpty() ? "" : models.get(0);
    }

    public static AiProvider fromString(String name) {
        if (name == null || name.isBlank()) return NONE;
        // Try display name first, then enum name
        for (AiProvider p : values()) {
            if (p.displayName.equalsIgnoreCase(name.trim())) return p;
        }
        try {
            return valueOf(name.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}

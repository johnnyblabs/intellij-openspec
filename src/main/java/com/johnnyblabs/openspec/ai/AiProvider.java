package com.johnnyblabs.openspec.ai;

import java.util.List;

public enum AiProvider {
    NONE("None", List.of()),
    // Dateless aliases: providers resolve these to the current snapshot server-side, so the
    // default (models.get(0)) is always a live model and the list does not need a date bump each
    // release. Pinning a dated snapshot here previously shipped a fabricated ID that 404'd.
    CLAUDE("Claude", List.of("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5")),
    // Current flagships, all reasoning models, so they need max_completion_tokens (see
    // DirectApiService.openAiTokenParam). gpt-6-luna is the published replacement for gpt-4o and
    // gpt-4o-mini, which shut down 2027-01-14. IDs are from the models and deprecations pages, not a key.
    OPENAI("OpenAI", List.of("gpt-6-luna", "gpt-6-astra", "gpt-6.1-sol")),
    // gemini-3.5-flash is deprecated (2026-10-08): the endpoint still answers but routes to
    // gemini-3.6-flash, so it would misreport which model ran.
    GEMINI("Gemini", List.of("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite"));

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

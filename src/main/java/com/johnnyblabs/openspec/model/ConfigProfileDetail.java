package com.johnnyblabs.openspec.model;

import java.util.Collections;
import java.util.List;

/**
 * Represents the JSON response from {@code openspec config profile --json}.
 * Contains the active profile name, description, and list of active workflows.
 */
public class ConfigProfileDetail {

    private String name;
    private String description;
    private List<String> workflows;

    public ConfigProfileDetail() {
        this.name = "";
        this.description = "";
        this.workflows = Collections.emptyList();
    }

    public ConfigProfileDetail(String name, String description, List<String> workflows) {
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.workflows = workflows != null ? Collections.unmodifiableList(workflows) : Collections.emptyList();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getWorkflows() {
        return workflows != null ? workflows : Collections.emptyList();
    }

    /**
     * Parses a JSON string from {@code openspec config profile --json} into a ConfigProfileDetail.
     * Returns a fallback instance with just the profile name on parse failure.
     */
    public static ConfigProfileDetail fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ConfigProfileDetail();
        }
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            ConfigProfileDetail detail = gson.fromJson(json, ConfigProfileDetail.class);
            if (detail == null) {
                return new ConfigProfileDetail();
            }
            // Ensure non-null fields
            return new ConfigProfileDetail(detail.name, detail.description, detail.workflows);
        } catch (Exception e) {
            return new ConfigProfileDetail();
        }
    }

    /**
     * Creates a fallback instance with only the profile name (no CLI data).
     */
    public static ConfigProfileDetail fallback(String profileName) {
        return new ConfigProfileDetail(profileName != null ? profileName : "", "", Collections.emptyList());
    }
}

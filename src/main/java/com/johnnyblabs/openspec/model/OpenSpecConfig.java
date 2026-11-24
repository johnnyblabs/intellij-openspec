package com.johnnyblabs.openspec.model;

import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenSpecConfig {
    private String schema;
    private String version;
    private Map<String, String> profile;
    private String context;
    private Map<String, String> rules;
    private @Nullable SkillsScope skillsScope;

    /**
     * The team's recorded skills scope ({@code skillsScope:}, CLI 2.1+). Only {@code openspec update} writes it, when a
     * team keeps project skills; the plugin reads it and never writes it, so there is no setter.
     */
    public enum SkillsScope {
        PROJECT, GLOBAL;

        /** {@code project} or {@code global}, as 2.1 accepts them; anything else is no recorded choice. */
        static @Nullable SkillsScope parse(@Nullable Object value) {
            if ("project".equals(value)) return PROJECT;
            if ("global".equals(value)) return GLOBAL;
            return null;
        }
    }

    public OpenSpecConfig() {
    }

    @SuppressWarnings("unchecked")
    public static OpenSpecConfig fromMap(Map<String, Object> map) {
        OpenSpecConfig config = new OpenSpecConfig();
        if (map == null) return config;

        Object schemaVal = map.get("schema");
        if (schemaVal instanceof String s) config.setSchema(s);

        Object versionVal = map.get("version");
        if (versionVal instanceof String s) config.setVersion(s);

        Object contextVal = map.get("context");
        if (contextVal instanceof String s) config.setContext(s);

        // 2.1 warns about any other value and keeps the rest of the config, so this never fails the load.
        config.skillsScope = SkillsScope.parse(map.get("skillsScope"));

        Object profileVal = map.get("profile");
        if (profileVal instanceof Map<?, ?> m) {
            Map<String, String> profile = new LinkedHashMap<>();
            m.forEach((k, v) -> {
                if (k instanceof String key && v instanceof String val) {
                    profile.put(key, val);
                }
            });
            config.setProfile(profile);
        }

        Object rulesVal = map.get("rules");
        if (rulesVal instanceof Map<?, ?> m) {
            Map<String, String> rules = new LinkedHashMap<>();
            m.forEach((k, v) -> {
                if (k instanceof String key && v instanceof String val) {
                    rules.put(key, val);
                }
            });
            config.setRules(rules);
        }

        return config;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, String> getProfile() {
        return profile != null ? profile : Collections.emptyMap();
    }

    public void setProfile(Map<String, String> profile) {
        this.profile = profile;
    }

    public String getContext() {
        return context != null ? context : "";
    }

    public void setContext(String context) {
        this.context = context;
    }

    /** The recorded skills scope, or {@code null} when the config records none (or an invalid one). */
    public @Nullable SkillsScope getSkillsScope() {
        return skillsScope;
    }

    public Map<String, String> getRules() {
        return rules != null ? rules : Collections.emptyMap();
    }

    public void setRules(Map<String, String> rules) {
        this.rules = rules;
    }
}

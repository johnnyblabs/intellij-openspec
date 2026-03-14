package com.johnnyblabs.openspec.model;

import java.util.Collections;
import java.util.Map;

public class OpenSpecConfig {
    private String schema;
    private String version;
    private Map<String, String> profile;
    private String context;
    private Map<String, String> rules;

    public OpenSpecConfig() {
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

    public Map<String, String> getRules() {
        return rules != null ? rules : Collections.emptyMap();
    }

    public void setRules(Map<String, String> rules) {
        this.rules = rules;
    }
}

package com.johnnyb.openspec.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OpenSpecConfig {
    private String schema;
    private String version;
    private Map<String, String> profile;
    private List<String> context;
    private List<String> rules;

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

    public List<String> getContext() {
        return context != null ? context : Collections.emptyList();
    }

    public void setContext(List<String> context) {
        this.context = context;
    }

    public List<String> getRules() {
        return rules != null ? rules : Collections.emptyList();
    }

    public void setRules(List<String> rules) {
        this.rules = rules;
    }
}

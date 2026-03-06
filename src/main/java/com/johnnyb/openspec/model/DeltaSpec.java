package com.johnnyb.openspec.model;

import java.util.ArrayList;
import java.util.List;

public class DeltaSpec {
    private final String domain;
    private final List<String> added;
    private final List<String> modified;
    private final List<String> removed;

    public DeltaSpec(String domain) {
        this.domain = domain;
        this.added = new ArrayList<>();
        this.modified = new ArrayList<>();
        this.removed = new ArrayList<>();
    }

    public String getDomain() {
        return domain;
    }

    public List<String> getAdded() {
        return added;
    }

    public List<String> getModified() {
        return modified;
    }

    public List<String> getRemoved() {
        return removed;
    }
}

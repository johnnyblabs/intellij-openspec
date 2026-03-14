package com.johnnyblabs.openspec.model;

import java.util.ArrayList;
import java.util.List;

public class Change {
    private final String name;
    private final String path;
    private ChangeMetadata metadata;
    private final List<String> artifactFiles;

    public Change(String name, String path) {
        this.name = name;
        this.path = path;
        this.artifactFiles = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public ChangeMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ChangeMetadata metadata) {
        this.metadata = metadata;
    }

    public List<String> getArtifactFiles() {
        return artifactFiles;
    }
}

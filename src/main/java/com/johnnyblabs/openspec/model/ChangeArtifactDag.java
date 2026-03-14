package com.johnnyblabs.openspec.model;

import java.util.List;
import java.util.stream.Collectors;

public class ChangeArtifactDag {
    private String changeName;
    private String schemaName;
    private boolean isComplete;
    private List<String> applyRequires;
    private List<ArtifactInfo> artifacts;

    public ChangeArtifactDag() {
    }

    public String getChangeName() {
        return changeName;
    }

    public void setChangeName(String changeName) {
        this.changeName = changeName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(boolean complete) {
        isComplete = complete;
    }

    public List<String> getApplyRequires() {
        return applyRequires != null ? applyRequires : List.of();
    }

    public void setApplyRequires(List<String> applyRequires) {
        this.applyRequires = applyRequires;
    }

    public List<ArtifactInfo> getArtifacts() {
        return artifacts != null ? artifacts : List.of();
    }

    public void setArtifacts(List<ArtifactInfo> artifacts) {
        this.artifacts = artifacts;
    }

    public List<ArtifactInfo> getReadyArtifacts() {
        return getArtifacts().stream()
                .filter(a -> a.status() == ArtifactStatus.READY)
                .collect(Collectors.toList());
    }
}

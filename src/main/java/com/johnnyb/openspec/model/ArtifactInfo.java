package com.johnnyb.openspec.model;

import java.util.List;

public class ArtifactInfo {
    private String id;
    private String outputPath;
    private ArtifactStatus status;
    private List<String> missingDeps;

    public ArtifactInfo() {
    }

    public ArtifactInfo(String id, String outputPath, ArtifactStatus status, List<String> missingDeps) {
        this.id = id;
        this.outputPath = outputPath;
        this.status = status;
        this.missingDeps = missingDeps;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public ArtifactStatus getStatus() {
        return status != null ? status : ArtifactStatus.UNKNOWN;
    }

    public void setStatus(ArtifactStatus status) {
        this.status = status;
    }

    public List<String> getMissingDeps() {
        return missingDeps != null ? missingDeps : List.of();
    }

    public void setMissingDeps(List<String> missingDeps) {
        this.missingDeps = missingDeps;
    }
}

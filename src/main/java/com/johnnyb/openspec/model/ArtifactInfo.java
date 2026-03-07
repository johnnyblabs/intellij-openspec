package com.johnnyb.openspec.model;

import java.util.List;

public record ArtifactInfo(String id, String outputPath, ArtifactStatus status, List<String> missingDeps) {

    public ArtifactInfo {
        if (status == null) status = ArtifactStatus.UNKNOWN;
        if (missingDeps == null) missingDeps = List.of();
    }
}

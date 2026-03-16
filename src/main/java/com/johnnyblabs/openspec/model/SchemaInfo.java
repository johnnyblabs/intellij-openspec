package com.johnnyblabs.openspec.model;

import java.util.List;

public record SchemaInfo(String name, String description, boolean isBuiltIn, List<String> artifactIds) {

    public SchemaInfo {
        if (name == null) name = "";
        if (description == null) description = "";
        if (artifactIds == null) artifactIds = List.of();
    }
}

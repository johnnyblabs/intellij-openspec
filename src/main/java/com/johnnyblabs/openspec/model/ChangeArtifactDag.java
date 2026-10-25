package com.johnnyblabs.openspec.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChangeArtifactDag {
    private String changeName;
    private String schemaName;
    private boolean isComplete;
    private List<String> applyRequires;
    private List<ArtifactInfo> artifacts;
    private ActionContext actionContext;
    private LinkedHashMap<String, ArtifactPaths> artifactPaths;
    private Root root;

    public ChangeArtifactDag() {
    }

    /**
     * One entry of the {@code artifactPaths} block (CLI 1.4.0+): the artifact's output pattern and the files that
     * match it, as absolute realpaths sorted by the CLI.
     */
    public record ArtifactPaths(String outputPath, List<String> existingOutputPaths) {
        public ArtifactPaths {
            if (existingOutputPaths == null) existingOutputPaths = List.of();
        }

        /** Whether the artifact generates several files (its output path is a glob, like spec-driven's {@code specs}). */
        public boolean isGlob() {
            return outputPath != null && outputPath.contains("*");
        }
    }

    /** The {@code root} block: where the CLI resolved the OpenSpec root from, and the store id when it's a store. */
    public record Root(String path, String source, @SerializedName("store_id") String storeId) {
        /** Whether the CLI answered for a store rather than the project's own {@code openspec/} directory. */
        public boolean isStore() {
            return storeId != null || "store".equals(source) || "declared".equals(source)
                    || "global_default".equals(source);
        }
    }

    /**
     * The {@code actionContext} block from {@code openspec status --json} (1.3+).
     * Describes the active mode (e.g. {@code repo-local}, {@code workspace-planning}),
     * the source of truth, and the roots that may be edited. Null when the CLI is
     * below the floor or did not surface it.
     */
    public static class ActionContext {
        private String mode;
        private String sourceOfTruth;
        private List<String> allowedEditRoots;
        private boolean requiresAffectedAreaSelection;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getSourceOfTruth() {
            return sourceOfTruth;
        }

        public void setSourceOfTruth(String sourceOfTruth) {
            this.sourceOfTruth = sourceOfTruth;
        }

        public List<String> getAllowedEditRoots() {
            return allowedEditRoots != null ? allowedEditRoots : List.of();
        }

        public void setAllowedEditRoots(List<String> allowedEditRoots) {
            this.allowedEditRoots = allowedEditRoots;
        }

        public boolean isRequiresAffectedAreaSelection() {
            return requiresAffectedAreaSelection;
        }

        public void setRequiresAffectedAreaSelection(boolean requiresAffectedAreaSelection) {
            this.requiresAffectedAreaSelection = requiresAffectedAreaSelection;
        }
    }

    public ActionContext getActionContext() {
        return actionContext;
    }

    public void setActionContext(ActionContext actionContext) {
        this.actionContext = actionContext;
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

    /**
     * The {@code artifactPaths} block in the order the CLI emits it, which is the schema's declaration order. Empty on
     * CLIs before 1.4.0, which don't emit it.
     */
    public Map<String, ArtifactPaths> getArtifactPaths() {
        return artifactPaths != null ? Collections.unmodifiableMap(artifactPaths) : Map.of();
    }

    /** The {@code root} block, or null when the CLI didn't emit one. */
    public @Nullable Root getRoot() {
        return root;
    }

    public List<ArtifactInfo> getReadyArtifacts() {
        return getArtifacts().stream()
                .filter(a -> a.status() == ArtifactStatus.READY)
                .collect(Collectors.toList());
    }
}

package com.johnnyblabs.openspec.model;

import java.util.List;
import java.util.stream.Collectors;

public class ChangeArtifactDag {
    private String changeName;
    private String schemaName;
    private boolean isComplete;
    private List<String> applyRequires;
    private List<ArtifactInfo> artifacts;
    private ActionContext actionContext;

    public ChangeArtifactDag() {
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

    public List<ArtifactInfo> getReadyArtifacts() {
        return getArtifacts().stream()
                .filter(a -> a.status() == ArtifactStatus.READY)
                .collect(Collectors.toList());
    }
}

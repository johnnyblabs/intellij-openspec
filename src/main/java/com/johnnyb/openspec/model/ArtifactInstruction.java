package com.johnnyb.openspec.model;

import java.util.List;

public class ArtifactInstruction {
    private String changeName;
    private String artifactId;
    private String changeDir;
    private String outputPath;
    private String instruction;
    private String template;
    private List<Dependency> dependencies;
    private List<String> unlocks;

    public static class Dependency {
        private String id;
        private boolean done;
        private String path;
        private String description;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public ArtifactInstruction() {
    }

    public String getChangeName() {
        return changeName;
    }

    public void setChangeName(String changeName) {
        this.changeName = changeName;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getChangeDir() {
        return changeDir;
    }

    public void setChangeDir(String changeDir) {
        this.changeDir = changeDir;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public List<Dependency> getDependencies() {
        return dependencies != null ? dependencies : List.of();
    }

    public void setDependencies(List<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getUnlocks() {
        return unlocks != null ? unlocks : List.of();
    }

    public void setUnlocks(List<String> unlocks) {
        this.unlocks = unlocks;
    }

    /**
     * Builds the full prompt text combining instruction and template.
     */
    public String buildPrompt() {
        StringBuilder sb = new StringBuilder();
        if (instruction != null && !instruction.isEmpty()) {
            sb.append(instruction);
        }
        if (template != null && !template.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n---\n\n");
            sb.append("Template:\n").append(template);
        }
        if (!getDependencies().isEmpty()) {
            sb.append("\n\n---\n\nDependencies:\n");
            for (Dependency dep : getDependencies()) {
                sb.append("\n### ").append(dep.getId());
                if (dep.getDescription() != null) {
                    sb.append(" — ").append(dep.getDescription());
                }
                sb.append("\n");
                if (dep.getPath() != null) {
                    sb.append("Path: ").append(dep.getPath()).append("\n");
                }
            }
        }
        return sb.toString();
    }
}

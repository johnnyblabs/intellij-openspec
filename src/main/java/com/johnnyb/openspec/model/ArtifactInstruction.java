package com.johnnyb.openspec.model;

import java.util.List;

public record ArtifactInstruction(
        String changeName,
        String artifactId,
        String changeDir,
        String outputPath,
        String instruction,
        String template,
        List<Dependency> dependencies,
        List<String> unlocks
) {

    public record Dependency(String id, boolean done, String path, String description) {
    }

    public ArtifactInstruction {
        if (dependencies == null) dependencies = List.of();
        if (unlocks == null) unlocks = List.of();
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
            if (!sb.isEmpty()) sb.append("\n\n---\n\n");
            sb.append("Template:\n").append(template);
        }
        if (!dependencies.isEmpty()) {
            sb.append("\n\n---\n\nDependencies:\n");
            for (Dependency dep : dependencies) {
                sb.append("\n### ").append(dep.id());
                if (dep.description() != null) {
                    sb.append(" — ").append(dep.description());
                }
                sb.append("\n");
                if (dep.path() != null) {
                    sb.append("Path: ").append(dep.path()).append("\n");
                }
            }
        }
        return sb.toString();
    }
}

package com.johnnyb.openspec.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * Builds the full prompt text combining instruction, template, and
     * the inline content of completed dependency files.
     */
    public String buildPrompt() {
        StringBuilder sb = new StringBuilder();

        // Change context header
        if (changeName != null && !changeName.isEmpty()) {
            sb.append("# Change: ").append(changeName).append("\n\n");
        }

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

                // Inline the content of completed dependency files
                if (dep.done() && dep.path() != null && changeDir != null) {
                    String content = readDependencyContent(changeDir, dep.path());
                    if (content != null) {
                        sb.append("\n```\n").append(content).append("\n```\n");
                    } else {
                        sb.append("Path: ").append(dep.path()).append("\n");
                    }
                } else if (dep.path() != null) {
                    sb.append("Path: ").append(dep.path()).append(" (not yet completed)\n");
                }
            }
        }
        return sb.toString();
    }

    private static String readDependencyContent(String changeDir, String relativePath) {
        try {
            Path filePath = Path.of(changeDir, relativePath);
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                return Files.readString(filePath);
            }
        } catch (IOException ignored) {
            // Fall back to path-only reference
        }
        return null;
    }
}

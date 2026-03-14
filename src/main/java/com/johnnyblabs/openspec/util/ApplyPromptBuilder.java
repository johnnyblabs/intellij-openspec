package com.johnnyblabs.openspec.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles a full-context implementation prompt from a change's
 * design, specs, and tasks for delivery to an AI tool.
 */
public final class ApplyPromptBuilder {

    private ApplyPromptBuilder() {}

    /**
     * Builds the apply prompt for the given change.
     *
     * @param changeName the change name
     * @param changeDir  the change directory path
     * @return assembled prompt text
     */
    public static String build(String changeName, String changeDir) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Change: ").append(changeName).append("\n\n");

        // Design context
        String design = readFile(changeDir, "design.md");
        if (design != null) {
            sb.append("## Design\n\n").append(design).append("\n\n");
        }

        // Specs context
        String specs = readSpecs(changeDir);
        if (specs != null) {
            sb.append("## Specifications\n\n").append(specs).append("\n\n");
        }

        // Tasks
        String tasks = readFile(changeDir, "tasks.md");
        if (tasks != null) {
            sb.append("## Tasks\n\n").append(tasks).append("\n\n");
        }

        sb.append("---\n\n");
        sb.append("Work through the remaining tasks (marked `- [ ]`) in order. ");
        sb.append("Mark each task complete (`- [x]`) in tasks.md as you finish it. ");
        sb.append("Keep changes minimal and focused on each task.");

        return sb.toString();
    }

    /**
     * Appends a save-path hint for CLI-based tools.
     */
    public static String appendSavePathHint(String prompt, String changeDir) {
        return prompt + "\n\nSave task progress to: " + changeDir + "/tasks.md";
    }

    /**
     * Parses tasks.md content and returns [complete, total] counts.
     */
    public static int[] countTasks(String tasksContent) {
        if (tasksContent == null || tasksContent.isEmpty()) {
            return new int[]{0, 0};
        }
        int total = 0;
        int complete = 0;
        for (String line : tasksContent.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- [x]") || trimmed.startsWith("- [X]")) {
                total++;
                complete++;
            } else if (trimmed.startsWith("- [ ]")) {
                total++;
            }
        }
        return new int[]{complete, total};
    }

    private static String readFile(String changeDir, String fileName) {
        try {
            Path path = Path.of(changeDir, fileName);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return Files.readString(path);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String readSpecs(String changeDir) {
        Path specsDir = Path.of(changeDir, "specs");
        if (!Files.isDirectory(specsDir)) return null;

        StringBuilder sb = new StringBuilder();
        try (DirectoryStream<Path> capabilities = Files.newDirectoryStream(specsDir)) {
            for (Path capDir : capabilities) {
                if (!Files.isDirectory(capDir)) continue;
                Path specFile = capDir.resolve("spec.md");
                if (Files.exists(specFile)) {
                    if (!sb.isEmpty()) sb.append("\n\n---\n\n");
                    sb.append("### ").append(capDir.getFileName()).append("\n\n");
                    sb.append(Files.readString(specFile));
                }
            }
        } catch (IOException ignored) {
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}

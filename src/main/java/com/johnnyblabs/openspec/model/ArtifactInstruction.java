package com.johnnyblabs.openspec.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

import org.jetbrains.annotations.Nullable;

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

                // Inline the content of completed dependency files. A glob (the CLI's "specs/**/*.md") names a
                // set of files: inline each one that exists, labeled with its path.
                List<String> globMatches = dep.done() && changeDir != null && isGlobPath(dep.path())
                        ? MultiFileArtifact.matchingFiles(Path.of(changeDir), dep.path()) : List.of();
                if (!globMatches.isEmpty()) {
                    for (String match : globMatches) {
                        String content = readDependencyContent(changeDir, match);
                        if (content != null) {
                            sb.append("\n#### ").append(match).append("\n\n```\n").append(content).append("\n```\n");
                        }
                    }
                } else if (dep.done() && dep.path() != null && changeDir != null) {
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

    /** Heads the one-off note a user gives when regenerating an artifact. */
    public static final String NOTE_HEADING =
            "Note for this regeneration (follow the instructions above; this note only says what to change):";

    /** Heads the artifact's current content in a noted Direct API regeneration. */
    public static final String CURRENT_VERSION_HEADING = "Current version (revise this):";

    /**
     * {@link #buildPrompt()} with a one-off note appended as its own trailing section. A null or blank note returns
     * {@link #buildPrompt()} unchanged, byte for byte.
     */
    public String buildPrompt(@Nullable String note) {
        String trimmed = trimNote(note);
        return trimmed.isEmpty() ? buildPrompt() : buildPrompt() + noteSection(trimmed);
    }

    /**
     * The prompt for a Direct API call: {@link #buildPrompt()}, plus, for an artifact whose output path is a glob, the
     * multi-file output format and the context an API call can't look up (see {@link MultiFileArtifact}). Copied
     * prompts keep using {@link #buildPrompt()}, because there an agent writes its own files.
     */
    public String buildDirectApiPrompt() {
        return buildDirectApiPrompt(null);
    }

    /**
     * {@link #buildDirectApiPrompt()} with a one-off note. The note goes before the multi-file block, which stays last.
     * With a note, a single-file artifact's current content is included so the model revises it; a glob artifact's
     * existing files are already in the multi-file block. A null or blank note returns {@link #buildDirectApiPrompt()}
     * unchanged.
     */
    public String buildDirectApiPrompt(@Nullable String note) {
        String trimmed = trimNote(note);
        StringBuilder prompt = new StringBuilder(buildPrompt());
        if (!trimmed.isEmpty() && !isGlobPath(outputPath) && changeDir != null && outputPath != null) {
            String current = readDependencyContent(changeDir, outputPath);
            if (current != null) {
                prompt.append("\n\n---\n\n").append(CURRENT_VERSION_HEADING).append("\n\n```\n")
                        .append(current).append("\n```\n");
            }
        }
        if (!trimmed.isEmpty()) {
            prompt.append(noteSection(trimmed));
        }
        if (!isGlobPath(outputPath)) {
            return prompt.toString();
        }
        Path dir = null;
        try {
            dir = changeDir != null ? Path.of(changeDir) : null;
        } catch (InvalidPathException ignored) {
            // An unresolvable change directory only loses the context lists, never the format.
        }
        return prompt + MultiFileArtifact.directApiPromptSection(dir, outputPath);
    }

    private static String trimNote(@Nullable String note) {
        return note == null ? "" : note.strip();
    }

    private static String noteSection(String note) {
        return "\n\n---\n\n" + NOTE_HEADING + "\n" + note + "\n";
    }

    private static String readDependencyContent(String changeDir, String relativePath) {
        // A glob-valued dependency path (e.g. the CLI's "specs/**/*.md") is not a single
        // readable file on any OS. Resolving it to a Path throws on Windows (where '*' is an
        // illegal path character) and matches nothing on POSIX, so short-circuit to the
        // path-only reference here — identically on every platform, without relying on the OS
        // to reject the glob. See GitHub #20.
        if (isGlobPath(relativePath)) {
            return null;
        }
        try {
            Path filePath = Path.of(changeDir, relativePath);
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                return Files.readString(filePath);
            }
        } catch (IOException | InvalidPathException ignored) {
            // Fall back to path-only reference — a dependency path that cannot be resolved to a
            // filesystem path (InvalidPathException is a RuntimeException) must never abort
            // prompt assembly.
        }
        return null;
    }

    /** True when the path contains a glob metacharacter, so it names a set of files, not one. */
    private static boolean isGlobPath(String path) {
        return MultiFileArtifact.isGlob(path);
    }
}

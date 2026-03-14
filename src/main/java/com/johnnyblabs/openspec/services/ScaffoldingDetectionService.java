package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class ScaffoldingDetectionService {

    private static final int MIN_CONTENT_THRESHOLD = 20;

    private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s{0,3}#+\\s+.*$", Pattern.MULTILINE);
    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final Set<String> PLACEHOLDER_LINES = Set.of(
            "- [ ] task 1",
            "- [ ] task 2",
            "- [ ] task 3",
            "- [ ] write unit tests",
            "- [ ] integration testing"
    );

    @SuppressWarnings("unused")
    private final Project project;

    public ScaffoldingDetectionService(Project project) {
        this.project = project;
    }

    /**
     * Checks whether the file at the given path contains only scaffolding placeholder content.
     * Returns false if the file does not exist (file absence is handled by CLI status).
     */
    public boolean isScaffolding(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return false;
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return isScaffoldingContent(content);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks whether the given content string is scaffolding.
     * Visible for testing.
     */
    public boolean isScaffoldingContent(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }

        String stripped = content;

        // Remove markdown headings
        stripped = HEADING_PATTERN.matcher(stripped).replaceAll("");

        // Remove HTML comments
        stripped = HTML_COMMENT_PATTERN.matcher(stripped).replaceAll("");

        // Remove known placeholder task lines
        String[] lines = stripped.split("\n");
        StringBuilder remaining = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim().toLowerCase();
            if (!trimmed.isEmpty() && !PLACEHOLDER_LINES.contains(trimmed)) {
                remaining.append(line).append("\n");
            }
        }

        String result = remaining.toString().trim();
        return result.length() < MIN_CONTENT_THRESHOLD;
    }
}

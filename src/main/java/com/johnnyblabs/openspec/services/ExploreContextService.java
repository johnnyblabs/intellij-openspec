package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.OpenSpecConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assembles project context from config, active changes, specs, and detected AI tools
 * into a Markdown-formatted string. Shared by both {@code ExplorePanel} and
 * {@code ExploreContextAction}.
 */
@Service(Service.Level.PROJECT)
public final class ExploreContextService {
    private static final Logger LOG = Logger.getInstance(ExploreContextService.class);
    private static final String[] CHANGE_ARTIFACTS = {"proposal.md", "design.md", "tasks.md"};

    private final Project project;

    public ExploreContextService(Project project) {
        this.project = project;
    }

    /**
     * Assembles the full project context as a Markdown-formatted string.
     *
     * @return Markdown string containing config summary, active changes with proposal
     *         summaries, spec domains, and detected AI tools.
     */
    public String assembleContext() {
        StringBuilder context = new StringBuilder();
        context.append("# OpenSpec Explore Context\n\n");

        appendConfigSummary(context);
        appendDetectedTools(context);
        appendActiveChanges(context);
        appendSpecsDomains(context);

        return context.toString();
    }

    private void appendConfigSummary(StringBuilder context) {
        ConfigService configService = project.getService(ConfigService.class);
        if (configService == null) return;

        OpenSpecConfig config = configService.getConfig();
        if (config != null) {
            context.append("## Project Config\n");
            context.append("- Version: ").append(config.getVersion()).append("\n");
            if (config.getSchema() != null) {
                context.append("- Schema: ").append(config.getSchema()).append("\n");
            }
            if (!config.getContext().isEmpty()) {
                context.append("\n> ").append(config.getContext().replace("\n", "\n> ")).append("\n");
            }
            if (!config.getRules().isEmpty()) {
                context.append("\n**Rules:**\n");
                config.getRules().forEach((name, rule) ->
                        context.append("- **").append(name).append("**: ").append(rule).append("\n"));
            }
            context.append("\n");
        }
    }

    private void appendDetectedTools(StringBuilder context) {
        AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
        if (detection == null) return;

        detection.detect();
        List<String> tools = detection.getDetectedTools();
        context.append("## Detected AI Tools\n");
        if (tools.isEmpty()) {
            context.append("None detected.\n");
        } else {
            for (String tool : tools) {
                AiToolDetectionService.ToolType type = AiToolDetectionService.getToolType(tool);
                context.append("- ").append(tool).append(" [").append(type).append("]\n");
            }
        }
        context.append("\n");
    }

    private void appendActiveChanges(StringBuilder context) {
        ChangeService changeService = project.getService(ChangeService.class);
        if (changeService == null) return;

        List<Change> changes = changeService.getActiveChanges();
        context.append("## Active Changes\n");
        if (changes.isEmpty()) {
            context.append("No active changes.\n");
        } else {
            for (Change change : changes) {
                context.append("\n### ").append(change.getName());
                if (change.getMetadata() != null && change.getMetadata().getSchema() != null) {
                    context.append(" (").append(change.getMetadata().getSchema()).append(")");
                }
                context.append("\n");

                appendChangeArtifacts(change.getName(), context);
            }
        }
        context.append("\n");
    }

    private void appendChangeArtifacts(String changeName, StringBuilder context) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        Path changeDir = Path.of(basePath, "openspec", "changes", changeName);

        // Read standard artifacts
        for (String artifact : CHANGE_ARTIFACTS) {
            Path artifactPath = changeDir.resolve(artifact);
            if (Files.exists(artifactPath)) {
                String label = artifact.replace(".md", "");
                try {
                    String content = Files.readString(artifactPath);
                    context.append("\n**").append(label).append(":**\n\n").append(content.strip()).append("\n");
                } catch (IOException ignored) {
                    // Skip if unreadable
                }
            }
        }

        // Read delta specs
        Path specsDir = changeDir.resolve("specs");
        if (Files.isDirectory(specsDir)) {
            try (var dirs = Files.list(specsDir).sorted()) {
                dirs.filter(Files::isDirectory).forEach(dir -> {
                    Path specFile = dir.resolve("spec.md");
                    if (Files.exists(specFile)) {
                        try {
                            String content = Files.readString(specFile);
                            context.append("\n**delta spec (").append(dir.getFileName()).append("):**\n\n")
                                    .append(content.strip()).append("\n");
                        } catch (IOException ignored) {
                            // Skip if unreadable
                        }
                    }
                });
            } catch (IOException ignored) {
                // Skip if unreadable
            }
        }
    }

    private static final Pattern REQUIREMENT_PATTERN = Pattern.compile(
            "^### Requirement:\\s*(.+)", Pattern.MULTILINE);
    private static final Pattern SCENARIO_PATTERN = Pattern.compile(
            "^#### Scenario:", Pattern.MULTILINE);

    private void appendSpecsDomains(StringBuilder context) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        Path specsDir = Path.of(basePath, "openspec", "specs");
        if (!Files.isDirectory(specsDir)) return;

        context.append("## Specs\n");
        try (var dirs = Files.list(specsDir).sorted()) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path specFile = dir.resolve("spec.md");
                if (!Files.exists(specFile)) return;

                context.append("\n### ").append(dir.getFileName()).append("\n");
                try {
                    String content = Files.readString(specFile);
                    Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(content);
                    while (reqMatcher.find()) {
                        String reqName = reqMatcher.group(1).trim();
                        // Extract description: text between requirement header and first scenario (or next requirement/section)
                        int descStart = reqMatcher.end();
                        Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(content);
                        int descEnd = content.length();
                        if (scenarioMatcher.find(descStart)) {
                            descEnd = scenarioMatcher.start();
                        }
                        // Also stop at next ### or ##
                        Matcher nextHeader = Pattern.compile("^#{2,3} ", Pattern.MULTILINE).matcher(content);
                        if (nextHeader.find(descStart)) {
                            descEnd = Math.min(descEnd, nextHeader.start());
                        }
                        String description = content.substring(descStart, descEnd).strip();
                        context.append("- **").append(reqName).append("**");
                        if (!description.isEmpty()) {
                            context.append(": ").append(description);
                        }
                        context.append("\n");
                    }
                } catch (IOException ignored) {
                    // Skip if unreadable
                }
            });
        } catch (IOException ignored) {
            // Skip if unreadable
        }
        context.append("\n");
    }
}

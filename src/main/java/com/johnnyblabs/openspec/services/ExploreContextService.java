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

/**
 * Assembles project context from config, active changes, specs, and detected AI tools
 * into a Markdown-formatted string. Shared by both {@code ExplorePanel} and
 * {@code ExploreContextAction}.
 */
@Service(Service.Level.PROJECT)
public final class ExploreContextService {
    private static final Logger LOG = Logger.getInstance(ExploreContextService.class);
    static final int PROPOSAL_SUMMARY_MAX_LENGTH = 500;

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
                context.append("- **").append(change.getName()).append("**");
                if (change.getMetadata() != null && change.getMetadata().getSchema() != null) {
                    context.append(" (").append(change.getMetadata().getSchema()).append(")");
                }
                context.append("\n");

                appendProposalSummary(change.getName(), context);
            }
        }
        context.append("\n");
    }

    private void appendProposalSummary(String changeName, StringBuilder context) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        Path proposalPath = Path.of(basePath, "openspec", "changes", changeName, "proposal.md");
        if (Files.exists(proposalPath)) {
            try {
                String content = Files.readString(proposalPath);
                String summary = content.length() > PROPOSAL_SUMMARY_MAX_LENGTH
                        ? content.substring(0, PROPOSAL_SUMMARY_MAX_LENGTH) + "..."
                        : content;
                context.append("  Proposal:\n  ```\n  ").append(summary.replace("\n", "\n  ")).append("\n  ```\n");
            } catch (IOException ignored) {
                // Skip if unreadable
            }
        }
    }

    private void appendSpecsDomains(StringBuilder context) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        Path specsDir = Path.of(basePath, "openspec", "specs");
        if (!Files.isDirectory(specsDir)) return;

        context.append("## Specs\n");
        try (var dirs = Files.list(specsDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path specFile = dir.resolve("spec.md");
                if (Files.exists(specFile)) {
                    context.append("- ").append(dir.getFileName()).append("\n");
                }
            });
        } catch (IOException ignored) {
            // Skip if unreadable
        }
        context.append("\n");
    }
}

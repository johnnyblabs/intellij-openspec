package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.OpenSpecConfig;
import com.johnnyb.openspec.services.AiToolDetectionService;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.services.ConfigService;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Assembles project context and copies it to the clipboard for use with an AI tool in explore mode.
 */
public class ExploreContextAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            StringBuilder context = new StringBuilder();
            context.append("# OpenSpec Explore Context\n\n");

            // Config summary
            ConfigService configService = project.getService(ConfigService.class);
            if (configService != null) {
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

            // Detected AI tools
            AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
            if (detection != null) {
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

            // Active changes
            ChangeService changeService = project.getService(ChangeService.class);
            if (changeService != null) {
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

                        // Include proposal summary if available
                        appendProposalSummary(project, change.getName(), context);
                    }
                }
                context.append("\n");
            }

            // Recent specs summary
            appendSpecsSummary(project, context);

            // Copy to clipboard on EDT
            String contextText = context.toString();
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(contextText), null);
                OpenSpecNotifier.info(project, "Explore Context",
                        "Context copied \u2014 paste into your AI tool to start exploring.");
            });
        });
    }

    private void appendProposalSummary(Project project, String changeName, StringBuilder context) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        Path proposalPath = Path.of(basePath, "openspec", "changes", changeName, "proposal.md");
        if (Files.exists(proposalPath)) {
            try {
                String content = Files.readString(proposalPath);
                // Include first 500 chars as summary
                String summary = content.length() > 500 ? content.substring(0, 500) + "..." : content;
                context.append("  Proposal:\n  ```\n  ").append(summary.replace("\n", "\n  ")).append("\n  ```\n");
            } catch (IOException ignored) {
                // Skip if unreadable
            }
        }
    }

    private void appendSpecsSummary(Project project, StringBuilder context) {
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

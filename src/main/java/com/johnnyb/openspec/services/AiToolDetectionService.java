package com.johnnyb.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.settings.OpenSpecSettings;

import java.io.File;
import java.util.*;

@Service(Service.Level.PROJECT)
public final class AiToolDetectionService {
    private static final Logger LOG = Logger.getInstance(AiToolDetectionService.class);

    public enum ToolType { CLI, IDE_PANEL }

    /**
     * Guidance metadata for tool-specific delivery instructions.
     */
    public record ToolGuidance(
            String chatPanelName,
            String pasteAction,
            String promptPrefix,
            boolean canAutoSave
    ) {}

    private static final Map<String, ToolGuidance> TOOL_GUIDANCE = Map.of(
            "Claude Code", new ToolGuidance("terminal", "Paste into Claude Code", "/opsx:", true),
            "Gemini", new ToolGuidance("terminal", "Paste into Gemini", null, true),
            "GitHub Copilot", new ToolGuidance("Copilot Chat", "Open Copilot Chat and paste the prompt", "/opsx-", false),
            "Cursor", new ToolGuidance("Composer", "Open Composer and paste the prompt", null, false),
            "Windsurf", new ToolGuidance("Cascade", "Open Cascade and paste the prompt", null, false),
            "Cline", new ToolGuidance("Cline chat", "Open Cline chat and paste the prompt", null, false)
    );

    private static final ToolGuidance DEFAULT_GUIDANCE =
            new ToolGuidance("your AI tool", "Paste into your AI tool", null, false);

    private static final Map<String, String> TOOL_DIRS = new LinkedHashMap<>() {{
        put(".claude", "Claude Code");
        put(".github", "GitHub Copilot");
        put(".cursor", "Cursor");
        put(".windsurf", "Windsurf");
        put(".cline", "Cline");
        put(".gemini", "Gemini");
    }};

    private static final Map<String, ToolType> TOOL_TYPES = Map.of(
            "Claude Code", ToolType.CLI,
            "Gemini", ToolType.CLI,
            "GitHub Copilot", ToolType.IDE_PANEL,
            "Cursor", ToolType.IDE_PANEL,
            "Windsurf", ToolType.IDE_PANEL,
            "Cline", ToolType.IDE_PANEL
    );

    private final Project project;
    private volatile List<String> detectedTools = List.of();

    public AiToolDetectionService(Project project) {
        this.project = project;
    }

    /**
     * Scans the project root for AI tool configuration directories.
     */
    public void detect() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            detectedTools = List.of();
            return;
        }

        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> entry : TOOL_DIRS.entrySet()) {
            File dir = new File(basePath, entry.getKey());
            if (dir.isDirectory()) {
                found.add(entry.getValue());
                LOG.info("Detected AI tool: " + entry.getValue() + " (" + entry.getKey() + ")");
            }
        }
        detectedTools = List.copyOf(found);
    }

    /**
     * Returns the list of detected AI tool names.
     */
    public List<String> getDetectedTools() {
        return detectedTools;
    }

    /**
     * Returns a summary string for display.
     */
    public String getSummary() {
        if (detectedTools.isEmpty()) {
            return "No AI tools detected";
        }
        return "AI: " + String.join(", ", detectedTools);
    }

    /**
     * Checks if any AI tools are detected.
     */
    public boolean hasDetectedTools() {
        return !detectedTools.isEmpty();
    }

    /**
     * Returns the first detected tool name for use as a clipboard label.
     */
    public String getPrimaryToolLabel() {
        return detectedTools.isEmpty() ? "AI Tool" : detectedTools.getFirst();
    }

    /**
     * Returns the preferred tool label: user's selection if set, otherwise first detected.
     */
    public String getPreferredToolLabel() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        String preferred = settings.getPreferredTool();
        if (preferred != null && !preferred.isBlank() && detectedTools.contains(preferred)) {
            return preferred;
        }
        return getPrimaryToolLabel();
    }

    /**
     * Returns whether the given tool name is a CLI-based tool (can write files directly).
     */
    public static boolean isCliTool(String toolName) {
        if (toolName == null) return false;
        return TOOL_TYPES.getOrDefault(toolName, ToolType.IDE_PANEL) == ToolType.CLI;
    }

    /**
     * Returns the tool type classification for the given tool name.
     */
    public static ToolType getToolType(String toolName) {
        if (toolName == null) return ToolType.IDE_PANEL;
        return TOOL_TYPES.getOrDefault(toolName, ToolType.IDE_PANEL);
    }

    /**
     * Returns guidance metadata for the given tool, with a generic fallback for unknown tools.
     */
    public static ToolGuidance getToolGuidance(String toolName) {
        if (toolName == null) return DEFAULT_GUIDANCE;
        return TOOL_GUIDANCE.getOrDefault(toolName, DEFAULT_GUIDANCE);
    }
}

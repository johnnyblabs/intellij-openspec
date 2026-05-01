package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import org.jetbrains.annotations.Nullable;

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

    private static final Map<String, ToolGuidance> TOOL_GUIDANCE = Map.ofEntries(
            Map.entry("Claude Code", new ToolGuidance("terminal", "Paste into Claude Code", "/opsx:", true)),
            Map.entry("Gemini", new ToolGuidance("terminal", "Paste into Gemini", null, true)),
            Map.entry("GitHub Copilot", new ToolGuidance("Copilot Chat", "Open Copilot Chat and paste the prompt", "/opsx-", false)),
            Map.entry("Cursor", new ToolGuidance("Composer", "Open Composer and paste the prompt", null, false)),
            Map.entry("Windsurf", new ToolGuidance("Cascade", "Open Cascade and paste the prompt", null, false)),
            Map.entry("Cline", new ToolGuidance("Cline chat", "Open Cline chat and paste the prompt", null, false)),
            Map.entry("Codex", new ToolGuidance("terminal", "Paste into Codex", null, true)),
            Map.entry("OpenCode", new ToolGuidance("terminal", "Paste into OpenCode", null, true)),
            Map.entry("ForgeCode", new ToolGuidance("terminal", "Paste into ForgeCode", null, true)),
            Map.entry("Bob Shell", new ToolGuidance("terminal", "Paste into Bob Shell", null, true)),
            Map.entry("Junie", new ToolGuidance("Junie", "Open Junie and paste the prompt", "/opsx-", false)),
            Map.entry("Lingma", new ToolGuidance("Lingma chat", "Open Lingma chat and paste the prompt", null, false)),
            Map.entry("Kiro", new ToolGuidance("Kiro chat", "Open Kiro chat and paste the prompt", null, false)),
            Map.entry("Roo Code", new ToolGuidance("Roo Code chat", "Open Roo Code chat and paste the prompt", null, false)),
            Map.entry("Continue", new ToolGuidance("Continue chat", "Open Continue chat and paste the prompt", null, false)),
            Map.entry("Amazon Q", new ToolGuidance("Amazon Q chat", "Open Amazon Q chat and paste the prompt", null, false))
    );

    private static final ToolGuidance DEFAULT_GUIDANCE =
            new ToolGuidance("your AI tool", "Paste into your AI tool", null, false);

    // Upstream registry: @fission-ai/openspec/dist/core/config.js — cross-check on CLI bumps.
    private static final Map<String, String> TOOL_DIRS = new LinkedHashMap<>() {{
        put(".claude", "Claude Code");
        put(".github", "GitHub Copilot");
        put(".cursor", "Cursor");
        put(".windsurf", "Windsurf");
        put(".cline", "Cline");
        put(".gemini", "Gemini");
        put(".amazonq", "Amazon Q");
        put(".agent", "Antigravity");
        put(".augment", "Augment");
        put(".codex", "Codex");
        put(".codebuddy", "CodeBuddy");
        put(".continue", "Continue");
        put(".cospec", "Costrict");
        put(".crush", "Crush");
        put(".factory", "Factory");
        put(".iflow", "iFlow");
        put(".kilocode", "Kilocode");
        put(".kiro", "Kiro");
        put(".opencode", "OpenCode");
        put(".pi", "Pi");
        put(".qoder", "Qoder");
        put(".qwen", "Qwen");
        put(".roo", "Roo Code");
        put(".trae", "Trae");
        put(".junie", "Junie");
        put(".lingma", "Lingma");
        put(".forge", "ForgeCode"); // upstream value is "forgecode" but skillsDir is ".forge"
        put(".bob", "Bob Shell");
    }};

    private static final Map<String, ToolType> TOOL_TYPES = Map.ofEntries(
            Map.entry("Claude Code", ToolType.CLI),
            Map.entry("Gemini", ToolType.CLI),
            Map.entry("Codex", ToolType.CLI),
            Map.entry("OpenCode", ToolType.CLI),
            Map.entry("GitHub Copilot", ToolType.IDE_PANEL),
            Map.entry("Cursor", ToolType.IDE_PANEL),
            Map.entry("Windsurf", ToolType.IDE_PANEL),
            Map.entry("Cline", ToolType.IDE_PANEL),
            Map.entry("Amazon Q", ToolType.IDE_PANEL),
            Map.entry("Antigravity", ToolType.IDE_PANEL),
            Map.entry("Augment", ToolType.IDE_PANEL),
            Map.entry("CodeBuddy", ToolType.IDE_PANEL),
            Map.entry("Continue", ToolType.IDE_PANEL),
            Map.entry("Costrict", ToolType.IDE_PANEL),
            Map.entry("Crush", ToolType.IDE_PANEL),
            Map.entry("Factory", ToolType.IDE_PANEL),
            Map.entry("iFlow", ToolType.IDE_PANEL),
            Map.entry("Kilocode", ToolType.IDE_PANEL),
            Map.entry("Kiro", ToolType.IDE_PANEL),
            Map.entry("Pi", ToolType.IDE_PANEL),
            Map.entry("Qoder", ToolType.IDE_PANEL),
            Map.entry("Qwen", ToolType.IDE_PANEL),
            Map.entry("Roo Code", ToolType.IDE_PANEL),
            Map.entry("Trae", ToolType.IDE_PANEL),
            Map.entry("Junie", ToolType.IDE_PANEL),
            Map.entry("Lingma", ToolType.IDE_PANEL),
            Map.entry("ForgeCode", ToolType.CLI),
            Map.entry("Bob Shell", ToolType.CLI)
    );

    private static final Map<String, String> CLI_TOOL_IDS = Map.ofEntries(
            Map.entry("Claude Code", "claude"),
            Map.entry("Gemini", "gemini"),
            Map.entry("GitHub Copilot", "github-copilot"),
            Map.entry("Cursor", "cursor"),
            Map.entry("Windsurf", "windsurf"),
            Map.entry("Cline", "cline"),
            Map.entry("Amazon Q", "amazon-q"),
            Map.entry("Antigravity", "antigravity"),
            Map.entry("Augment", "auggie"),
            Map.entry("Codex", "codex"),
            Map.entry("CodeBuddy", "codebuddy"),
            Map.entry("Continue", "continue"),
            Map.entry("Costrict", "costrict"),
            Map.entry("Crush", "crush"),
            Map.entry("Factory", "factory"),
            Map.entry("iFlow", "iflow"),
            Map.entry("Kilocode", "kilocode"),
            Map.entry("Kiro", "kiro"),
            Map.entry("OpenCode", "opencode"),
            Map.entry("Pi", "pi"),
            Map.entry("Qoder", "qoder"),
            Map.entry("Qwen", "qwen"),
            Map.entry("Roo Code", "roocode"),
            Map.entry("Trae", "trae"),
            Map.entry("Junie", "junie"),
            Map.entry("Lingma", "lingma"),
            Map.entry("ForgeCode", "forgecode"),
            Map.entry("Bob Shell", "bob")
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
     * Returns all known tool display names (detected or not).
     */
    public static List<String> getAllToolNames() {
        return List.copyOf(TOOL_DIRS.values());
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

    public enum ToolStatus { CONFIGURED, DETECTED, AVAILABLE }

    public record ToolInfo(String name, ToolStatus status, ToolType type, @Nullable String cliId) {}

    /**
     * Returns the directory name for a tool (e.g., "Claude Code" → ".claude").
     */
    public static String getToolDirName(String toolName) {
        for (Map.Entry<String, String> entry : TOOL_DIRS.entrySet()) {
            if (entry.getValue().equals(toolName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Returns the tool status: CONFIGURED (has skills), DETECTED (directory only), or AVAILABLE.
     */
    public ToolStatus getToolStatus(String toolName) {
        String basePath = project.getBasePath();
        if (basePath == null) return ToolStatus.AVAILABLE;

        String dirName = getToolDirName(toolName);
        if (dirName == null) return ToolStatus.AVAILABLE;

        File toolDir = new File(basePath, dirName);
        if (!toolDir.isDirectory()) return ToolStatus.AVAILABLE;

        // Check for OpenSpec skills
        File skillsDir = new File(toolDir, "skills");
        if (skillsDir.isDirectory()) {
            File[] skillFolders = skillsDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("openspec-"));
            if (skillFolders != null && skillFolders.length > 0) {
                return ToolStatus.CONFIGURED;
            }
        }

        // Check for OpenSpec commands (varies by tool)
        File commandsDir = new File(toolDir, "commands");
        if (!commandsDir.isDirectory()) commandsDir = new File(toolDir, "prompts");
        if (!commandsDir.isDirectory()) commandsDir = new File(toolDir, "workflows");
        if (commandsDir.isDirectory()) {
            File[] cmdFiles = commandsDir.listFiles(f -> f.getName().contains("opsx") || f.getName().contains("openspec"));
            if (cmdFiles != null && cmdFiles.length > 0) {
                return ToolStatus.CONFIGURED;
            }
        }

        return ToolStatus.DETECTED;
    }

    /**
     * Returns all tools with their current status, grouped by status.
     */
    public List<ToolInfo> getAllToolsWithStatus() {
        List<ToolInfo> configured = new ArrayList<>();
        List<ToolInfo> detected = new ArrayList<>();
        List<ToolInfo> available = new ArrayList<>();

        for (String toolName : TOOL_DIRS.values()) {
            ToolStatus status = getToolStatus(toolName);
            ToolType type = TOOL_TYPES.getOrDefault(toolName, ToolType.IDE_PANEL);
            String cliId = CLI_TOOL_IDS.get(toolName);
            ToolInfo info = new ToolInfo(toolName, status, type, cliId);

            switch (status) {
                case CONFIGURED -> configured.add(info);
                case DETECTED -> detected.add(info);
                case AVAILABLE -> available.add(info);
            }
        }

        List<ToolInfo> result = new ArrayList<>();
        result.addAll(configured);
        result.addAll(detected);
        result.addAll(available);
        return result;
    }

    /**
     * Returns the CLI tool ID for the given display name (e.g., "GitHub Copilot" → "github-copilot").
     */
    public static String getCliToolId(String displayName) {
        if (displayName == null) return null;
        return CLI_TOOL_IDS.get(displayName);
    }

    /**
     * Returns CLI tool IDs for all currently detected tools, suitable for the --tools flag.
     */
    public List<String> getDetectedCliToolIds() {
        List<String> ids = new ArrayList<>();
        for (String tool : detectedTools) {
            String id = CLI_TOOL_IDS.get(tool);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }
}

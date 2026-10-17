package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
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

    private static final ToolGuidance DEFAULT_GUIDANCE =
            new ToolGuidance("your AI tool", "Paste into your AI tool", null, false);

    private final Project project;
    private volatile List<String> detectedTools = List.of();

    public AiToolDetectionService(Project project) {
        this.project = project;
    }

    /**
     * Detects the project's AI tools the way the OpenSpec CLI does (see {@link AiToolDetector}).
     */
    public void detect() {
        detectedTools = scan().stream().map(AiToolRegistry.AiTool::name).toList();
        if (!detectedTools.isEmpty()) LOG.info("Detected AI tools: " + detectedTools);
    }

    private List<AiToolRegistry.AiTool> scan() {
        String basePath = project.getBasePath();
        if (basePath == null) return List.of();
        return AiToolDetector.detect(Path.of(basePath), home());
    }

    private static @Nullable Path homeForTest;

    /** Test seam for where global tools are looked for; {@code null} restores the user's home. */
    static void setHomeForTest(@Nullable Path home) {
        homeForTest = home;
    }

    private static @Nullable Path home() {
        if (homeForTest != null) return homeForTest;
        String home = System.getProperty("user.home");
        return home == null ? null : Path.of(home);
    }

    private @Nullable java.util.function.Supplier<String> cliVersionForTest;

    /** Test seam for the detected CLI version; {@code null} restores the real detection. */
    void setCliVersionForTest(@Nullable java.util.function.Supplier<String> version) {
        cliVersionForTest = version;
    }

    /** The detected CLI's version, or {@code null} when none is detected. */
    private @Nullable String cliVersion() {
        if (cliVersionForTest != null) return cliVersionForTest.get();
        CliDetectionService cli = project.getService(CliDetectionService.class);
        return cli != null ? cli.getDetectedVersion() : null;
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
        return AiToolRegistry.all().stream().map(AiToolRegistry.AiTool::name).toList();
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
        return getToolType(toolName) == ToolType.CLI;
    }

    /**
     * Returns the tool type classification for the given tool name.
     */
    public static ToolType getToolType(String toolName) {
        AiToolRegistry.AiTool tool = AiToolRegistry.byName(toolName);
        return tool != null ? tool.type() : ToolType.IDE_PANEL;
    }

    /**
     * Returns guidance metadata for the given tool, with a generic fallback for unknown tools.
     */
    public static ToolGuidance getToolGuidance(String toolName) {
        ToolGuidance guidance = AiToolRegistry.guidance(AiToolRegistry.byName(toolName));
        return guidance != null ? guidance : DEFAULT_GUIDANCE;
    }

    public enum ToolStatus { CONFIGURED, DETECTED, AVAILABLE }

    public record ToolInfo(String name, ToolStatus status, ToolType type, @Nullable String cliId) {}

    /**
     * The directory OpenSpec writes a tool's files into (e.g. "Claude Code" → ".claude"), or {@code null} for a tool
     * that only installs into the home directory.
     */
    public static String getToolDirName(String toolName) {
        AiToolRegistry.AiTool tool = AiToolRegistry.byName(toolName);
        return tool != null ? tool.skillsDir() : null;
    }

    /**
     * Returns the tool status: CONFIGURED (detected, with OpenSpec skills or commands), DETECTED, or AVAILABLE.
     */
    public ToolStatus getToolStatus(String toolName) {
        AiToolRegistry.AiTool tool = AiToolRegistry.byName(toolName);
        if (tool == null || !scan().contains(tool)) return ToolStatus.AVAILABLE;
        return statusOfDetected(tool);
    }

    private ToolStatus statusOfDetected(AiToolRegistry.AiTool tool) {
        String basePath = project.getBasePath();
        List<File> roots = new ArrayList<>();
        if (basePath != null && tool.skillsDir() != null) roots.add(new File(basePath, tool.skillsDir()));
        if (basePath != null) tool.legacySkillsDirs().forEach(d -> roots.add(new File(basePath, d)));
        // Older directories that only appear as detection paths (.windsurf for Devin Desktop, .kimi for Kimi Code)
        if (basePath != null) {
            for (String p : tool.detectionPaths()) {
                File root = new File(basePath, p.endsWith("/skills") ? p.substring(0, p.length() - "/skills".length()) : p);
                if (root.isDirectory() && !roots.contains(root)) roots.add(root);
            }
        }
        Path home = home();
        if (home != null && tool.globalSkillsDir() != null) roots.add(home.resolve(tool.globalSkillsDir()).toFile());
        for (File root : roots) {
            if (hasOpenSpecFiles(root)) return ToolStatus.CONFIGURED;
        }
        return ToolStatus.DETECTED;
    }

    private static boolean hasOpenSpecFiles(File toolDir) {
        File[] skillFolders = new File(toolDir, "skills").listFiles(f -> f.isDirectory() && f.getName().startsWith("openspec-"));
        if (skillFolders != null && skillFolders.length > 0) return true;
        // Command files, where each tool keeps them
        for (String dir : List.of("commands", "command", "prompts", "workflows")) {
            File[] cmdFiles = new File(toolDir, dir).listFiles(f -> f.getName().contains("opsx") || f.getName().contains("openspec"));
            if (cmdFiles != null && cmdFiles.length > 0) return true;
        }
        return false;
    }

    /**
     * Returns the tools to show, grouped by status. A tool the detected CLI doesn't accept yet is left out unless the
     * project already has it, and then carries no CLI id, so it is never offered for {@code init}.
     */
    public List<ToolInfo> getAllToolsWithStatus() {
        List<AiToolRegistry.AiTool> detected = scan();
        String version = cliVersion();
        List<ToolInfo> configured = new ArrayList<>();
        List<ToolInfo> found = new ArrayList<>();
        List<ToolInfo> available = new ArrayList<>();

        for (AiToolRegistry.AiTool tool : AiToolRegistry.all()) {
            boolean accepted = AiToolRegistry.acceptedBy(tool, version);
            ToolStatus status = detected.contains(tool) ? statusOfDetected(tool) : ToolStatus.AVAILABLE;
            if (!accepted && status == ToolStatus.AVAILABLE) continue;
            ToolInfo info = new ToolInfo(tool.name(), status, tool.type(), accepted ? tool.cliId() : null);
            switch (status) {
                case CONFIGURED -> configured.add(info);
                case DETECTED -> found.add(info);
                case AVAILABLE -> available.add(info);
            }
        }

        List<ToolInfo> result = new ArrayList<>();
        result.addAll(configured);
        result.addAll(found);
        result.addAll(available);
        return result;
    }

    /**
     * Returns the CLI tool ID for the given display name (e.g., "GitHub Copilot" → "github-copilot").
     */
    public static String getCliToolId(String displayName) {
        AiToolRegistry.AiTool tool = AiToolRegistry.byName(displayName);
        return tool != null ? tool.cliId() : null;
    }

    /**
     * Returns CLI tool IDs for the detected tools the detected CLI accepts, suitable for the --tools flag.
     */
    public List<String> getDetectedCliToolIds() {
        String version = cliVersion();
        List<String> ids = new ArrayList<>();
        for (String name : detectedTools) {
            AiToolRegistry.AiTool tool = AiToolRegistry.byName(name);
            if (tool != null && AiToolRegistry.acceptedBy(tool, version) && !ids.contains(tool.cliId())) {
                ids.add(tool.cliId());
            }
        }
        return ids;
    }
}

package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolGuidance;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolType;
import com.johnnyblabs.openspec.util.CliVersion;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The AI tools OpenSpec can set up, mirroring the CLI's registry ({@code AI_TOOLS} in
 * {@code @fission-ai/openspec/dist/core/config.js}) at the target CLI version. The fields that come from upstream
 * are contract-tested against the registry captured from the CLI ({@code fixtures/cli/<version>/ai-tools.json});
 * refresh them with {@code scripts/capture-ai-tools-fixtures.py} on a CLI bump.
 */
public final class AiToolRegistry {

    private AiToolRegistry() {
    }

    /**
     * One tool.
     *
     * @param id               upstream's id ({@code value})
     * @param cliId            the id the plugin passes to {@code --tools}; differs from {@code id} only where an older
     *                         id is accepted by every supported CLI (Devin Desktop is passed as {@code windsurf})
     * @param name             upstream's {@code name}, the label OpenSpec's tool picker shows
     * @param aliases          names earlier plugin versions used, so a saved preference still matches
     * @param skillsDir        the project directory OpenSpec writes into, or {@code null} for a global-only tool
     * @param globalSkillsDir  the home-directory folder a global tool writes into, or {@code null}
     * @param detectionPaths   paths whose presence detects the tool; when empty, {@code skillsDir} is used
     * @param legacySkillsDirs directories the tool used before {@code skillsDir}
     * @param type             plugin-only: whether the tool runs in a terminal and can write files itself
     * @param sinceCli         the first CLI version that accepts {@code cliId}
     */
    public record AiTool(String id, String cliId, String name, List<String> aliases,
                         @Nullable String skillsDir, @Nullable String globalSkillsDir,
                         List<String> detectionPaths, List<String> legacySkillsDirs,
                         ToolType type, String sinceCli) {
    }

    /** Upstream's {@code OPENSPEC_SKILL_NAMES}: the skill folders OpenSpec generates. */
    static final List<String> SKILL_NAMES = List.of(
            "openspec-explore", "openspec-new-change", "openspec-continue-change", "openspec-apply-change",
            "openspec-update-change", "openspec-ff-change", "openspec-sync-specs", "openspec-archive-change",
            "openspec-bulk-archive-change", "openspec-verify-change", "openspec-onboard", "openspec-propose");

    /** Upstream's universal target, the default owner of an unclaimed shared {@code .agents} root. */
    static final String UNIVERSAL_ID = "agents";

    /** In upstream's order, which decides ties when tools share a skills root. */
    private static final List<AiTool> TOOLS = List.of(
            new AiTool("amazon-q", "amazon-q", "Amazon Q Developer", List.of("Amazon Q"),
                    ".amazonq", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("amp", "amp", "Amp", List.of(),
                    ".agents", null, List.of(".amp", ".agents/skills"), List.of(),
                    ToolType.CLI, "1.14.0"),
            new AiTool("antigravity", "antigravity", "Antigravity", List.of(),
                    ".agents", null, List.of(".agent", ".agents/workflows"), List.of(".agent"),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("atomcode", "atomcode", "AtomCode", List.of(),
                    ".atomcode", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.14.0"),
            new AiTool("auggie", "auggie", "Auggie (Augment CLI)", List.of("Augment"),
                    ".augment", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("bob", "bob", "IBM Bob", List.of("Bob Shell"),
                    ".bob", null, List.of(), List.of(),
                    ToolType.CLI, "1.3.0"),
            new AiTool("claude", "claude", "Claude Code", List.of(),
                    ".claude", null, List.of(), List.of(),
                    ToolType.CLI, "1.3.0"),
            new AiTool("cline", "cline", "Cline", List.of(),
                    ".cline", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("command-code", "command-code", "Command Code", List.of(),
                    ".commandcode", null, List.of(), List.of(),
                    ToolType.CLI, "1.9.0"),
            new AiTool("codeartsagent", "codeartsagent", "CodeArts", List.of(),
                    ".codeartsdoer", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.7.0"),
            new AiTool("codex", "codex", "Codex", List.of(),
                    ".agents", null, List.of(".agents/skills", ".codex/skills"), List.of(".codex"),
                    ToolType.CLI, "1.3.0"),
            new AiTool("dsh", "dsh", "DeepSeek Harness", List.of(),
                    ".dsh", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.14.0"),
            new AiTool("devin", "windsurf", "Devin Desktop (formerly Windsurf)", List.of("Windsurf"),
                    ".devin", null, List.of(".devin", ".windsurf"), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("forgecode", "forgecode", "ForgeCode", List.of(),
                    ".forge", null, List.of(), List.of(),
                    ToolType.CLI, "1.3.0"),
            new AiTool("codebuddy", "codebuddy", "CodeBuddy Code (CLI)", List.of("CodeBuddy"),
                    ".codebuddy", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("codestudio", "codestudio", "Code Studio", List.of(),
                    ".codestudio", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.14.0"),
            new AiTool("continue", "continue", "Continue", List.of(),
                    ".continue", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("costrict", "costrict", "CoStrict", List.of("Costrict"),
                    ".cospec", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("crush", "crush", "Crush", List.of(),
                    ".crush", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("cursor", "cursor", "Cursor", List.of(),
                    ".cursor", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("easycode", "easycode", "EasyCode", List.of(),
                    ".easycode", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.14.0"),
            new AiTool("factory", "factory", "Factory Droid", List.of("Factory"),
                    ".factory", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("gemini", "gemini", "Gemini CLI", List.of("Gemini"),
                    ".gemini", null, List.of(), List.of(),
                    ToolType.CLI, "1.3.0"),
            new AiTool("github-copilot", "github-copilot", "GitHub Copilot", List.of(),
                    ".github", null, List.of(".github/copilot-instructions.md", ".github/instructions", ".github/workflows/copilot-setup-steps.yml", ".github/prompts", ".github/agents", ".github/skills", ".github/.mcp.json"), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("gigacode", "gigacode", "GigaCode", List.of(),
                    ".gigacode", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.14.0"),
            new AiTool("grok", "grok", "Grok Build", List.of(),
                    ".grok", null, List.of(), List.of(),
                    ToolType.CLI, "1.14.0"),
            new AiTool("gsd", "gsd", "GSD", List.of(),
                    ".agents", null, List.of(".gsd"), List.of(),
                    ToolType.CLI, "1.14.0"),
            new AiTool("hermes", "hermes", "Hermes Agent", List.of(),
                    ".hermes", null, List.of(".hermes", "HERMES.md", ".hermes.md"), List.of(),
                    ToolType.CLI, "1.7.0"),
            new AiTool("iflow", "iflow", "iFlow", List.of(),
                    ".iflow", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("junie", "junie", "Junie", List.of(),
                    ".junie", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("kilocode", "kilocode", "Kilo Code", List.of("Kilocode"),
                    ".kilocode", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("kimi", "kimi", "Kimi Code", List.of("Kimi CLI"),
                    ".kimi-code", null, List.of(".kimi-code", ".kimi"), List.of(),
                    ToolType.CLI, "1.4.0"),
            new AiTool("kiro", "kiro", "Kiro", List.of(),
                    ".kiro", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("lingma", "lingma", "Lingma", List.of(),
                    ".lingma", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("minimax-code", "minimax-code", "MiniMax Code", List.of(),
                    null, ".minimax", List.of(), List.of(),
                    ToolType.CLI, "1.8.0"),
            new AiTool("vibe", "vibe", "Mistral Vibe", List.of(),
                    ".vibe", null, List.of(), List.of(),
                    ToolType.CLI, "1.4.0"),
            new AiTool("oh-my-pi", "oh-my-pi", "Oh My Pi", List.of(),
                    ".omp", null, List.of(), List.of(),
                    ToolType.CLI, "1.6.0"),
            new AiTool("opencode", "opencode", "OpenCode", List.of(),
                    ".opencode", null, List.of(), List.of(),
                    ToolType.CLI, "1.3.0"),
            new AiTool("pi", "pi", "Pi", List.of(),
                    ".pi", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("codeassistant", "codeassistant", "SourceCraft Code Assistant", List.of(),
                    ".codeassistant", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.12.0"),
            new AiTool("qoder", "qoder", "Qoder", List.of(),
                    ".qoder", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("qwen", "qwen", "Qwen Code", List.of("Qwen"),
                    ".qwen", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("rovodev", "rovodev", "Rovo Dev CLI", List.of(),
                    ".rovodev", null, List.of(".rovodev/skills", ".rovodev"), List.of(),
                    ToolType.CLI, "1.8.0"),
            new AiTool("roocode", "roocode", "Zoo Code", List.of("Roo Code"),
                    ".roo", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("trae", "trae", "Trae", List.of(),
                    ".trae", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0"),
            new AiTool("veai", "veai", "Veai", List.of(),
                    ".veai", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.14.0"),
            new AiTool("warp", "warp", "Warp", List.of(),
                    ".warp", null, List.of(".warp", "WARP.md"), List.of(),
                    ToolType.IDE_PANEL, "1.14.0"),
            new AiTool("zed", "zed", "Zed Agent", List.of(),
                    ".agents", null, List.of(".zed", ".agents/skills"), List.of(),
                    ToolType.IDE_PANEL, "1.10.0"),
            new AiTool("zcode", "zcode", "ZCode", List.of(),
                    ".zcode", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.7.0"),
            new AiTool("agents", "agents", "Other / Universal (shared .agents skills)", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.IDE_PANEL, "1.3.0")
    );

    private static final Map<String, ToolGuidance> GUIDANCE = Map.ofEntries(
            Map.entry("claude", new ToolGuidance("terminal", "Paste into Claude Code", "/opsx:", true)),
            Map.entry("gemini", new ToolGuidance("terminal", "Paste into Gemini CLI", null, true)),
            Map.entry("github-copilot", new ToolGuidance("Copilot Chat", "Open Copilot Chat and paste the prompt", "/opsx-", false)),
            Map.entry("cursor", new ToolGuidance("Composer", "Open Composer and paste the prompt", null, false)),
            Map.entry("devin", new ToolGuidance("Cascade", "Open Cascade and paste the prompt", null, false)),
            Map.entry("cline", new ToolGuidance("Cline chat", "Open Cline chat and paste the prompt", null, false)),
            Map.entry("codex", new ToolGuidance("terminal", "Paste into Codex", null, true)),
            Map.entry("opencode", new ToolGuidance("terminal", "Paste into OpenCode", null, true)),
            Map.entry("forgecode", new ToolGuidance("terminal", "Paste into ForgeCode", null, true)),
            Map.entry("bob", new ToolGuidance("terminal", "Paste into IBM Bob", null, true)),
            Map.entry("junie", new ToolGuidance("Junie", "Open Junie and paste the prompt", "/opsx-", false)),
            Map.entry("lingma", new ToolGuidance("Lingma chat", "Open Lingma chat and paste the prompt", null, false)),
            Map.entry("kiro", new ToolGuidance("Kiro chat", "Open Kiro chat and paste the prompt", null, false)),
            Map.entry("roocode", new ToolGuidance("Zoo Code chat", "Open Zoo Code chat and paste the prompt", null, false)),
            Map.entry("continue", new ToolGuidance("Continue chat", "Open Continue chat and paste the prompt", null, false)),
            Map.entry("amazon-q", new ToolGuidance("Amazon Q chat", "Open Amazon Q chat and paste the prompt", null, false)),
            Map.entry("kimi", new ToolGuidance("terminal", "Paste into Kimi Code", null, true)),
            Map.entry("vibe", new ToolGuidance("terminal", "Paste into Mistral Vibe", null, true)),
            Map.entry("amp", new ToolGuidance("terminal", "Paste into Amp", null, true)),
            Map.entry("command-code", new ToolGuidance("terminal", "Paste into Command Code", null, true)),
            Map.entry("grok", new ToolGuidance("terminal", "Paste into Grok Build", null, true)),
            Map.entry("gsd", new ToolGuidance("terminal", "Paste into GSD", null, true)),
            Map.entry("hermes", new ToolGuidance("terminal", "Paste into Hermes Agent", null, true)),
            Map.entry("minimax-code", new ToolGuidance("terminal", "Paste into MiniMax Code", null, true)),
            Map.entry("oh-my-pi", new ToolGuidance("terminal", "Paste into Oh My Pi", null, true)),
            Map.entry("rovodev", new ToolGuidance("terminal", "Paste into Rovo Dev CLI", null, true))
    );

    /**
     * The explore command file OpenSpec writes for each tool with a command adapter, when {@code delivery} isn't
     * {@code skills}. The layout follows no single rule (roots, {@code prompts} vs {@code commands}, {@code opsx/x}
     * vs {@code opsx-x}, four extensions), so it is listed as captured from {@code openspec init}; the contract test
     * against the captured listings catches a path that moves.
     */
    private static final Map<String, String> EXPLORE_COMMAND_PATHS = Map.ofEntries(
            Map.entry("amazon-q", ".amazonq/prompts/opsx-explore.md"),
            Map.entry("antigravity", ".agents/workflows/opsx-explore.md"),
            Map.entry("atomcode", ".atomcode/commands/opsx-explore.md"),
            Map.entry("auggie", ".augment/commands/opsx-explore.md"),
            Map.entry("bob", ".bob/commands/opsx-explore.md"),
            Map.entry("claude", ".claude/commands/opsx/explore.md"),
            Map.entry("cline", ".clinerules/workflows/opsx-explore.md"),
            Map.entry("command-code", ".commandcode/commands/opsx-explore.md"),
            Map.entry("devin", ".devin/workflows/opsx-explore.md"),
            Map.entry("codebuddy", ".codebuddy/commands/opsx/explore.md"),
            Map.entry("codestudio", ".codestudio/prompts/opsx-explore.prompt.md"),
            Map.entry("continue", ".continue/prompts/opsx-explore.prompt"),
            Map.entry("costrict", ".cospec/openspec/commands/opsx-explore.md"),
            Map.entry("crush", ".crush/commands/opsx/explore.md"),
            Map.entry("cursor", ".cursor/commands/opsx-explore.md"),
            Map.entry("easycode", ".easycode/commands/opsx/explore.toml"),
            Map.entry("factory", ".factory/commands/opsx-explore.md"),
            Map.entry("gemini", ".gemini/commands/opsx/explore.toml"),
            Map.entry("github-copilot", ".github/prompts/opsx-explore.prompt.md"),
            Map.entry("gigacode", ".gigacode/commands/opsx-explore.md"),
            Map.entry("iflow", ".iflow/commands/opsx-explore.md"),
            Map.entry("junie", ".junie/commands/opsx-explore.md"),
            Map.entry("kilocode", ".kilo/command/opsx-explore.md"),
            Map.entry("kiro", ".kiro/prompts/opsx-explore.prompt.md"),
            Map.entry("lingma", ".lingma/commands/opsx/explore.md"),
            Map.entry("oh-my-pi", ".omp/commands/opsx-explore.md"),
            Map.entry("opencode", ".opencode/commands/opsx-explore.md"),
            Map.entry("pi", ".pi/prompts/opsx-explore.md"),
            Map.entry("codeassistant", ".codeassistant/commands/opsx-explore.md"),
            Map.entry("qoder", ".qoder/commands/opsx/explore.md"),
            Map.entry("qwen", ".qwen/commands/opsx-explore.md"),
            Map.entry("roocode", ".roo/commands/opsx-explore.md"),
            Map.entry("trae", ".trae/commands/opsx-explore.md"),
            Map.entry("zcode", ".zcode/commands/opsx/explore.md")
    );

    private static final Map<String, AiTool> BY_ID = new LinkedHashMap<>();
    private static final Map<String, AiTool> BY_NAME = new HashMap<>();

    static {
        for (AiTool tool : TOOLS) {
            BY_ID.put(tool.id(), tool);
            BY_NAME.put(tool.name(), tool);
            for (String alias : tool.aliases()) BY_NAME.put(alias, tool);
        }
    }

    /** Every tool, in upstream's order. */
    public static List<AiTool> all() {
        return TOOLS;
    }

    public static @Nullable AiTool byId(@Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** The tool with this display name or a former one. */
    public static @Nullable AiTool byName(@Nullable String name) {
        return name == null ? null : BY_NAME.get(name);
    }

    /** The current display name for a name or former name; other values are returned unchanged. */
    public static @Nullable String canonicalName(@Nullable String name) {
        AiTool tool = byName(name);
        return tool != null ? tool.name() : name;
    }

    public static @Nullable ToolGuidance guidance(@Nullable AiTool tool) {
        return tool == null ? null : GUIDANCE.get(tool.id());
    }

    /** Whether a CLI of this version accepts the tool's id. With no detected CLI nothing is passed, so all are. */
    public static boolean acceptedBy(AiTool tool, @Nullable String cliVersion) {
        return cliVersion == null || cliVersion.isBlank() || CliVersion.atLeast(cliVersion, tool.sinceCli());
    }

    /**
     * The directory to create when adding a tool without the CLI, chosen so the tool is then detected: a detection
     * directory (preferring one under its skills directory), else its skills directory. On a shared root that is
     * {@code <root>/skills}, which {@link #builtInMarker} claims. {@code null} for a global-only tool.
     */
    public static @Nullable String builtInDir(AiTool tool) {
        if (tool.skillsDir() == null) return null;
        String under = null, any = null;
        for (String p : tool.detectionPaths()) {
            if (p.endsWith("/skills") || !looksLikeDirectory(p)) continue;
            if (any == null) any = p;
            if (under == null && (p.equals(tool.skillsDir()) || p.startsWith(tool.skillsDir() + "/"))) under = p;
        }
        if (under != null) return under;
        if (any != null) return any;
        return sharesRoot(tool) ? tool.skillsDir() + "/skills" : tool.skillsDir();
    }

    /**
     * The ownership marker to write with {@link #builtInDir}, as upstream records which tool owns a shared skills
     * root ({@code <root>/skills/.openspec-target} holding the tool's id), or {@code null} when none is needed.
     */
    public static @Nullable String builtInMarker(AiTool tool) {
        String dir = builtInDir(tool);
        return dir != null && sharesRoot(tool) && dir.equals(tool.skillsDir() + "/skills") ? dir + "/.openspec-target" : null;
    }

    private static boolean sharesRoot(AiTool tool) {
        return TOOLS.stream().filter(t -> Objects.equals(t.skillsDir(), tool.skillsDir())).count() > 1;
    }

    /** The explore skill OpenSpec writes into the project for this tool, or {@code null} for a global-only tool. */
    public static @Nullable String exploreSkillPath(AiTool tool) {
        return tool.skillsDir() == null ? null : tool.skillsDir() + "/skills/openspec-explore/SKILL.md";
    }

    /** The explore command file OpenSpec writes for this tool, or {@code null} when it has no command adapter. */
    public static @Nullable String exploreCommandPath(AiTool tool) {
        return EXPLORE_COMMAND_PATHS.get(tool.id());
    }

    /** A path whose last segment has no extension (a leading dot doesn't count): {@code .amp}, not {@code WARP.md}. */
    private static boolean looksLikeDirectory(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !(last.startsWith(".") ? last.substring(1) : last).contains(".");
    }

    /** The project-root names a tool can be detected from, for watching their creation. */
    public static Set<String> topLevelPaths() {
        Set<String> roots = new LinkedHashSet<>();
        for (AiTool tool : TOOLS) {
            if (tool.skillsDir() != null) roots.add(firstSegment(tool.skillsDir()));
            tool.detectionPaths().forEach(p -> roots.add(firstSegment(p)));
            tool.legacySkillsDirs().forEach(p -> roots.add(firstSegment(p)));
        }
        return roots;
    }

    private static String firstSegment(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }
}

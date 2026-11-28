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
     * @param untilCli         the first CLI version that no longer accepts it (Amazon Q, retired in 2.0), or
     *                         {@code null} when every release from {@code sinceCli} on does
     */
    public record AiTool(String id, String cliId, String name, List<String> aliases,
                         @Nullable String skillsDir, @Nullable String globalSkillsDir,
                         List<String> detectionPaths, List<String> legacySkillsDirs,
                         ToolType type, String sinceCli, @Nullable String untilCli) {

        /** A tool no release has retired. */
        public AiTool(String id, String cliId, String name, List<String> aliases,
                      @Nullable String skillsDir, @Nullable String globalSkillsDir,
                      List<String> detectionPaths, List<String> legacySkillsDirs,
                      ToolType type, String sinceCli) {
            this(id, cliId, name, aliases, skillsDir, globalSkillsDir, detectionPaths, legacySkillsDirs, type, sinceCli,
                    null);
        }
    }

    /**
     * Upstream's {@code OPENSPEC_SKILL_NAMES}: the skill folders OpenSpec generates, in its order. CLI 2.2 appends the
     * router, {@code openspec}, which is a skill but not a workflow.
     */
    static final List<String> SKILL_NAMES = List.of(
            "openspec-explore", "openspec-new-change", "openspec-continue-change", "openspec-apply-change",
            "openspec-update-change", "openspec-ff-change", "openspec-sync-specs", "openspec-archive-change",
            "openspec-bulk-archive-change", "openspec-verify-change", "openspec-onboard", "openspec-propose",
            "openspec");

    /**
     * The skill names a release recognizes ({@code SKILL_NAMES} in {@code dist/core/shared/tool-detection.js}): 11 before
     * 1.6.0, which adds {@code openspec-update-change}; 12 before 2.2.0, which adds the {@code openspec} router; all 13
     * from 2.2.0 and when no release is known. Contract-tested against every captured {@code <v>/skill-names.json}.
     */
    public static List<String> skillNames(@Nullable String release) {
        if (release == null || release.isBlank() || !CliVersion.below(release, "2.2.0")) return SKILL_NAMES;
        List<String> names = SKILL_NAMES.stream().filter(n -> !n.equals("openspec")).toList();
        return CliVersion.below(release, "1.6.0") ? names.stream().filter(n -> !n.equals("openspec-update-change")).toList() : names;
    }

    /** Upstream's universal target, the default owner of an unclaimed shared {@code .agents} root. */
    static final String UNIVERSAL_ID = "agents";

    /** In upstream's order, which decides ties when tools share a skills root. */
    private static final List<AiTool> TOOLS = List.of(
            new AiTool("amazon-q", "amazon-q", "Amazon Q Developer", List.of("Amazon Q"),
                    ".amazonq", null, List.of(), List.of(),
                    ToolType.IDE_PANEL, "1.3.0", "2.0.0"),
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
                    ToolType.IDE_PANEL, "1.3.0"),
            // Added in CLI 2.1, from the skills package it bundles; types from each vendor's documentation (see
            // the tool-registry-2x design), unclassified where that doesn't settle it.
            new AiTool("aider-desk", "aider-desk", "AiderDesk", List.of(),
                    ".aider-desk", null, List.of(".aider-desk/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("antigravity-cli", "antigravity-cli", "Antigravity CLI", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("astrbot", "astrbot", "AstrBot", List.of(),
                    "data", null, List.of("data/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("autohand-code", "autohand-code", "Autohand Code CLI", List.of(),
                    ".autohand", null, List.of(".autohand/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("openclaw", "openclaw", "OpenClaw", List.of(),
                    ".", null, List.of("skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("codemaker", "codemaker", "Codemaker", List.of(),
                    ".codemaker", null, List.of(".codemaker/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("cortex", "cortex", "Cortex Code", List.of(),
                    ".cortex", null, List.of(".cortex/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("deepagents", "deepagents", "Deep Agents", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("dexto", "dexto", "Dexto", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("eve", "eve", "Eve", List.of(),
                    "agent", null, List.of("agent/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("firebender", "firebender", "Firebender", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("fx", "fx", "fx", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("goose", "goose", "Goose", List.of(),
                    ".goose", null, List.of(".goose/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("inference-sh", "inference-sh", "inference.sh", List.of(),
                    ".inferencesh", null, List.of(".inferencesh/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("jazz", "jazz", "Jazz", List.of(),
                    ".jazz", null, List.of(".jazz/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("kimchi", "kimchi", "Kimchi", List.of(),
                    ".kimchi", null, List.of(".kimchi/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("kode", "kode", "Kode", List.of(),
                    ".kode", null, List.of(".kode/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("loaf", "loaf", "Loaf", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("mcpjam", "mcpjam", "MCPJam", List.of(),
                    ".mcpjam", null, List.of(".mcpjam/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("moxby", "moxby", "Moxby", List.of(),
                    ".moxby", null, List.of(".moxby/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("mux", "mux", "Mux", List.of(),
                    ".mux", null, List.of(".mux/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("openhands", "openhands", "OpenHands", List.of(),
                    ".openhands", null, List.of(".openhands/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("ona", "ona", "Ona", List.of(),
                    ".ona", null, List.of(".ona/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("posit-assistant", "posit-assistant", "Posit Assistant", List.of(),
                    ".posit/assistant", null, List.of(".posit/assistant/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("qoder-cn", "qoder-cn", "Qoder CN", List.of(),
                    ".qoder", null, List.of(".qoder/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("replit", "replit", "Replit", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("reasonix", "reasonix", "Reasonix", List.of(),
                    ".reasonix", null, List.of(".reasonix/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("sarvam-code", "sarvam-code", "Sarvam Code", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("tabnine-cli", "tabnine-cli", "Tabnine CLI", List.of(),
                    ".tabnine/agent", null, List.of(".tabnine/agent/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("terramind", "terramind", "Terramind", List.of(),
                    ".terramind", null, List.of(".terramind/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("tinycloud", "tinycloud", "Tinycloud", List.of(),
                    ".tinycloud", null, List.of(".tinycloud/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("trae-cn", "trae-cn", "Trae CN", List.of(),
                    ".trae", null, List.of(".trae/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("zencoder", "zencoder", "Zencoder", List.of(),
                    ".zencoder", null, List.of(".zencoder/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("zenflow", "zenflow", "Zenflow", List.of(),
                    ".zencoder", null, List.of(".zencoder/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("neovate", "neovate", "Neovate", List.of(),
                    ".neovate", null, List.of(".neovate/skills"), List.of(),
                    ToolType.CLI, "2.1.0"),
            new AiTool("pochi", "pochi", "Pochi", List.of(),
                    ".pochi", null, List.of(".pochi/skills"), List.of(),
                    ToolType.IDE_PANEL, "2.1.0"),
            new AiTool("promptscript", "promptscript", "PromptScript", List.of(),
                    ".agents", null, List.of(".agents/skills"), List.of(),
                    ToolType.UNCLASSIFIED, "2.1.0"),
            new AiTool("adal", "adal", "AdaL", List.of(),
                    ".adal", null, List.of(".adal/skills"), List.of(),
                    ToolType.CLI, "2.1.0")
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

    /**
     * The tool as a 1.x {@code release} shaped it: its skills directory, detection paths and older directories as that
     * release's registry had them ({@code fixtures/cli/<v>/ai-tools.json}). The record itself is 1.17's shape; only five
     * tools changed within 1.x. {@code null} (no release known) returns the record unchanged.
     */
    public static AiTool asOf1x(AiTool tool, @Nullable String release) {
        if (release == null || release.isBlank()) return tool;
        return switch (tool.id()) {
            // 1.7.0: Kimi moved to .kimi-code and Windsurf became Devin Desktop, both keeping the old folder as a path.
            case "kimi" -> CliVersion.below(release, "1.7.0") ? shaped(tool, ".kimi", List.of(), List.of()) : tool;
            case "devin" -> CliVersion.below(release, "1.7.0") ? shaped(tool, ".windsurf", List.of(), List.of()) : tool;
            // 1.8.0: the shared .agents target arrived; Codex moved into it and `agents` gained it as its folder.
            case "codex" -> CliVersion.below(release, "1.8.0") ? shaped(tool, ".codex", List.of(), List.of()) : tool;
            case "agents" -> CliVersion.below(release, "1.8.0") ? shaped(tool, null, List.of(), List.of()) : tool;
            // 1.11.0: Antigravity moved into .agents, keeping .agent as a detection path and older directory.
            case "antigravity" -> CliVersion.below(release, "1.11.0") ? shaped(tool, ".agent", List.of(), List.of()) : tool;
            default -> tool;
        };
    }

    private static AiTool shaped(AiTool tool, @Nullable String skillsDir, List<String> detectionPaths,
                                 List<String> legacySkillsDirs) {
        return new AiTool(tool.id(), tool.cliId(), tool.name(), tool.aliases(), skillsDir, tool.globalSkillsDir(),
                detectionPaths, legacySkillsDirs, tool.type(), tool.sinceCli(), tool.untilCli());
    }

    /**
     * Whether a CLI of this version accepts the tool's id: from its {@code sinceCli}, and before its {@code untilCli}
     * when it has one. With no detected CLI nothing is passed, so all are.
     */
    public static boolean acceptedBy(AiTool tool, @Nullable String cliVersion) {
        if (cliVersion == null || cliVersion.isBlank()) return true;
        return CliVersion.atLeast(cliVersion, tool.sinceCli())
                && (tool.untilCli() == null || CliVersion.below(cliVersion, tool.untilCli()));
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

    /** What adding a tool without the CLI may do in a project. */
    public enum BuiltInAdd {
        /** Create {@link #builtInDir}. */
        CREATE_DIR,
        /** Create {@link #builtInDir} and, when it's absent, the single-id {@link #builtInMarker} list (1.x only). */
        CREATE_DIR_AND_LIST,
        /** Write nothing: only the CLI can record this tool in this project's tool list. */
        NEEDS_CLI
    }

    /**
     * What adding {@code tool} without the CLI may do in {@code project}, decided from disk. Only a tool the list alone
     * records (its {@link #builtInMarker} is non-null: Codex, the universal tool) is restricted. On a project whose files
     * show a 2.x layout ({@link DetectionRules#fromDisk}) the list is the CLI's record of every tool set up, and 2.x counts
     * nothing under {@code .agents} as such a tool's evidence; and where a list names another tool, the plugin, which
     * never overwrites it, can't record this one. Either way the CLI is needed. Pure nio; call off the EDT or for a
     * handful of file checks only.
     */
    public static @Nullable BuiltInAdd builtInAdd(AiTool tool, java.nio.file.Path project) {
        if (builtInDir(tool) == null) return null;
        // A tool CLI 2.1 added is detected only by OpenSpec skills of its own or by the CLI's tool list, and only the
        // CLI writes either.
        if (addedIn21(tool)) return BuiltInAdd.NEEDS_CLI;
        if (builtInMarker(tool) == null) return BuiltInAdd.CREATE_DIR;
        DetectionRules rules = DetectionRules.fromDisk(project);
        if (rules.is2x()) return BuiltInAdd.NEEDS_CLI;
        java.util.List<String> listed = SkillToolList.ids(SkillToolList.read(project));
        if (!listed.isEmpty() && !listed.contains(tool.id())) return BuiltInAdd.NEEDS_CLI;
        // Writing the list would hand the shared folder to this tool: refuse while another tool holds it (a 1.x tree
        // that names its owner by its skills' wording, or Codex skills in its older .codex folder).
        for (AiTool other : AiToolDetector.detect(project, null, rules)) {
            if (!other.equals(tool) && Objects.equals(other.skillsDir(), tool.skillsDir())) return BuiltInAdd.NEEDS_CLI;
        }
        return BuiltInAdd.CREATE_DIR_AND_LIST;
    }

    /**
     * The tools on CLI 2.1 that share {@code tool}'s own project folder (Trae and Trae CN in {@code .trae}), in
     * registry order, or an empty list when it shares it with none. Upstream's {@code agentsByProjectFolder}.
     */
    public static List<AiTool> folderGroup(AiTool tool) {
        if (tool.skillsDir() == null || ".agents".equals(tool.skillsDir()) || !acceptedBy(tool, "2.1.0")) return List.of();
        List<AiTool> group = TOOLS.stream()
                .filter(t -> acceptedBy(t, "2.1.0") && tool.skillsDir().equals(t.skillsDir())).toList();
        return group.size() > 1 ? group : List.of();
    }

    /** Hermes's setup note in the CLI's registry from 1.7.0 until 2.0.0. */
    static final String HERMES_NOTE_1X = "Hermes only loads skills from ~/.hermes/skills by default. Add this project's .hermes/skills directory to skills.external_dirs in ~/.hermes/config.yaml so Hermes picks up the generated OpenSpec skills.";
    /** Hermes's setup note from 2.0.0. */
    static final String HERMES_NOTE_2X = "Hermes loads this project's .hermes/skills only once you trust the repo: run `hermes skills trust` in this project (Hermes v2026.6.5 or later). On older versions, add this project's .hermes/skills directory to skills.external_dirs in ~/.hermes/config.yaml instead.";
    /** The note 2.1 gives every tool its installer treats as universal, except {@code agents} itself. */
    static final String UNIVERSAL_FOLDER_NOTE = "Skills are in the universal skills folder. If your tool needs its own folder, see https://github.com/Fission-AI/OpenSpec/blob/main/docs-lab/reference/supported-tools.md#link-another-tool";

    /**
     * The tools 2.1's installer treats as universal: they read the shared skills folder as their own. The CLI's
     * {@code installerAgent} maps them to {@code universal}; the skills-scope capture's {@code skipped} list records it.
     */
    public static final Set<String> UNIVERSAL_INSTALLER_TOOLS = Set.of("agents", "atomcode", "codeassistant",
            "costrict", "dsh", "easycode", "gigacode", "gsd", "oh-my-pi", "veai");

    /**
     * The setup note the CLI's registry of {@code version} gives {@code tool}, which its {@code init} and
     * {@code update} print as "Setup required for <tool>", or {@code null}.
     */
    public static @Nullable String setupNote(AiTool tool, @Nullable String version) {
        if (version == null || version.isBlank()) return null;
        if ("hermes".equals(tool.id())) {
            if (CliVersion.atLeast(version, "2.0.0")) return HERMES_NOTE_2X;
            return CliVersion.atLeast(version, "1.7.0") ? HERMES_NOTE_1X : null;
        }
        boolean universal = UNIVERSAL_INSTALLER_TOOLS.contains(tool.id()) && !UNIVERSAL_ID.equals(tool.id());
        return universal && CliVersion.atLeast(version, "2.1.0") ? UNIVERSAL_FOLDER_NOTE : null;
    }

    /**
     * The setup note to pass on after {@code args} set {@code tool} up, as the CLI's {@code init} prints it (captured
     * in {@code setup-notes.json}): none for a tool outside the universal skills folder when the run kept the skills
     * on the machine.
     */
    public static @Nullable String setupNoteAfter(AiTool tool, @Nullable String cliVersion,
                                                  com.johnnyblabs.openspec.util.CliArgs args) {
        boolean keptOnMachine = cliVersion != null && CliVersion.atLeast(cliVersion, "2.1.0")
                && !com.johnnyblabs.openspec.util.CliCommands.asksForProjectScope(args);
        if (keptOnMachine && !UNIVERSAL_INSTALLER_TOOLS.contains(tool.id())) return null;
        return setupNote(tool, cliVersion);
    }

    /** Whether CLI 2.1 added the tool, from its bundled installer. */
    static boolean addedIn21(AiTool tool) {
        return CliVersion.atLeast(tool.sinceCli(), "2.1.0");
    }

    /**
     * Whether tools of the 1.x registry share the tool's skills root, which is where the 1.x ownership marker applies.
     * Tools 2.1 added are left out: they never had a marker, and Trae CN sharing Trae's folder doesn't make it shared.
     */
    private static boolean sharesRoot(AiTool tool) {
        return TOOLS.stream().filter(t -> !addedIn21(t) && Objects.equals(t.skillsDir(), tool.skillsDir())).count() > 1;
    }

    /** The explore skill OpenSpec writes into the project for this tool, or {@code null} for a global-only tool. */
    public static @Nullable String exploreSkillPath(AiTool tool) {
        return skillPath(tool, WorkflowInvocation.EXPLORE_SKILL);
    }

    /** The explore command file OpenSpec writes for this tool, or {@code null} when it has no command adapter. */
    public static @Nullable String exploreCommandPath(AiTool tool) {
        return EXPLORE_COMMAND_PATHS.get(tool.id());
    }

    /**
     * The skill {@code skillName} a 1.x release writes into the project for this tool, or {@code null} for a global-only
     * tool. Where a skill is on disk is {@link SkillLocator}'s question; this is where a release writes it.
     */
    public static @Nullable String skillPath(AiTool tool, String skillName) {
        return tool.skillsDir() == null ? null : tool.skillsDir() + "/skills/" + skillName + "/SKILL.md";
    }

    /**
     * Where {@code rules}' release line writes the skill: 2.x writes every project tool's skills to the shared
     * {@code .agents/skills/} (a tool's own folder gets at most a link, {@link SkillLinkDirs}); 1.x as {@link #skillPath}.
     */
    public static @Nullable String skillPath(AiTool tool, String skillName, DetectionRules rules) {
        if (!rules.is2x()) return skillPath(tool, skillName);
        if (rules.skillsDir(tool) == null || rules.globalSkillsDir(tool) != null) return null;
        return ".agents/skills/" + skillName + "/SKILL.md";
    }

    /**
     * The command file OpenSpec writes for {@code workflowId}, or {@code null} when the tool has no command adapter. Every
     * adapter names one workflow's file the way it names explore's ({@code opsx/explore.md} → {@code opsx/update.md},
     * {@code opsx-explore.md} → {@code opsx-update.md}); the workflow-invocations contract test checks it per tool.
     */
    public static @Nullable String commandPath(AiTool tool, String workflowId) {
        String explore = exploreCommandPath(tool);
        if (explore == null) return null;
        int at = explore.lastIndexOf(WorkflowInvocation.EXPLORE);
        return explore.substring(0, at) + workflowId + explore.substring(at + WorkflowInvocation.EXPLORE.length());
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

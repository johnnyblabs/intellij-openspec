package com.johnnyblabs.openspec.services;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The folder OpenSpec 2.x links each tool's skills into, per release line: upstream's {@code resolveSkillLinkDir}
 * ({@code dist/core/shared/skill-paths.js}), which derives from {@code SKILL_LINKED_TOOL_IDS} and the bundled
 * installer's agent table. A tool with no entry reads the shared {@code .agents/skills/} folder itself.
 * <p>
 * The two lines differ: 2.1 links Junie, Qoder, Zoo Code and others that 2.0 didn't, and stops linking EasyCode,
 * GigaCode and Veai. Contract-tested against the {@code effective} block of every 2.x {@code ai-tools.json} and the
 * {@code linkPath} column of every 2.x {@code workflow-invocations.json}, for every tool, the ones 2.1 added included.
 */
public final class SkillLinkDirs {

    /** The 2.x release lines whose link folders differ. */
    public enum Line { V2_0, V2_1 }

    private static final Map<String, String> V2_0 = Map.ofEntries(
            Map.entry("claude", ".claude/skills"),
            Map.entry("codeartsagent", ".codeartsdoer/skills"),
            Map.entry("forgecode", ".forge/skills"),
            Map.entry("codebuddy", ".codebuddy/skills"),
            Map.entry("codestudio", ".codestudio/skills"),
            Map.entry("continue", ".continue/skills"),
            Map.entry("easycode", ".easycode/skills"),
            Map.entry("gigacode", ".gigacode/skills"),
            Map.entry("hermes", ".hermes/skills"),
            Map.entry("iflow", ".iflow/skills"),
            Map.entry("kiro", ".kiro/skills"),
            Map.entry("lingma", ".lingma/skills"),
            Map.entry("rovodev", ".rovodev/skills"),
            Map.entry("veai", ".veai/skills")
    );

    private static final Map<String, String> V2_1 = Map.ofEntries(
            Map.entry("auggie", ".augment/skills"),
            Map.entry("bob", ".bob/skills"),
            Map.entry("claude", ".claude/skills"),
            Map.entry("command-code", ".commandcode/skills"),
            Map.entry("codeartsagent", ".codeartsdoer/skills"),
            Map.entry("devin", ".devin/skills"),
            Map.entry("forgecode", ".forge/skills"),
            Map.entry("codebuddy", ".codebuddy/skills"),
            Map.entry("codestudio", ".codestudio/skills"),
            Map.entry("continue", ".continue/skills"),
            Map.entry("crush", ".crush/skills"),
            Map.entry("grok", ".grok/skills"),
            Map.entry("hermes", ".hermes/skills"),
            Map.entry("iflow", ".iflow/skills"),
            Map.entry("junie", ".junie/skills"),
            Map.entry("kiro", ".kiro/skills"),
            Map.entry("lingma", ".lingma/skills"),
            Map.entry("minimax-code", ".minimax/skills"),
            Map.entry("vibe", ".vibe/skills"),
            Map.entry("qoder", ".qoder/skills"),
            Map.entry("qwen", ".qwen/skills"),
            Map.entry("rovodev", ".rovodev/skills"),
            Map.entry("roocode", ".roo/skills"),
            Map.entry("trae", ".trae/skills"),
            Map.entry("zcode", ".zcode/skills"),
            // Tools added in 2.1
            Map.entry("aider-desk", ".aider-desk/skills"),
            Map.entry("astrbot", "data/skills"),
            Map.entry("autohand-code", ".autohand/skills"),
            Map.entry("openclaw", "skills"),
            Map.entry("codemaker", ".codemaker/skills"),
            Map.entry("cortex", ".cortex/skills"),
            Map.entry("eve", "agent/skills"),
            Map.entry("goose", ".goose/skills"),
            Map.entry("inference-sh", ".inferencesh/skills"),
            Map.entry("jazz", ".jazz/skills"),
            Map.entry("kimchi", ".kimchi/skills"),
            Map.entry("kode", ".kode/skills"),
            Map.entry("mcpjam", ".mcpjam/skills"),
            Map.entry("moxby", ".moxby/skills"),
            Map.entry("mux", ".mux/skills"),
            Map.entry("openhands", ".openhands/skills"),
            Map.entry("ona", ".ona/skills"),
            Map.entry("posit-assistant", ".posit/assistant/skills"),
            Map.entry("qoder-cn", ".qoder/skills"),
            Map.entry("reasonix", ".reasonix/skills"),
            Map.entry("tabnine-cli", ".tabnine/agent/skills"),
            Map.entry("terramind", ".terramind/skills"),
            Map.entry("tinycloud", ".tinycloud/skills"),
            Map.entry("trae-cn", ".trae/skills"),
            Map.entry("zencoder", ".zencoder/skills"),
            Map.entry("zenflow", ".zencoder/skills"),
            Map.entry("neovate", ".neovate/skills"),
            Map.entry("pochi", ".pochi/skills"),
            Map.entry("adal", ".adal/skills")
    );

    private SkillLinkDirs() {
    }

    /** The project-relative folder {@code toolId}'s skills are linked into on {@code line}, or {@code null}. */
    public static @Nullable String linkDir(Line line, String toolId) {
        return (line == Line.V2_0 ? V2_0 : V2_1).get(toolId);
    }

    /** The link OpenSpec makes for {@code skillName} in {@code toolId}'s folder on {@code line}, or {@code null}. */
    public static @Nullable String linkPath(Line line, String toolId, String skillName) {
        String dir = linkDir(line, toolId);
        return dir == null ? null : dir + "/" + skillName;
    }

    /** Every link folder either line uses for {@code toolId}, 2.1's first: a project set up by 2.0 keeps its links. */
    public static List<String> linkDirsAnyLine(String toolId) {
        LinkedHashSet<String> dirs = new LinkedHashSet<>();
        if (V2_1.get(toolId) != null) dirs.add(V2_1.get(toolId));
        if (V2_0.get(toolId) != null) dirs.add(V2_0.get(toolId));
        return List.copyOf(dirs);
    }
}

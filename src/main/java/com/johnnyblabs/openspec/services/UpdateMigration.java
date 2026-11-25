package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.util.UpdatePlanParser.Change;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Plan;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * How an {@code openspec update} plan reads as the move from a 1.x layout to the 2.x one, worked out from the plan's
 * paths alone. The rules are held to what 2.0.0, 2.1.0 and 2.1.1 plan for projects 1.17.0 and earlier releases set up
 * ({@code fixtures/cli/<version>/update-migration/}):
 * <ul>
 *   <li>a <b>shared skill</b> is anything under {@code .agents/skills/}, the one copy every tool reads on 2.x;</li>
 *   <li>a <b>replaced copy</b> is a delete under a tool folder's {@code skills/openspec-*}/ folder, whatever tool value
 *       the CLI gave it (2.0.0 tags Cursor's with {@code cursor}, 2.1 with {@code openspec});</li>
 *   <li>a <b>command file</b> is a delete of one of the paths a 1.x command adapter writes, each workflow named the way
 *       its explore file is named ({@link AiToolRegistry#commandPath});</li>
 *   <li>a <b>link</b> is an add of a tool folder's {@code skills/openspec-*} entry with no file extension, which 2.x
 *       points at the shared skill.</li>
 * </ul>
 * A plan is a <b>migration</b> when it adds or updates the shared-skills marker and replaces a copy or a command file.
 * 1.x already writes the marker for tools rooted at {@code .agents} (Codex, Amp…), so an update of it counts too.
 */
public final class UpdateMigration {

    /** The marker listing the tools a shared skills folder serves. */
    public static final String MARKER = ".agents/skills/.openspec-target";
    static final String SHARED_PREFIX = ".agents/skills/";

    /** What one planned change is, in the migration's terms. */
    public enum Kind { SHARED, REPLACED_COPY, COMMAND_FILE, LINK, OTHER }

    /** A path inside an OpenSpec skill folder: {@code <folder>/skills/openspec-<name>/<file>}. */
    private static final Pattern IN_SKILL_FOLDER = Pattern.compile("^(?:.+/)?skills/openspec-[^/]+/.+$");
    /** A skill folder itself: {@code <folder>/skills/openspec-<name>}, no extension. */
    private static final Pattern SKILL_FOLDER = Pattern.compile("^(?:.+/)?skills/(openspec-[^/.]+)$");

    /**
     * Upstream's {@code LEGACY_TOOL_ROOTS} ({@code dist/core/migration.js}): the folder a tool's files lived in before
     * it moved. 1.x command files under the former folder are still command files.
     */
    static final Map<String, String> FORMER_ROOTS = Map.of("kimi", ".kimi", "devin", ".windsurf", "codex", ".codex",
            "antigravity", ".agent");

    /** Every path a 1.x command adapter writes, for every tool and workflow, in its folder and its former one. */
    private static final Set<String> COMMAND_PATHS = commandPaths();

    private UpdateMigration() {
    }

    private static Set<String> commandPaths() {
        Set<String> paths = new LinkedHashSet<>();
        for (AiToolRegistry.AiTool tool : AiToolRegistry.all()) {
            for (String id : Detector2x.COMMAND_IDS) {
                String path = AiToolRegistry.commandPath(tool, id);
                if (path == null) continue;
                paths.add(path);
                String former = FORMER_ROOTS.get(tool.id());
                int slash = path.indexOf('/');
                if (former != null && slash > 0) paths.add(former + path.substring(slash));
            }
        }
        return Set.copyOf(paths);
    }

    /**
     * Whether {@code plan}, from CLI {@code cliVersion}, moves a 1.x layout to 2.x. A 1.x CLI can plan the same shape
     * (1.17 moves a 1.6-era Antigravity folder to {@code .agents}), so an unknown or 1.x version is never a migration.
     */
    public static boolean isMigration(Plan plan, @org.jetbrains.annotations.Nullable String cliVersion) {
        if (cliVersion == null || !com.johnnyblabs.openspec.util.CliVersion.atLeast(cliVersion, "2.0.0")) return false;
        boolean marker = plan.changes().stream().anyMatch(c -> MARKER.equals(c.path())
                && (c.action().equals("add") || c.action().equals("update")));
        return marker && plan.changes().stream().anyMatch(c -> {
            Kind k = kind(c);
            return k == Kind.REPLACED_COPY || k == Kind.COMMAND_FILE;
        });
    }

    /** What {@code change} is. A change outside the project is always {@link Kind#OTHER}. */
    public static Kind kind(Change change) {
        if (change.outsideProject()) return Kind.OTHER;
        String path = change.path();
        if (path.startsWith(SHARED_PREFIX)) return Kind.SHARED;
        if (change.action().equals("delete")) {
            if (inSkillFolder(path)) return Kind.REPLACED_COPY;
            if (COMMAND_PATHS.contains(path)) return Kind.COMMAND_FILE;
        }
        if (change.action().equals("add") && SKILL_FOLDER.matcher(path).matches()) return Kind.LINK;
        return Kind.OTHER;
    }

    /** Whether {@code path} lies inside an OpenSpec skill folder, the shared one included. */
    public static boolean inSkillFolder(String path) {
        return IN_SKILL_FOLDER.matcher(path).matches();
    }

    /** The shared skill a {@link Kind#LINK} change points at: {@code .agents/skills/<skill>}. */
    public static String linkTarget(Change link) {
        var m = SKILL_FOLDER.matcher(link.path());
        if (!m.matches()) throw new IllegalArgumentException("not a skill-folder link: " + link.path());
        return SHARED_PREFIX + m.group(1);
    }

    /** Whether {@code path} is one of the files a 1.x command adapter writes. Package-private for tests. */
    static boolean isCommandPath(String path) {
        return COMMAND_PATHS.contains(path);
    }
}

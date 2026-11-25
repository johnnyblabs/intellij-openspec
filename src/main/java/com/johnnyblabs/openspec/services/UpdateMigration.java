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

    /** A planned change OpenSpec can't confirm it generated: the update deletes or overwrites it and keeps no copy. */
    public record AtRisk(String path, String action) {
    }

    /**
     * The planned in-project deletes and updates of files in OpenSpec skill folders that OpenSpec can't confirm as its
     * own, read from the project at {@code projectRoot} before the update runs. Reads the disk: call off the UI thread.
     * <ul>
     *   <li>A file reached through a link is never at risk: the update removes the link, not the file.</li>
     *   <li>A file other than {@code SKILL.md} is at risk unless it is operating-system junk.</li>
     *   <li>A {@code SKILL.md} that is a released file ({@link ReleasedSkillFingerprints}) is never at risk.</li>
     *   <li>Any other {@code SKILL.md} in a tool folder is at risk unless it still equals the shared copy, apart from
     *       upstream's per-tool front-matter rewrite ({@code allowed-tools} dropped).</li>
     *   <li>Any other {@code SKILL.md} in the shared folder is at risk only when its {@code generatedBy} names a 1.x
     *       release: 2.1 writes no {@code generatedBy}, 2.0.0 its own version, and the plugin can't reproduce a 2.x
     *       rendering to compare with.</li>
     *   <li>A file that can't be read is at risk.</li>
     * </ul>
     */
    public static java.util.List<AtRisk> atRiskFiles(java.nio.file.Path projectRoot, Plan plan) {
        java.util.List<AtRisk> risky = new java.util.ArrayList<>();
        for (Change c : plan.changes()) {
            if (c.outsideProject() || !(c.action().equals("delete") || c.action().equals("update"))) continue;
            if (!inSkillFolder(c.path())) continue;
            java.nio.file.Path file = projectRoot.resolve(c.path());
            if (throughLink(projectRoot, file)) continue;
            if (atRisk(projectRoot, c.path(), file)) risky.add(new AtRisk(c.path(), c.action()));
        }
        return java.util.List.copyOf(risky);
    }

    private static boolean atRisk(java.nio.file.Path root, String path, java.nio.file.Path file) {
        String name = file.getFileName().toString();
        if (!name.equals("SKILL.md")) return !SkillSpot.isOsJunk(name);
        String content;
        try {
            content = java.nio.file.Files.readString(file);
        } catch (java.io.IOException | RuntimeException e) {
            return true;
        }
        if (ReleasedSkillFingerprints.isReleased(content)) return false;
        if (path.startsWith(SHARED_PREFIX)) {
            // A 1.x shared copy that isn't released was edited; a 2.x one can't be judged (no 2.x rendering to compare).
            String version = ReleasedSkillFingerprints.generatedVersion(content);
            return version != null && !com.johnnyblabs.openspec.util.CliVersion.atLeast(version, "2.0.0");
        }
        String skill = file.getParent().getFileName().toString();
        java.nio.file.Path shared = root.resolve(SHARED_PREFIX + skill + "/SKILL.md");
        try {
            return !java.nio.file.Files.isRegularFile(shared) || !sameSkill(content, java.nio.file.Files.readString(shared));
        } catch (java.io.IOException | RuntimeException e) {
            return true;
        }
    }

    /** Whether {@code file}, or a folder between it and {@code root}, is a link. */
    private static boolean throughLink(java.nio.file.Path root, java.nio.file.Path file) {
        for (java.nio.file.Path p = file; p != null && !p.equals(root); p = p.getParent()) {
            if (java.nio.file.Files.isSymbolicLink(p)) return true;
        }
        return false;
    }

    /**
     * Whether two skill files say the same: the same front matter, read as data, apart from {@code allowed-tools}
     * (which upstream drops for some tools, re-serializing the rest), and the same body after leading blank lines.
     */
    static boolean sameSkill(String a, String b) {
        String[] pa = split(ReleasedSkillFingerprints.normalizeFile(a));
        String[] pb = split(ReleasedSkillFingerprints.normalizeFile(b));
        if (pa == null || pb == null) return false;
        return frontMatter(pa[0]).equals(frontMatter(pb[0])) && pa[1].equals(pb[1]);
    }

    /** {front matter, body without leading blank lines}, or {@code null} without front matter. */
    private static String @org.jetbrains.annotations.Nullable [] split(String text) {
        if (!text.startsWith("---\n")) return null;
        int end = text.indexOf("\n---", 4);
        if (end < 0) return null;
        int bodyStart = text.indexOf('\n', end + 4);
        String body = bodyStart < 0 ? "" : text.substring(bodyStart + 1).replaceFirst("^\\n+", "");
        return new String[]{text.substring(4, end), body};
    }

    private static Map<String, Object> frontMatter(String yaml) {
        Object loaded = new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(
                new org.yaml.snakeyaml.LoaderOptions())).load(yaml);
        if (!(loaded instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> copy = new java.util.TreeMap<>();
        map.forEach((k, v) -> copy.put(String.valueOf(k), v));
        copy.remove("allowed-tools");
        return copy;
    }

    /** Whether {@code path} is one of the files a 1.x command adapter writes. Package-private for tests. */
    static boolean isCommandPath(String path) {
        return COMMAND_PATHS.contains(path);
    }
}

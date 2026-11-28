package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliVersion;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service(Service.Level.PROJECT)
public final class AiToolDetectionService {
    private static final Logger LOG = Logger.getInstance(AiToolDetectionService.class);

    /**
     * Whether a tool runs in a terminal ({@code CLI}) or a panel or app ({@code IDE_PANEL}): the plugin's own field,
     * which upstream doesn't record. {@code UNCLASSIFIED} when the tool's own documentation doesn't settle it: no
     * badge, and the generic guidance.
     */
    public enum ToolType {
        CLI, IDE_PANEL, UNCLASSIFIED;

        /** The badge shown beside the tool, or {@code null} for an unclassified tool, which gets none. */
        public @Nullable String badge() {
            return switch (this) {
                case CLI -> "CLI";
                case IDE_PANEL -> "IDE";
                case UNCLASSIFIED -> null;
            };
        }
    }

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
        Path base = Path.of(basePath);
        // The detected CLI's release decides 1.x rules within the line; with none, 1.17's apply as before.
        return AiToolDetector.detect(base, home(), rules(base), cliVersion());
    }

    /** The detected CLI's release-line rules, or, with no CLI detected, the line the project's files show. */
    DetectionRules rules(Path base) {
        DetectionRules byCli = DetectionRules.forVersion(cliVersion());
        return byCli != null ? byCli : DetectionRules.fromDisk(base);
    }

    private static @Nullable Path homeForTest;
    private static java.util.function.@Nullable Supplier<String> savedScopeForTest;

    /** The saved skills scope tests see, in place of the process environment and the CLI's global config. */
    @org.jetbrains.annotations.TestOnly
    static void setSavedScopeForTest(java.util.function.@Nullable Supplier<String> scope) {
        savedScopeForTest = scope;
    }

    /** Test seam for where global tools are looked for; {@code null} restores the user's home. */
    static void setHomeForTest(@Nullable Path home) {
        homeForTest = home;
    }

    /** Where machine-wide skills are looked for: the CLI's own home resolution ({@link HomeDirectory}). */
    static @Nullable Path home() {
        if (homeForTest != null) return homeForTest;
        return HomeDirectory.current();
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

    /**
     * One row of Manage AI Tools.
     *
     * @param viaShared set up on a 2.x project only through the shared {@code .agents/skills} copy, with nothing of
     *                  its own in a folder that isn't {@code .agents}
     */
    /**
     * One row of Manage AI Tools. {@code installsForMachine}: detected, not set up for the project, and
     * {@code openspec init} for it installs into this machine (CLI 2.1+), not the project; see
     * {@link #installsForMachine(String, boolean, String, ToolStatus, AiToolRegistry.AiTool)}.
     */
    public record ToolInfo(String name, ToolStatus status, ToolType type, @Nullable String cliId, boolean viaShared,
                           boolean installsForMachine) {
        public ToolInfo(String name, ToolStatus status, ToolType type, @Nullable String cliId, boolean viaShared) {
            this(name, status, type, cliId, viaShared, false);
        }

        public ToolInfo(String name, ToolStatus status, ToolType type, @Nullable String cliId) {
            this(name, status, type, cliId, false, false);
        }
    }

    /**
     * Whether {@code openspec init --tools <tool>} installs into this machine rather than the project, as upstream's
     * {@code resolveSkillScope} decides it ({@code dist/core/init.js}): CLI 2.1+, the tool is detected but not set up, the
     * project has no skills of its own (no tool set up in it: {@code projectHasOwnSkills}), no saved scope says
     * {@code project}, and the tool supports global skills. Configure then installs or refreshes the tool's skills for
     * every project on the machine, and the tool stays not set up for the project, as the CLI says. Pure, for tests.
     */
    static boolean installsForMachine(@Nullable String cliVersion, boolean projectHasOwnSkills, @Nullable String savedScope,
                                      ToolStatus status, AiToolRegistry.AiTool tool) {
        return cliVersion != null && CliVersion.atLeast(cliVersion, "2.1.0") && status == ToolStatus.DETECTED
                && !projectHasOwnSkills && savesMachineOrNothing(savedScope) && AiToolRegistry.supportsGlobalSkills(tool);
    }

    /**
     * Whether a saved scope leaves {@code init} on the machine: none saved, or {@code global}. Any other saved value sends
     * upstream to the project (a config value is returned as it is), or makes it refuse (an invalid environment value).
     */
    private static boolean savesMachineOrNothing(@Nullable String savedScope) {
        return savedScope == null || savedScope.isBlank() || savedScope.trim().equals("global");
    }

    /**
     * The directory OpenSpec writes a tool's files into (e.g. "Claude Code" → ".claude"), or {@code null} for a tool
     * that only installs into the home directory.
     */
    public static String getToolDirName(String toolName) {
        AiToolRegistry.AiTool tool = AiToolRegistry.byName(toolName);
        return tool != null ? tool.skillsDir() : null;
    }

    /**
     * Returns the tool status: CONFIGURED (set up, by the CLI's own rule, even when not detected), DETECTED, or AVAILABLE.
     */
    public ToolStatus getToolStatus(String toolName) {
        AiToolRegistry.AiTool tool = AiToolRegistry.byName(toolName);
        if (tool == null) return ToolStatus.AVAILABLE;
        boolean detected = scan().contains(tool);
        String basePath = project.getBasePath();
        if (basePath == null) return detected ? ToolStatus.DETECTED : ToolStatus.AVAILABLE;
        return status(new Verdict(Path.of(basePath)), tool, detected);
    }

    /**
     * What one set-up verdict reads once: the release line's rules, which 1.x rules apply (each from the release
     * that introduced it), and on 2.x the tool list as upstream reads it.
     */
    private final class Verdict {
        final Path base;
        final DetectionRules rules;
        /** The skill names the detected release recognizes (all, with no CLI). */
        final List<String> skillNames;
        /** Commands count from 1.7.0; before it, upstream's verdict reads skills only. */
        final boolean countsCommands;
        /** A tool's commands in an older folder (Antigravity's .agent/workflows) count from 1.17.0. */
        final boolean countsLegacyCommands;
        /** From 1.8 a shared skills folder's skills count only for its owner, and the marker's tool counts. */
        final boolean sharedFolderOwnership;
        final Detector2x.@Nullable Context listing;
        /** The tool ids the project's tool list names, as written. */
        final List<String> listed;
        /** Each shared skills folder's owner under 1.x rules, reconciled once per folder. */
        private final Map<String, Optional<AiToolRegistry.AiTool>> owners = new HashMap<>();

        /** The detected 1.x release, or {@code null}: 2.x rules, or no CLI (1.17's shapes with no release filter). */
        final @Nullable String release1x;

        AiToolRegistry.@Nullable AiTool sharedRootOwner(AiToolRegistry.AiTool tool) {
            return owners.computeIfAbsent(shape(tool).skillsDir(),
                    d -> Optional.ofNullable(AiToolDetector.sharedRootOwner(base, tool, release1x))).orElse(null);
        }

        /** The tool as the detected 1.x release shaped it (its folders then); the record itself otherwise. */
        AiToolRegistry.AiTool shape(AiToolRegistry.AiTool tool) {
            return AiToolRegistry.asOf1x(tool, release1x);
        }

        Verdict(Path base) {
            this.base = base;
            this.rules = rules(base);
            String version = cliVersion();
            boolean known = version != null && !version.isBlank();
            this.skillNames = AiToolRegistry.skillNames(known ? version : null);
            // 2.x, and no CLI, keep 1.17's rules: both count.
            this.countsCommands = rules.is2x() || !known || CliVersion.atLeast(version, "1.7.0");
            this.countsLegacyCommands = rules.is2x() || !known || CliVersion.atLeast(version, "1.17.0");
            this.sharedFolderOwnership = !known || CliVersion.atLeast(version, "1.8.0");
            this.listing = rules.is2x() ? new Detector2x.Context(base, rules) : null;
            this.release1x = known && !rules.is2x() ? version : null;
            this.listed = SkillToolList.ids(SkillToolList.read(base));
        }
    }

    /**
     * Upstream's {@code getConfiguredTools} for one tool, so a tool can be set up without being detected. Detected
     * tools keep the plugin's wider search (home links and folders through {@link SkillLocator}); a tool detection
     * didn't find counts only from the project, as upstream's verdict does.
     */
    private ToolStatus status(Verdict v, AiToolRegistry.AiTool tool, boolean detected) {
        if (isSetUp(v, tool, detected)) return ToolStatus.CONFIGURED;
        return detected ? ToolStatus.DETECTED : ToolStatus.AVAILABLE;
    }

    private boolean isSetUp(Verdict v, AiToolRegistry.AiTool tool, boolean detected) {
        Path base = v.base;
        if (!v.rules.knows(tool) || !countsInItsFolderGroup(v.rules, v.listed, tool)) return false;
        // A 1.x release counts only the tools it has, in the folders it gave them.
        if (v.release1x != null && !AiToolRegistry.acceptedBy(tool, v.release1x)) return false;
        AiToolRegistry.AiTool shaped = v.shape(tool);
        // Whether the skills in the tool's own skills folder are someone else's: on 2.x every project tool's skills
        // sit in the shared .agents/skills, which counts only through the list; on 1.x a shared folder's owner.
        boolean sharedSkillsNotItsOwn;
        if (v.listing != null) {
            if (Detector2x.isListedToolPresent(v.listing, tool) || Detector2x.hasSkillLinks(v.listing, tool)) return true;
            sharedSkillsNotItsOwn = ".agents".equals(v.rules.skillsDir(tool));
        } else if (v.sharedFolderOwnership && shaped.skillsDir() != null) {
            if (tool.id().equals(AiToolDetector.readMarker(base, shaped.skillsDir()))) return true;
            AiToolRegistry.AiTool owner = v.sharedRootOwner(tool);
            sharedSkillsNotItsOwn = owner != null && !owner.equals(tool);
        } else {
            sharedSkillsNotItsOwn = false;
        }
        String skillsDir = v.listing != null ? v.rules.skillsDir(tool) : shaped.skillsDir();
        if (skillsDir != null) {
            File own = base.resolve(skillsDir).toFile();
            // In a folder tools share, a command counts only at the tool's own command paths, as upstream checks it.
            boolean commands = v.countsCommands
                    && (isSharedFolder(skillsDir) ? Detector2x.hasAnyCommand(base, tool) : hasOpenSpecCommands(own));
            if (commands || (!sharedSkillsNotItsOwn && hasOpenSpecSkills(own, v.skillNames))) return true;
        }
        for (String d : shaped.legacySkillsDirs()) {
            File legacy = base.resolve(d).toFile();
            // 1.x reads older folders only for the shared folder's owner; 2.x reads them for every tool.
            if ((v.countsLegacyCommands && hasOpenSpecCommands(legacy))
                    || ((v.listing != null || !sharedSkillsNotItsOwn || AiToolRegistry.countsLegacySkillsRegardlessOfOwner(tool))
                        && hasOpenSpecSkills(legacy, v.skillNames))) {
                return true;
            }
        }
        Path home = home();
        String global = v.rules.globalSkillsDir(tool);
        if (home != null && global != null && hasOpenSpecFiles(home.resolve(global).toFile(), v.skillNames)) return true;
        // No release counts a tool as set up for its skills found elsewhere (a machine-wide install, another tool's
        // folder): upstream's getConfiguredTools reads the project. What init does for the machine is ToolInfo.installsForMachine.
        return false;
    }

    /**
     * CLI 2.1's rule for tools sharing a project folder (Trae and Trae CN): when the tool list doesn't name the tool,
     * it counts as set up only if no other tool of its group is listed and it is the group's first.
     */
    private static boolean countsInItsFolderGroup(DetectionRules rules, List<String> listed, AiToolRegistry.AiTool tool) {
        if (rules != DetectionRules.V2_1 && rules != DetectionRules.V2_1_1) return true;
        List<AiToolRegistry.AiTool> group = AiToolRegistry.folderGroup(tool);
        if (group.isEmpty()) return true;
        if (listed.contains(tool.id())) return true;
        return group.stream().noneMatch(t -> listed.contains(t.id())) && group.getFirst().equals(tool);
    }

    /**
     * Whether a set-up tool reaches its skills only through the shared {@code .agents/skills} copy on a 2.x project:
     * its own folder isn't {@code .agents}, the tool list names it, the shared copy holds a skill, and neither its link
     * folder nor its own skills folder holds an OpenSpec entry of any kind (a link, a copy, or a link as text).
     */
    boolean viaSharedFolder(Path base, AiToolRegistry.AiTool tool) {
        DetectionRules rules = rules(base);
        if (!rules.is2x() || tool.skillsDir() == null || ".agents".equals(tool.skillsDir())) return false;
        if (!SkillToolList.ids(SkillToolList.read(base)).contains(tool.id())) return false;
        Path shared = base.resolve(".agents/skills");
        boolean sharedHolds = AiToolRegistry.SKILL_NAMES.stream()
                .anyMatch(s -> Files.isRegularFile(shared.resolve(s).resolve("SKILL.md")));
        if (!sharedHolds) return false;
        List<Path> own = new ArrayList<>();
        String linkDir = SkillLinkDirs.linkDir(rules.linkLine(), tool.id());
        if (linkDir != null) own.add(base.resolve(linkDir));
        own.add(base.resolve(tool.skillsDir()).resolve("skills"));
        for (Path dir : own) {
            for (String s : AiToolRegistry.SKILL_NAMES) {
                if (Files.exists(dir.resolve(s), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return false;
            }
        }
        return true;
    }

    /** Whether more than one registry tool writes to {@code skillsDir} ({@code .agents}, or a regional pair's folder). */
    private static boolean isSharedFolder(String skillsDir) {
        return SHARED_FOLDERS.contains(skillsDir);
    }

    private static final Set<String> SHARED_FOLDERS = sharedFolders();

    private static Set<String> sharedFolders() {
        Map<String, Integer> count = new HashMap<>();
        for (AiToolRegistry.AiTool t : AiToolRegistry.all()) {
            if (t.skillsDir() != null) count.merge(t.skillsDir(), 1, Integer::sum);
        }
        Set<String> shared = new HashSet<>();
        count.forEach((dir, n) -> { if (n > 1) shared.add(dir); });
        return Set.copyOf(shared);
    }

    private static boolean hasOpenSpecFiles(File toolDir, List<String> skillNames) {
        return hasOpenSpecSkills(toolDir, skillNames) || hasOpenSpecCommands(toolDir);
    }

    /**
     * Whether {@code toolDir/skills/<name>/SKILL.md} exists for one of the release's skill names, upstream's only test
     * for an OpenSpec skill (an existence check, as its {@code existsSync}): a folder without the file, or a name the
     * release doesn't have, doesn't count.
     */
    static boolean hasOpenSpecSkills(File toolDir, List<String> skillNames) {
        File skills = new File(toolDir, "skills");
        return skillNames.stream().anyMatch(n -> new File(new File(skills, n), "SKILL.md").exists());
    }

    /** Command files, where each tool keeps them. */
    private static boolean hasOpenSpecCommands(File toolDir) {
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
        String basePath = project.getBasePath();
        Verdict verdict = basePath != null ? new Verdict(Path.of(basePath)) : null;
        String savedScope = savedScopeForTest != null ? savedScopeForTest.get() : SavedSkillsScope.current();
        Map<AiToolRegistry.AiTool, ToolStatus> statuses = new LinkedHashMap<>();
        for (AiToolRegistry.AiTool tool : AiToolRegistry.all()) {
            boolean isDetected = detected.contains(tool);
            statuses.put(tool, verdict != null ? status(verdict, tool, isDetected)
                    : isDetected ? ToolStatus.DETECTED : ToolStatus.AVAILABLE);
        }
        // Upstream's projectHasOwnSkills: a tool set up in the project (the home folder never has its own; #751).
        boolean ownSkills = statuses.containsValue(ToolStatus.CONFIGURED);
        List<ToolInfo> configured = new ArrayList<>();
        List<ToolInfo> found = new ArrayList<>();
        List<ToolInfo> available = new ArrayList<>();

        for (AiToolRegistry.AiTool tool : AiToolRegistry.all()) {
            boolean accepted = AiToolRegistry.acceptedBy(tool, version);
            ToolStatus status = statuses.get(tool);
            if (!accepted && status == ToolStatus.AVAILABLE) continue;
            boolean viaShared = status == ToolStatus.CONFIGURED && project.getBasePath() != null
                    && viaSharedFolder(Path.of(project.getBasePath()), tool);
            ToolInfo info = new ToolInfo(tool.name(), status, tool.type(), accepted ? tool.cliId() : null, viaShared,
                    installsForMachine(version, ownSkills, savedScope, status, tool));
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

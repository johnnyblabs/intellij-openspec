package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.util.UpdatePlanParser.Change;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Failure;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Note;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Plan;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything the update preview shows, worked out from a plan so the rules can be tested without Swing:
 * <ul>
 *   <li>a summary of the changes by action;</li>
 *   <li>the notes, with the legacy tool-directory move flagged;</li>
 *   <li>the failures;</li>
 *   <li>in-project changes grouped by tool (the focus tool first, then by name; deletes first within a group);</li>
 *   <li>one "Outside this project" group last;</li>
 *   <li>whether the plan is the move to OpenSpec 2 ({@link UpdateMigration}): then the shared skills, the replaced
 *       skill copies and the command files each form one group, ahead of the tools' groups;</li>
 *   <li>the files the update would delete or overwrite that OpenSpec can't confirm as its own
 *       ({@link UpdateMigration#atRiskFiles}, read before the preview opens);</li>
 *   <li>whether Apply is allowed and whether Cancel is the default button. With files at risk, Apply also needs the
 *       user's confirmation that they backed them up ({@link #canApply}).</li>
 * </ul>
 */
public record UpdatePreviewModel(String summary, List<NoteRow> notes, List<Failure> failures, List<Group> groups,
                                 @Nullable String focusTool, boolean applyEnabled, boolean cancelIsDefault,
                                 boolean migration, List<UpdateMigration.AtRisk> atRisk) {

    public static final String OUTSIDE_PROJECT = "Outside this project";
    /** The plan's tool value for untouched files an earlier release left where no tool reads them (CLI 1.17.0+). */
    public static final String EARLIER_RELEASE_TOOL = "earlier-release";
    /** How the preview names the {@link #EARLIER_RELEASE_TOOL} group. */
    public static final String EARLIER_RELEASE_LABEL = "Left by an earlier OpenSpec release";
    /** The migration's groups, keyed (in {@link Group#tool()}) by how the preview names them. */
    public static final String SHARED_SKILLS_GROUP = "Shared skills (.agents/skills)";
    public static final String REPLACED_COPIES_GROUP = "Skill copies replaced by the shared copy";
    public static final String COMMAND_FILES_GROUP = "Command files (OpenSpec 2 installs skills only)";
    /** The banner a migration plan shows above its changes. */
    public static final String MIGRATION_BANNER = "This update moves the project to OpenSpec 2: one shared copy of each "
            + "skill in .agents/skills, linked from the tool folders that need it, and no command files.";
    public static final String FAILURES_EXPLANATION =
            "openspec update stops at the first tool it can't update, so fix these before applying.";

    /** A note to show; {@code warning} marks the legacy tool-directory move, which happens without asking. */
    public record NoteRow(String code, String message, boolean warning) {
    }

    /** {@code tool} is the tool's id, or {@link #OUTSIDE_PROJECT} for the outside group. */
    public record Group(String tool, boolean outsideProject, List<Change> changes) {
        /** The tool's id and count; the earlier-release sweep is named in words rather than by its tool value. */
        public String label() {
            String name = !outsideProject && EARLIER_RELEASE_TOOL.equals(tool) ? EARLIER_RELEASE_LABEL : tool;
            return name + " (" + changes.size() + ")";
        }
    }

    private static final List<String> ACTION_ORDER = List.of("delete", "update", "add");

    public static UpdatePreviewModel of(Plan plan, @Nullable String focusTool) {
        return of(plan, focusTool, null);
    }

    /** As {@link #of(Plan, String)}, for a plan from CLI {@code cliVersion} (null when unknown: never a migration). */
    public static UpdatePreviewModel of(Plan plan, @Nullable String focusTool, @Nullable String cliVersion) {
        return of(plan, focusTool, cliVersion, List.of());
    }

    /** As {@link #of(Plan, String, String)}, with the files at risk already read from the project. */
    public static UpdatePreviewModel of(Plan plan, @Nullable String focusTool, @Nullable String cliVersion,
                                        List<UpdateMigration.AtRisk> atRisk) {
        List<NoteRow> notes = plan.notes().stream()
                .map(n -> new NoteRow(n.code(), n.message(),
                        com.johnnyblabs.openspec.util.UpdatePlanParser.LEGACY_TOOL_DIRECTORIES.equals(n.code())))
                .toList();

        boolean migration = UpdateMigration.isMigration(plan, cliVersion);
        Map<String, List<Change>> migrationGroups = new LinkedHashMap<>();
        if (migration) {
            List.of(SHARED_SKILLS_GROUP, REPLACED_COPIES_GROUP, COMMAND_FILES_GROUP)
                    .forEach(g -> migrationGroups.put(g, new ArrayList<>()));
        }
        Map<String, List<Change>> byTool = new TreeMap<>();
        List<Change> outside = new ArrayList<>();
        for (Change c : plan.changes()) {
            if (c.outsideProject()) {
                outside.add(c);
                continue;
            }
            String group = migration ? migrationGroup(UpdateMigration.kind(c)) : null;
            if (group != null) {
                migrationGroups.get(group).add(c);
            } else {
                byTool.computeIfAbsent(c.tool(), t -> new ArrayList<>()).add(c);
            }
        }
        Map<String, List<Change>> ordered = new LinkedHashMap<>();
        if (focusTool != null && byTool.containsKey(focusTool)) {
            ordered.put(focusTool, byTool.remove(focusTool));
        }
        ordered.putAll(byTool);

        List<Group> groups = new ArrayList<>();
        migrationGroups.forEach((name, changes) -> {
            if (!changes.isEmpty()) groups.add(new Group(name, false, sorted(changes)));
        });
        ordered.forEach((tool, changes) -> groups.add(new Group(tool, false, sorted(changes))));
        if (!outside.isEmpty()) {
            groups.add(new Group(OUTSIDE_PROJECT, true, sorted(outside)));
        }

        boolean deletes = plan.changes().stream().anyMatch(c -> c.action().equals("delete"));
        return new UpdatePreviewModel(summary(plan.changes()), notes, plan.failures(), List.copyOf(groups), focusTool,
                plan.failures().isEmpty(), deletes, migration, List.copyOf(atRisk));
    }

    /** The heading of the at-risk warning. */
    public static final String AT_RISK_WARNING = "OpenSpec can't confirm it generated these files. "
            + "Update will delete or overwrite them and keep no copy.";
    /** The confirmation Apply waits for when files are at risk. */
    public static final String BACKED_UP = "I've backed up these files";

    /** Whether Apply may run: no failures, and, when files are at risk, the user confirmed they backed them up. */
    public boolean canApply(boolean backedUp) {
        return applyEnabled && (atRisk.isEmpty() || backedUp);
    }

    /** The migration group a change of {@code kind} belongs to, or {@code null} for its tool's group. */
    private static @Nullable String migrationGroup(UpdateMigration.Kind kind) {
        return switch (kind) {
            case SHARED -> SHARED_SKILLS_GROUP;
            case REPLACED_COPY -> REPLACED_COPIES_GROUP;
            case COMMAND_FILE -> COMMAND_FILES_GROUP;
            case LINK, OTHER -> null;
        };
    }

    /** A link's row text, {@code <path> → .agents/skills/<skill>}, or {@code null} for a change that isn't a link. */
    public static @Nullable String linkRow(Change change) {
        return UpdateMigration.kind(change) == UpdateMigration.Kind.LINK
                ? change.path() + " \u2192 " + UpdateMigration.linkTarget(change) : null;
    }

    private static List<Change> sorted(List<Change> changes) {
        return changes.stream().sorted(Comparator.comparingInt(UpdatePreviewModel::actionRank)
                .thenComparing(Change::path)).toList();
    }

    private static int actionRank(Change c) {
        int i = ACTION_ORDER.indexOf(c.action());
        return i < 0 ? ACTION_ORDER.size() : i;
    }

    /** "24 changes: 12 deletes, 12 adds", or "No file changes" when the plan only has notes or failures. */
    static String summary(List<Change> changes) {
        if (changes.isEmpty()) {
            return "No file changes";
        }
        Map<String, Integer> counts = new TreeMap<>(Comparator.comparingInt((String a) -> {
            int i = ACTION_ORDER.indexOf(a);
            return i < 0 ? ACTION_ORDER.size() : i;
        }).thenComparing(a -> a));
        changes.forEach(c -> counts.merge(c.action(), 1, Integer::sum));
        List<String> parts = new ArrayList<>();
        counts.forEach((action, n) -> parts.add(n + " " + action + (n == 1 ? "" : "s")));
        return changes.size() + (changes.size() == 1 ? " change: " : " changes: ") + String.join(", ", parts);
    }
}

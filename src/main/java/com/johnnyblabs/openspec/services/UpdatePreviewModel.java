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
 *   <li>whether Apply is allowed and whether Cancel is the default button.</li>
 * </ul>
 */
public record UpdatePreviewModel(String summary, List<NoteRow> notes, List<Failure> failures, List<Group> groups,
                                 @Nullable String focusTool, boolean applyEnabled, boolean cancelIsDefault) {

    public static final String OUTSIDE_PROJECT = "Outside this project";
    public static final String FAILURES_EXPLANATION =
            "openspec update stops at the first tool it can't update, so fix these before applying.";

    /** A note to show; {@code warning} marks the legacy tool-directory move, which happens without asking. */
    public record NoteRow(String code, String message, boolean warning) {
    }

    /** {@code tool} is the tool's id, or {@link #OUTSIDE_PROJECT} for the outside group. */
    public record Group(String tool, boolean outsideProject, List<Change> changes) {
        public String label() {
            return tool + " (" + changes.size() + ")";
        }
    }

    private static final List<String> ACTION_ORDER = List.of("delete", "update", "add");

    public static UpdatePreviewModel of(Plan plan, @Nullable String focusTool) {
        List<NoteRow> notes = plan.notes().stream()
                .map(n -> new NoteRow(n.code(), n.message(),
                        com.johnnyblabs.openspec.util.UpdatePlanParser.LEGACY_TOOL_DIRECTORIES.equals(n.code())))
                .toList();

        Map<String, List<Change>> byTool = new TreeMap<>();
        List<Change> outside = new ArrayList<>();
        for (Change c : plan.changes()) {
            if (c.outsideProject()) {
                outside.add(c);
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
        ordered.forEach((tool, changes) -> groups.add(new Group(tool, false, sorted(changes))));
        if (!outside.isEmpty()) {
            groups.add(new Group(OUTSIDE_PROJECT, true, sorted(outside)));
        }

        boolean deletes = plan.changes().stream().anyMatch(c -> c.action().equals("delete"));
        return new UpdatePreviewModel(summary(plan.changes()), notes, plan.failures(), List.copyOf(groups), focusTool,
                plan.failures().isEmpty(), deletes);
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

package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-tests the pure label/type helpers of {@link SpecTreeModel}. These cover the glyph
 * retirement (artifact labels are now plain ids), the change-node {@code X/Y} task suffix,
 * and the apply-ready {@code CHANGE_DONE} routing — all headless, no live tree or project.
 */
class SpecTreeModelLabelTest {

    private static final String NO_GLYPHS = "checkmark/circle/minus glyphs must not appear in labels anymore";

    @Test
    void doneArtifactLabelIsPlainIdWithNoGlyph() {
        ArtifactInfo proposal = new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of());
        String label = SpecTreeModel.buildArtifactLabel(proposal);
        assertEquals("proposal", label);
        assertFalse(containsStatusGlyph(label), NO_GLYPHS);
    }

    @Test
    void readyArtifactLabelIsPlainId() {
        ArtifactInfo specs = new ArtifactInfo("specs", "specs/**/*.md", ArtifactStatus.READY, List.of());
        assertEquals("specs", SpecTreeModel.buildArtifactLabel(specs));
    }

    @Test
    void blockedArtifactKeepsNeedsSuffixButNoGlyph() {
        ArtifactInfo tasks = new ArtifactInfo("tasks", "tasks.md", ArtifactStatus.BLOCKED, List.of("design"));
        String label = SpecTreeModel.buildArtifactLabel(tasks);
        assertEquals("tasks (needs: design)", label);
        assertFalse(containsStatusGlyph(label), NO_GLYPHS);
    }

    @Test
    void blockedArtifactWithMultipleMissingDeps() {
        ArtifactInfo tasks = new ArtifactInfo("tasks", "tasks.md", ArtifactStatus.BLOCKED, List.of("design", "specs"));
        assertEquals("tasks (needs: design, specs)", SpecTreeModel.buildArtifactLabel(tasks));
    }

    @Test
    void changeLabelAppendsTaskProgress() {
        String label = SpecTreeModel.buildChangeLabel("my-change", new int[]{3, 7});
        assertTrue(label.contains("3/7"), "task progress X/Y must appear: " + label);
        assertEquals("my-change 3/7", label);
    }

    @Test
    void changeLabelHasNoSlashWhenNoTasksArtifact() {
        String label = SpecTreeModel.buildChangeLabel("my-change", null);
        assertFalse(label.contains("/"), "no task suffix when there is no tasks artifact: " + label);
        assertEquals("my-change", label);
    }

    @Test
    void changeLabelHasNoSuffixForZeroTasks() {
        String label = SpecTreeModel.buildChangeLabel("my-change", new int[]{0, 0});
        assertFalse(label.contains("/"), "an empty tasks file yields no X/Y suffix: " + label);
        assertEquals("my-change", label);
    }

    @Test
    void changeLabelIsNameAndCount() {
        // The invented [status] tag is retired — the label is just the name (+ optional X/Y count).
        String label = SpecTreeModel.buildChangeLabel("my-change", new int[]{1, 4});
        assertEquals("my-change 1/4", label);
    }

    @Test
    void completeDagRoutesToChangeDone() {
        ChangeArtifactDag dag = new ChangeArtifactDag();
        dag.setComplete(true);
        assertEquals(TreeNodeType.CHANGE_DONE, SpecTreeModel.changeNodeType(dag, null));
    }

    @Test
    void incompleteDagRoutesToPlainChange() {
        ChangeArtifactDag dag = new ChangeArtifactDag();
        dag.setComplete(false);
        assertEquals(TreeNodeType.CHANGE, SpecTreeModel.changeNodeType(dag, null));
    }

    @Test
    void nullDagRoutesToPlainChange() {
        assertEquals(TreeNodeType.CHANGE, SpecTreeModel.changeNodeType(null, null));
    }

    // --- Archive-ready routing: artifacts complete (CLI) AND all tasks done (N/N, total > 0) ---
    // DAGs come from captured real CLI `status --json` (isComplete true/false), not setters.

    private static ChangeArtifactDag capturedDag(String fixture) throws java.io.IOException {
        try (java.io.InputStream in = SpecTreeModelLabelTest.class.getResourceAsStream("/fixtures/cli/" + fixture)) {
            assertNotNull(in, "missing fixture " + fixture);
            return com.johnnyblabs.openspec.util.CliOutputParser.parseChangeStatus(
                    new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static ChangeArtifactDag complete() throws java.io.IOException {
        ChangeArtifactDag dag = capturedDag("1.7.0/status-complete.json");
        assertTrue(dag.isComplete(), "fixture must be the CLI's complete DAG");
        return dag;
    }

    private static ChangeArtifactDag incomplete() throws java.io.IOException {
        ChangeArtifactDag dag = capturedDag("1.7.0/status.json");
        assertFalse(dag.isComplete(), "fixture must be the CLI's incomplete DAG");
        return dag;
    }

    @Test
    void completeDagAndAllTasksDone_isArchiveReady() throws Exception {
        assertEquals(TreeNodeType.CHANGE_ARCHIVE_READY, SpecTreeModel.changeNodeType(complete(), new int[]{3, 3}));
    }

    @Test
    void incompleteDagWithAllTasksDone_isPlainChange() throws Exception {
        assertEquals(TreeNodeType.CHANGE, SpecTreeModel.changeNodeType(incomplete(), new int[]{3, 3}),
                "all tasks done but an artifact missing is not archive-ready");
    }

    @Test
    void noCliWithAllTasksDone_isPlainChange() {
        assertEquals(TreeNodeType.CHANGE, SpecTreeModel.changeNodeType(null, new int[]{3, 3}),
                "without the CLI's artifact signal neither badge shows — the artifact half is never guessed");
    }

    @Test
    void completeDagWithOpenTasks_isApplyReady() throws Exception {
        assertEquals(TreeNodeType.CHANGE_DONE, SpecTreeModel.changeNodeType(complete(), new int[]{2, 3}));
    }

    @Test
    void completeDagWithZeroTasks_isApplyReady() throws Exception {
        assertEquals(TreeNodeType.CHANGE_DONE, SpecTreeModel.changeNodeType(complete(), new int[]{0, 0}),
                "an empty task list is not 'all tasks done'");
    }

    @Test
    void completeDagWithNoTasksArtifact_isApplyReady() throws Exception {
        assertEquals(TreeNodeType.CHANGE_DONE, SpecTreeModel.changeNodeType(complete(), null));
    }

    @Test
    void capturedTaskCountsRouteLikeTheClientsTaskStatus() throws Exception {
        // End to end through the real count chain: the client's list --json calls t-cr-only 1/1 "complete"
        // and task-markers 1/6 — so with a complete DAG they are archive-ready and apply-ready respectively.
        assertEquals(TreeNodeType.CHANGE_ARCHIVE_READY, SpecTreeModel.changeNodeType(complete(),
                com.johnnyblabs.openspec.util.ApplyPromptBuilder.countTasks(
                        rawFixture("1.13.2/task-grammar/openspec/changes/t-cr-only/tasks.md"))));
        assertEquals(TreeNodeType.CHANGE_DONE, SpecTreeModel.changeNodeType(complete(),
                com.johnnyblabs.openspec.util.ApplyPromptBuilder.countTasks(
                        rawFixture("1.13.2/task-markers/openspec/changes/c/tasks.md"))));
    }

    private static String rawFixture(String path) throws java.io.IOException {
        try (java.io.InputStream in = SpecTreeModelLabelTest.class.getResourceAsStream("/fixtures/cli/" + path)) {
            assertNotNull(in, "missing fixture " + path);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void exactlyTheThreeChangeRollupTypesAreChangeNodes() {
        java.util.Set<TreeNodeType> changeTypes = java.util.EnumSet.noneOf(TreeNodeType.class);
        for (TreeNodeType t : TreeNodeType.values()) {
            if (t.isChange()) changeTypes.add(t);
        }
        assertEquals(java.util.EnumSet.of(TreeNodeType.CHANGE, TreeNodeType.CHANGE_DONE, TreeNodeType.CHANGE_ARCHIVE_READY),
                changeTypes);
    }

    @Test
    void archiveReadyTooltipIsASuggestionThatDefersToVerify_oneFactPerLine() {
        String tip = SpecTreeModel.changeTooltip(TreeNodeType.CHANGE_ARCHIVE_READY, new int[]{3, 3}, "/p/c");
        assertTrue(tip.startsWith("<html>") && tip.endsWith("</html>"), tip);
        List<String> lines = List.of(tip.substring("<html>".length(), tip.length() - "</html>".length()).split("<br>"));
        assertEquals(4, lines.size(), "status / count / Verify hint / path, each on its own line: " + lines);
        assertTrue(lines.get(0).startsWith("<b>All tasks done</b>"), "status first, emphasized: " + lines.get(0));
        assertTrue(lines.get(0).contains("suggestion"), lines.get(0));
        assertEquals("3/3 tasks", lines.get(1));
        assertTrue(lines.get(2).contains("Verify"), lines.get(2));
        assertEquals("<small>/p/c</small>", lines.get(3));
        String lower = tip.toLowerCase(java.util.Locale.ROOT);
        for (String overclaim : List.of("guarantee", "safe", "will archive")) {
            assertFalse(lower.contains(overclaim), "must not overclaim (" + overclaim + "): " + tip);
        }
    }

    @Test
    void applyReadyTooltipHasNoVerifyHint_andPlainChangeIsJustThePath() {
        assertEquals("<html><b>Apply-ready</b> — all artifacts complete<br>2/3 tasks<br><small>/p/c</small></html>",
                SpecTreeModel.changeTooltip(TreeNodeType.CHANGE_DONE, new int[]{2, 3}, "/p/c"));
        assertEquals("/p/c", SpecTreeModel.changeTooltip(TreeNodeType.CHANGE, null, "/p/c"));
    }

    @Test
    void tooltipPathIsProjectRelativeWhenUnderTheProject() {
        assertEquals("openspec/changes/ship-it",
                SpecTreeModel.displayPath("/work/proj", "/work/proj/openspec/changes/ship-it"));
        assertEquals("openspec/changes/ship-it",
                SpecTreeModel.displayPath("/work/proj/", "/work/proj/./openspec/changes/ship-it"));
    }

    @Test
    void tooltipPathStaysAbsoluteOutsideTheProjectOrWithoutARoot() {
        // A change in a store outside the project, a sibling dir sharing a name prefix, or no project root.
        assertEquals("/stores/team/openspec/changes/x", SpecTreeModel.displayPath("/work/proj", "/stores/team/openspec/changes/x"));
        assertEquals("/work/project2/openspec/changes/x", SpecTreeModel.displayPath("/work/proj", "/work/project2/openspec/changes/x"));
        assertEquals("/work/proj/openspec/changes/x", SpecTreeModel.displayPath(null, "/work/proj/openspec/changes/x"));
        assertNull(SpecTreeModel.displayPath("/work/proj", null));
    }

    @Test
    void tooltipPathIsHtmlEscaped() {
        String tip = SpecTreeModel.changeTooltip(TreeNodeType.CHANGE_DONE, new int[]{1, 2}, "/p/a&b<c>");
        assertTrue(tip.contains("<small>/p/a&amp;b&lt;c&gt;</small>"), "a path must never inject markup: " + tip);
    }

    private static boolean containsStatusGlyph(String s) {
        return s.indexOf('✓') >= 0   // ✓
                || s.indexOf('○') >= 0  // ○
                || s.indexOf('−') >= 0; // −
    }
}

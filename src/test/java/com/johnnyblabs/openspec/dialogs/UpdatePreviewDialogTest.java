package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.UpdatePreviewModel;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.UpdatePlanParser;

import com.intellij.ui.ColoredTreeCellRenderer;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The real preview dialog built from captured plans: Apply gating, the default button, and the visible groups. */
public class UpdatePreviewDialogTest extends BasePlatformTestCase {

    private static UpdatePlanParser.Plan plan(String name) {
        return plan("1.15.0", name);
    }

    private static UpdatePlanParser.Plan plan(String version, String name) {
        String base = "/fixtures/cli/" + version + "/update-dry-run/" + name;
        return (UpdatePlanParser.Plan) UpdatePlanParser.parse(new CliRunner.CliResult(
                Integer.parseInt(read(base + ".exit").strip()), read(base + ".json"), ""));
    }

    private static String read(String path) {
        try (InputStream is = UpdatePreviewDialogTest.class.getResourceAsStream(path)) {
            assertNotNull(path, is);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private UpdatePreviewDialog dialog(UpdatePreviewModel model) {
        UpdatePreviewDialog dialog = new UpdatePreviewDialog(getProject(), model);
        Disposer.register(getTestRootDisposable(), dialog.getDisposable());
        return dialog;
    }

    /** Each group row's text as the tree's own renderer draws it, so a renderer that stops using the label fails. */
    private static List<String> groupLabels(UpdatePreviewDialog dialog) {
        JTree tree = dialog.treeForTest();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            Object node = root.getChildAt(i);
            ColoredTreeCellRenderer rendered = (ColoredTreeCellRenderer) tree.getCellRenderer()
                    .getTreeCellRendererComponent(tree, node, false, false, false, i, false);
            labels.add(rendered.getCharSequence(false).toString());
        }
        return labels;
    }

    public void testAPlanThatDeletesMakesCancelTheDefaultAndCanBeApplied() {
        UpdatePreviewDialog d = dialog(UpdatePreviewModel.of(plan("windsurf-move"), null));
        assertTrue(d.isApplyEnabledForTest());
        assertTrue("deletes make Cancel the default", d.isCancelDefaultForTest());
        assertEquals(List.of("devin (24)"), groupLabels(d));
        assertEquals(UpdatePreviewDialog.TITLE, d.getTitle());
    }

    public void testFailuresDisableApply() {
        UpdatePreviewDialog d = dialog(UpdatePreviewModel.of(plan("read-only-failure"), null));
        assertFalse(d.isApplyEnabledForTest());
        assertFalse("nothing is deleted", d.isCancelDefaultForTest());
        assertEquals(List.of("claude (5)"), groupLabels(d));
    }

    public void testTheGlobalConfigShowsInTheOutsideGroup() {
        UpdatePreviewDialog d = dialog(UpdatePreviewModel.of(plan("first-run-global-config"), null));
        assertEquals(List.of(UpdatePreviewModel.OUTSIDE_PROJECT + " (1)"), groupLabels(d));
        assertTrue(d.isApplyEnabledForTest());
    }

    public void testTheEarlierReleaseGroupIsNamedInWords() {
        UpdatePreviewDialog d = dialog(UpdatePreviewModel.of(plan("1.17.0", "earlier-release"), null));
        assertEquals(List.of("antigravity (13)", UpdatePreviewModel.EARLIER_RELEASE_LABEL + " (6)"), groupLabels(d));
        assertTrue("its deletes make Cancel the default", d.isCancelDefaultForTest());
    }

    // ---------- The move to OpenSpec 2 ----------

    private static UpdatePlanParser.Plan migrationPlan(String version, String name) {
        String base = "/fixtures/cli/" + version + "/update-migration/" + name + "/plan";
        return (UpdatePlanParser.Plan) UpdatePlanParser.parse(new CliRunner.CliResult(
                Integer.parseInt(read(base + ".exit").strip()), read(base + ".json"), ""));
    }

    /** Every label's text in the dialog's content, as shown. */
    private static List<String> labels(UpdatePreviewDialog dialog) {
        List<String> texts = new ArrayList<>();
        collect(dialog.centerForTest(), texts);
        return texts;
    }

    private static void collect(java.awt.Component c, List<String> texts) {
        if (c instanceof javax.swing.JLabel l && l.getText() != null) {
            texts.add(l.getText().replaceAll("<[^>]+>", ""));
        }
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) collect(child, texts);
        }
    }

    public void testFilesAtRiskAreListedAndApplyFollowsTheBackupCheckbox() {
        var risk = List.of(
                new com.johnnyblabs.openspec.services.UpdateMigration.AtRisk(".claude/skills/openspec-explore/SKILL.md", "delete"),
                new com.johnnyblabs.openspec.services.UpdateMigration.AtRisk(".cursor/skills/openspec-explore/SKILL.md", "delete"));
        UpdatePreviewDialog d = dialog(UpdatePreviewModel.of(migrationPlan("2.1.1", "edited"), null, "2.1.1", risk));
        List<String> shown = labels(d);
        assertTrue(shown.toString(), shown.contains(com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(UpdatePreviewModel.AT_RISK_WARNING)));
        assertTrue(shown.toString(), shown.contains(".claude/skills/openspec-explore/SKILL.md  (delete)"));
        assertTrue(shown.toString(), shown.contains(".cursor/skills/openspec-explore/SKILL.md  (delete)"));
        assertTrue("the migration banner", shown.contains(com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(UpdatePreviewModel.MIGRATION_BANNER)));

        var box = d.backedUpForTest();
        assertNotNull(box);
        assertEquals(UpdatePreviewModel.BACKED_UP, box.getText());
        assertFalse("Apply waits for the confirmation", d.isApplyEnabledForTest());
        box.setSelected(true);
        assertTrue(d.isApplyEnabledForTest());
        box.setSelected(false);
        assertFalse(d.isApplyEnabledForTest());
        assertTrue(d.isCancelDefaultForTest());
    }

    public void testNothingAtRiskMeansNoCheckboxAndApplyAtOnce() {
        UpdatePreviewDialog d = dialog(UpdatePreviewModel.of(migrationPlan("2.0.0", "edited"), null, "2.0.0", List.of()));
        assertNull(d.backedUpForTest());
        assertTrue(d.isApplyEnabledForTest());
        assertFalse(labels(d).contains(com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(UpdatePreviewModel.AT_RISK_WARNING)));
    }

    public void testALinkRowReadsAsALinkToItsSharedSkill() {
        UpdatePreviewDialog d = dialog(UpdatePreviewModel.of(migrationPlan("2.1.1", "unedited"), null, "2.1.1"));
        JTree tree = d.treeForTest();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(i);
            for (int j = 0; j < group.getChildCount(); j++) {
                ColoredTreeCellRenderer r = (ColoredTreeCellRenderer) tree.getCellRenderer()
                        .getTreeCellRendererComponent(tree, group.getChildAt(j), false, false, true, 0, false);
                rows.add(r.getCharSequence(false).toString());
            }
        }
        assertTrue(rows.toString(), rows.contains(
                ".claude/skills/openspec-explore \u2192 .agents/skills/openspec-explore  link"));
    }

    public void testNotesThatReachTheMachineAreWarningsQuotedAsTheCliPrintedThem() {
        UpdatePlanParser.Plan p = plan("2.1.1", "global-skills-removed");
        UpdatePreviewModel m = UpdatePreviewModel.of(p, null, "2.1.1");
        UpdatePreviewDialog d = dialog(m);
        assertEquals(2, p.notes().size());
        for (UpdatePlanParser.Note note : p.notes()) {
            assertTrue(note.code(), m.notes().stream().anyMatch(n -> n.code().equals(note.code()) && n.warning()));
            assertTrue(note.message(), labels(d).contains(com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(note.message())));
        }
    }
}

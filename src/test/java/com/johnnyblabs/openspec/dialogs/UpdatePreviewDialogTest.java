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
}

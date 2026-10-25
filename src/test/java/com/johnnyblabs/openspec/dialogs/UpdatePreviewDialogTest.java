package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.UpdatePreviewModel;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.UpdatePlanParser;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The real preview dialog built from captured plans: Apply gating, the default button, and the visible groups. */
public class UpdatePreviewDialogTest extends BasePlatformTestCase {

    private static UpdatePlanParser.Plan plan(String name) {
        String base = "/fixtures/cli/1.15.0/update-dry-run/" + name;
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

    private static List<String> groupLabels(UpdatePreviewDialog dialog) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) dialog.treeForTest().getModel().getRoot();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            labels.add(((UpdatePreviewModel.Group) ((DefaultMutableTreeNode) root.getChildAt(i)).getUserObject()).label());
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
}

package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.johnnyblabs.openspec.actions.OpenSpecValidateAction;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeData;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;

import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Change Deltas pane's validation line through the real panel. The "CLI" is a shell script that answers
 * {@code show} and {@code validate} with captured output and logs every call, so each test checks both what the pane
 * shows and which CLI runs happened. macOS and Linux only, like {@link ChangeDeltasRejectionPanelTest}.
 */
public class ChangeValidationLinePanelTest extends OpenSpecIntegrationTestBase {

    private static final String CHANGE = "add-auth";
    private static final String SHOW_DELTAS = "1.14.3/additive-shapes/show-change.json";
    private static final String VALIDATE = "1.15.0/validate-change/";

    private File script;
    private File log;
    private File changeDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (SystemInfo.isWindows) return;
        File dir = FileUtil.createTempDirectory("validation-line", null, true);
        // The CLI runs in the project's base dir, which a light project doesn't create on disk.
        Files.createDirectories(Path.of(getProject().getBasePath()));
        changeDir = new File(dir, CHANGE);
        assertTrue(changeDir.mkdirs());
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
        script = new File(dir, "openspec");
        log = new File(dir, "calls.log");
        answer(SHOW_DELTAS, 0, "valid-change");
        CliDetectionService detection = getProject().getService(CliDetectionService.class);
        CliDetectionTestSupport.fakeAvailable(detection, "1.15.0");
        assertTrue(detection.tryPath(script.getPath()));
    }

    /** Makes the fake CLI answer {@code show} with {@code showFixture} and {@code validate} with a captured verdict. */
    private void answer(String showFixture, int showExit, String validateCase) throws Exception {
        Path show = fixture(showFixture);
        Path validate = fixture(VALIDATE + validateCase + ".json");
        String validateExit = Files.readString(fixture(VALIDATE + validateCase + ".exit")).strip();
        Files.writeString(script.toPath(), "#!/bin/sh\n"
                + "echo \"$@\" >> '" + log + "'\n"
                + "case \"$1\" in\n"
                + "  validate) cat '" + validate + "'; exit " + validateExit + ";;\n"
                + "  *) cat '" + show + "'; exit " + showExit + ";;\n"
                + "esac\n");
        assertTrue(script.setExecutable(true));
    }

    private Path fixture(String path) throws Exception {
        return Path.of(getClass().getResource("/fixtures/cli/" + path).toURI());
    }

    private List<String> calls(String command) throws Exception {
        if (!log.exists()) return List.of();
        return Files.readAllLines(log.toPath()).stream().filter(l -> l.startsWith(command + " ")).toList();
    }

    private DefaultTreeModel model(String name, String path) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");
        DefaultMutableTreeNode changes = new DefaultMutableTreeNode(
                new TreeNodeData("Changes", TreeNodeType.CHANGES, null, null, null, "Active changes"));
        changes.add(new DefaultMutableTreeNode(new TreeNodeData(name, TreeNodeType.CHANGE, path, name, null)));
        changes.add(new DefaultMutableTreeNode(new TreeNodeData("other", TreeNodeType.CHANGES, null, null, null, "spacer")));
        root.add(changes);
        return new DefaultTreeModel(root);
    }

    private OpenSpecToolWindowPanel panel(DefaultTreeModel model) {
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        waitFor(() -> panel.treeModelForTest() == model, "the model is shown");
        return panel;
    }

    private static DefaultMutableTreeNode node(DefaultTreeModel model, int index) {
        DefaultMutableTreeNode changes = (DefaultMutableTreeNode) ((DefaultMutableTreeNode) model.getRoot()).getChildAt(0);
        return (DefaultMutableTreeNode) changes.getChildAt(index);
    }

    /** Selects the spacer, then the change, and waits for a preview whose accessible name starts with {@code name}. */
    private static String showChange(OpenSpecToolWindowPanel panel, DefaultTreeModel model, String name) {
        panel.treeForTest().setSelectionPath(new TreePath(node(model, 1).getPath()));
        // Wait for the spacer's empty state, so the change's preview below can't be the previous render.
        waitFor(() -> OpenSpecToolWindowPanel.PREVIEW_EMPTY_NAME.equals(panel.previewAccessibleNameForTest()),
                "the empty state");
        panel.treeForTest().setSelectionPath(new TreePath(node(model, 0).getPath()));
        waitFor(() -> panel.previewAccessibleNameForTest() != null
                && panel.previewAccessibleNameForTest().startsWith(name), "a preview named " + name);
        return panel.previewHtmlForTest();
    }

    private static void waitFor(BooleanSupplier condition, String what) {
        long end = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < end && !condition.getAsBoolean()) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        assertTrue("timed out waiting: " + what, condition.getAsBoolean());
    }

    /** The pane's CSS names every marker, so match the element's class, not the bare name. */
    private static int countClass(String html, String marker) {
        String needle = "class=\"" + marker + "\"";
        int n = 0;
        for (int i = html.indexOf(needle); i >= 0; i = html.indexOf(needle, i + 1)) n++;
        return n;
    }

    private static final String CHANGE_VALIDATE_CALL = "validate " + CHANGE + " --type change --json";

    public void testAValidChangeShowsTheClisVerdictRunsValidateOnceAndCachesTheLine() throws Exception {
        if (SystemInfo.isWindows) return;
        DefaultTreeModel model = model(CHANGE, changeDir.getPath());
        OpenSpecToolWindowPanel panel = panel(model);

        String html = showChange(panel, model, OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME);
        assertEquals(html, 1, countClass(html, SpecPreviewRenderer.VALIDATION_LINE_CLASS));
        assertTrue(html, html.contains("Validate: passed"));
        assertTrue(html, html.contains("openspec-validate:" + CHANGE));
        assertEquals("the pane runs exactly Validate's change-scoped command",
                List.of(CHANGE_VALIDATE_CALL), calls("validate"));
        assertEquals(String.join(" ", OpenSpecValidateAction.changeCliArgs(CHANGE)), CHANGE_VALIDATE_CALL);
        assertEquals(1, calls("show").size());

        String again = showChange(panel, model, OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME);
        assertTrue("the cached fragment carries the line", again.contains("Validate: passed"));
        assertEquals("a cached selection runs nothing", 1, calls("validate").size());
        assertEquals(1, calls("show").size());
    }

    public void testAFailingChangeShowsTheClisCounts() throws Exception {
        if (SystemInfo.isWindows) return;
        answer(SHOW_DELTAS, 0, "error-and-warning-change");
        DefaultTreeModel model = model(CHANGE, changeDir.getPath());
        String html = showChange(panel(model), model, OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME);
        assertTrue(html, html.contains("Validate: 1 error, 1 warning"));
    }

    public void testNoVerdictShowsTheDeltasWithoutALineAndAsksAgainNextTime() throws Exception {
        if (SystemInfo.isWindows) return;
        answer(SHOW_DELTAS, 0, "invalid-name-envelope");
        DefaultTreeModel model = model(CHANGE, changeDir.getPath());
        OpenSpecToolWindowPanel panel = panel(model);

        String html = showChange(panel, model, OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME);
        assertEquals(html, 0, countClass(html, SpecPreviewRenderer.VALIDATION_LINE_CLASS));
        assertEquals("the deltas still render", 1, countClass(html, SpecPreviewRenderer.DELTA_LEGEND_CLASS));

        answer(SHOW_DELTAS, 0, "valid-change");
        String again = showChange(panel, model, OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME);
        assertTrue("without a verdict nothing was cached, so the change is read again", again.contains("Validate: passed"));
        assertEquals(2, calls("validate").size());
        assertEquals(2, calls("show").size());
    }

    public void testShowIssuesRunsValidateForThatChangeOnly() throws Exception {
        if (SystemInfo.isWindows) return;
        DefaultTreeModel model = model(CHANGE, changeDir.getPath());
        OpenSpecToolWindowPanel panel = panel(model);
        showChange(panel, model, OpenSpecToolWindowPanel.PREVIEW_CHANGE_DELTAS_NAME);

        var pane = panel.previewPaneForTest();
        pane.fireHyperlinkUpdate(new HyperlinkEvent(pane, HyperlinkEvent.EventType.ACTIVATED, null,
                "openspec-validate:" + CHANGE));
        waitFor(() -> {
            try {
                return calls("validate").size() == 2;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "Validate runs for the change");
        assertEquals(List.of(CHANGE_VALIDATE_CALL, CHANGE_VALIDATE_CALL), calls("validate"));
    }

    public void testARejectedChangeHasNoLineAndRunsNoValidate() throws Exception {
        if (SystemInfo.isWindows) return;
        answer("1.15.0/errors/show-change-missing.stdout", 1, "valid-change");
        DefaultTreeModel model = model(CHANGE, changeDir.getPath());
        String html = showChange(panel(model), model, OpenSpecToolWindowPanel.PREVIEW_CLI_REJECTED_NAME);
        assertEquals(html, 0, countClass(html, SpecPreviewRenderer.VALIDATION_LINE_CLASS));
        assertTrue(calls("validate").isEmpty());
    }

    public void testWithoutTheCliTheBuiltInCheckIsLabelledAndHasNoLink() throws Exception {
        if (SystemInfo.isWindows) return;
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        // A change with a proposal but no delta specs: the built-in check reports it.
        myFixture.addFileToProject("openspec/changes/no-deltas/.openspec.yaml", "schema: spec-driven\n");
        myFixture.addFileToProject("openspec/changes/no-deltas/proposal.md", "## Why\nA reason.\n");
        VirtualFile dir = getChangeDir("no-deltas");
        assertNotNull(dir);
        DefaultTreeModel model = model("no-deltas", dir.getPath());

        String html = showChange(panel(model), model, OpenSpecToolWindowPanel.PREVIEW_CLI_UNAVAILABLE_NAME);
        assertEquals(html, 1, countClass(html, SpecPreviewRenderer.VALIDATION_LINE_CLASS));
        assertTrue(html, html.contains("Built-in check: 1 error"));
        assertFalse(html, html.contains("Validate:"));
        assertFalse(html, html.contains("openspec-validate:"));
        assertTrue(calls("validate").isEmpty());
    }
}

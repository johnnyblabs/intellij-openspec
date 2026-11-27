package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The run Propose and Fast-Forward start in the workflow panel, against real CLI output: a change on a schema with one
 * artifact past the apply gate ({@code fixtures/cli/2.1.1/new-change-past-apply/}). The run generates the
 * apply-required artifacts, stops once the change is ready to apply, says so, and then runs what follows. The fed
 * status follows the files the run writes, so each step reads its captured status. Also the creation step itself:
 * without the CLI, Propose scaffolds the change as {@code openspec new change} does; Fast-Forward, which needs the
 * CLI, reports the failure and creates nothing.
 */
public class ProposeRunPanelTest extends OpenSpecIntegrationTestBase {

    private static final String CHANGE = "retro-demo";
    private static final String REQUEST = "Add a retro step to every change.";
    private static final String CAPTURED_CHANGE_DIR = "/fixture/demo-project/openspec/changes/retro-demo";

    private Path changeDir;
    private WorkflowActionPanel workflowPanel;
    private final List<String> generated = new ArrayList<>();
    private final List<String> requests = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Path root = Files.createTempDirectory("openspec-propose-run").toRealPath();
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), root.toString());
        changeDir = root.resolve(CHANGE);
        Files.createDirectories(changeDir);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir);

        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        ChangeService changes = mock(ChangeService.class);
        when(changes.getActiveChanges()).thenReturn(List.of(new Change(CHANGE, changeDir.toString())));
        ServiceContainerUtil.replaceService(getProject(), ChangeService.class, changes, getTestRootDisposable());

        String dir = changeDir.toString().replace('\\', '/');
        ArtifactOrchestrationService orchestration = getProject().getService(ArtifactOrchestrationService.class);
        orchestration.setStatusCliForTest((p, cliArgs) -> {
            List<String> args = cliArgs.requested();
            try {
                if (args.get(0).equals("status")) {
                    return new CliRunner.CliResult(0, fixture("status-step" + written() + ".json"), "");
                }
                if (args.get(0).equals("instructions")) {
                    return new CliRunner.CliResult(0,
                            fixture("instructions-" + args.get(1) + ".json").replace(CAPTURED_CHANGE_DIR, dir), "");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new CliRunner.CliResult(1, "", "not fed: " + String.join(" ", args));
        });
        Disposer.register(getTestRootDisposable(), () -> {
            orchestration.setStatusCliForTest(null);
            orchestration.invalidateAllCaches();
        });

        DirectApiService api = mock(DirectApiService.class);
        when(api.isConfigured()).thenReturn(true);
        when(api.generate(any(), any())).thenAnswer(inv -> {
            String id = ((ArtifactInstruction) inv.getArgument(0)).artifactId();
            generated.add(id);
            requests.add(inv.getArgument(1));
            return id.equals("specs") ? "<!-- file: specs/demo/spec.md -->\n## ADDED Requirements\n" : "# " + id + "\n";
        });
        ServiceContainerUtil.replaceService(getProject(), DirectApiService.class, api, getTestRootDisposable());

        DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("OpenSpec"));
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        waitFor(() -> panel.treeModelForTest() == model);
        workflowPanel = panel.workflowPanelForTest();
        workflowPanel.setActiveChange(CHANGE);
        waitFor(() -> CHANGE.equals(workflowPanel.activeChangeForTest()));
        dispatch();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            WorkflowActionPanel.setNewChangeCliForTest(null);
            if (workflowPanel != null) workflowPanel.disposeForTest();
            if (workflowPanel != null) PanelStatusTestSupport.awaitIdle(workflowPanel);
            if (changeDir != null) {
                deleteTree(changeDir.getParent());
                LocalFileSystem.getInstance().refreshNioFiles(List.of(changeDir.getParent()), false, true, null);
            }
        } finally {
            super.tearDown();
        }
    }

    public void testTheRunStopsOnceTheChangeIsReadyToApplyAndSaysSo() throws Exception {
        AtomicInteger after = new AtomicInteger();
        workflowPanel.startFastForwardGeneration(CHANGE, REQUEST, after::incrementAndGet);
        waitFor(() -> after.get() == 1);
        dispatch();

        assertEquals(List.of("proposal", "specs", "design", "tasks"), generated);
        assertEquals("every artifact of the run carries the request", List.of(REQUEST, REQUEST, REQUEST, REQUEST), requests);
        assertFalse("the artifact past the apply gate is left", Files.exists(changeDir.resolve("retro.md")));
        assertEquals("✓ Ready to apply", workflowPanel.verifyStatusTextForTest());
        assertEquals("what follows runs once", 1, after.get());
    }

    public void testWithoutTheCliProposeScaffoldsTheChangeAndStartsTheRun() throws Exception {
        List<String> errors = new ArrayList<>();
        AtomicInteger created = new AtomicInteger();
        workflowPanel.proposeAndGenerate("add-retro", REQUEST, null, true, errors::add, created::incrementAndGet, null);
        waitFor(() -> created.get() == 1);

        assertEquals(List.of(), errors);
        VirtualFile change = OpenSpecFileUtil.getChangesDir(getProject()).findChild("add-retro");
        assertNotNull("the change was created", change);
        assertEquals(List.of(".openspec.yaml"),
                java.util.Arrays.stream(change.getChildren()).map(VirtualFile::getName).toList());
        assertEquals("add-retro", workflowPanel.activeChangeForTest());
        waitFor(() -> !generated.isEmpty());
        assertEquals("the run started with the request", REQUEST, requests.get(0));
    }

    public void testFastForwardNeedsTheCliAndCreatesNothingWithoutIt() throws Exception {
        List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger created = new AtomicInteger();
        WorkflowActionPanel.setNewChangeCliForTest((project, args) -> {
            throw new CliRunner.CliException("OpenSpec CLI not found");
        });
        workflowPanel.proposeAndGenerate("ff-without-cli", REQUEST, null, false, errors::add, created::incrementAndGet, null);
        waitFor(() -> !errors.isEmpty());
        assertTrue(errors.toString(), errors.get(0).contains("OpenSpec CLI not found"));

        assertEquals(0, created.get());
        VirtualFile changes = OpenSpecFileUtil.getChangesDir(getProject());
        assertNull(changes == null ? null : changes.findChild("ff-without-cli"));
        assertTrue(generated.isEmpty());
    }

    public void testWithTheCliItRunsNewChangeWithTheSchemaThenStartsTheRun() throws Exception {
        CliDetectionTestSupport.fakeAvailable(getProject().getService(CliDetectionService.class), "2.1.1");
        List<List<String>> runs = new java.util.concurrent.CopyOnWriteArrayList<>();
        WorkflowActionPanel.setNewChangeCliForTest((project, args) -> {
            runs.add(args.requested());
            return new CliRunner.CliResult(0, "", "");
        });
        AtomicInteger created = new AtomicInteger();
        workflowPanel.proposeAndGenerate(CHANGE, REQUEST, "with-retro", true, e -> fail(e), created::incrementAndGet, null);
        waitFor(() -> created.get() == 1);

        assertEquals(1, runs.size());
        assertTrue(runs.get(0).toString(), runs.get(0).containsAll(List.of("new", "change", CHANGE, "--schema", "with-retro")));
        assertFalse("never --description, which writes a README", runs.get(0).contains("--description"));
        waitFor(() -> !generated.isEmpty());
    }

    // --- helpers ---

    /** How many of the change's artifacts are on disk, in schema order. */
    private int written() throws IOException {
        int n = 0;
        if (Files.exists(changeDir.resolve("proposal.md"))) n++;
        if (Files.exists(changeDir.resolve("specs/demo/spec.md"))) n++;
        if (Files.exists(changeDir.resolve("design.md"))) n++;
        if (Files.exists(changeDir.resolve("tasks.md"))) n++;
        if (Files.exists(changeDir.resolve("retro.md"))) n++;
        return n;
    }

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/fixtures/cli/2.1.1/new-change-past-apply/" + name));
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertTrue("timed out", condition.getAsBoolean());
    }

    private static void dispatch() {
        for (int i = 0; i < 5; i++) PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}

package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.util.CliRunner;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fast-Forward sends the user's description as the request for the change: into the first artifact's prompt for
 * clipboard and editor-tab delivery, into every artifact of a Direct API run, and never into a later generation. Runs the
 * panel against a change just created by the CLI, from captured CLI 1.15.0 output ({@code fixtures/cli/1.15.0/new-change/},
 * re-captured by {@code scripts/capture-new-change-fixtures.py}), with a stubbed Direct API call.
 */
public class FastForwardRequestPanelTest extends OpenSpecIntegrationTestBase {

    private static final String CHANGE = "add-login";
    private static final String REQUEST = "Add login with email and password, and lock the account after five failures.";

    private Path changeDir;
    private WorkflowActionPanel workflowPanel;
    private final List<String> rawPrompts = new ArrayList<>();
    private final List<String> runRequests = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Path root = Files.createTempDirectory("openspec-ff-request").toRealPath();
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), root.toString());
        changeDir = root.resolve(CHANGE);
        Files.createDirectories(changeDir);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(changeDir);

        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        ChangeService changes = mock(ChangeService.class);
        when(changes.getActiveChanges()).thenReturn(List.of(new Change(CHANGE, changeDir.toString())));
        ServiceContainerUtil.replaceService(getProject(), ChangeService.class, changes, getTestRootDisposable());

        // A just-created change: proposal ready, everything else blocked, as the CLI reports it.
        String status = fixture("status.json").replace("\"demo\"", "\"" + CHANGE + "\"");
        String instructions = fixture("instructions-proposal.json")
                .replace("/fixture/demo-project/openspec/changes/demo", changeDir.toString().replace('\\', '/'))
                .replace("\"demo\"", "\"" + CHANGE + "\"");
        ArtifactOrchestrationService orchestration = getProject().getService(ArtifactOrchestrationService.class);
        orchestration.setStatusCliForTest((p, args) -> {
            if (args.length > 0 && args[0].equals("status")) return new CliRunner.CliResult(0, status, "");
            if (args.length > 1 && args[0].equals("instructions") && args[1].equals("proposal")) {
                return new CliRunner.CliResult(0, instructions, "");
            }
            return new CliRunner.CliResult(1, "", "not fed: " + String.join(" ", args));
        });
        Disposer.register(getTestRootDisposable(), () -> {
            orchestration.setStatusCliForTest(null);
            orchestration.invalidateAllCaches();
        });

        DirectApiService api = mock(DirectApiService.class);
        when(api.generateRaw(anyString())).thenAnswer(inv -> {
            rawPrompts.add(inv.getArgument(0));
            return "# Proposal\n";
        });
        // Record the request, then fail the call: the run stops at its first artifact (the fed status never moves past
        // the proposal) and writes nothing, so no file event outlives the test.
        when(api.generate(any(), any())).thenAnswer(inv -> {
            runRequests.add(inv.getArgument(1));
            throw new AiApiException("stubbed: stop the run");
        });
        when(api.generate(any())).thenAnswer(inv -> {
            runRequests.add(null);
            throw new AiApiException("stubbed: stop the run");
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
            if (workflowPanel != null) workflowPanel.disposeForTest();
            long end = System.currentTimeMillis() + 1_500;
            while (System.currentTimeMillis() < end) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(50);
            }
            if (changeDir != null) {
                deleteTree(changeDir.getParent());
                LocalFileSystem.getInstance().refreshNioFiles(List.of(changeDir.getParent()), false, true, null);
            }
        } finally {
            super.tearDown();
        }
    }

    public void testClipboardFastForwardSendsTheRequestWithTheFirstArtifact() throws Exception {
        workflowPanel.setDeliveryModeForTest(DeliveryMode.CLIPBOARD);

        workflowPanel.startFastForwardGeneration(CHANGE, REQUEST);
        waitFor(() -> workflowPanel.lastPromptForTest() != null);

        String prompt = workflowPanel.lastPromptForTest();
        assertTrue(prompt, prompt.contains(ArtifactInstruction.REQUEST_HEADING + "\n" + REQUEST + "\n"));
        assertTrue("it's the proposal's prompt", prompt.startsWith("# Change: " + CHANGE));
        assertFalse(prompt.contains(ArtifactInstruction.NOTE_HEADING));
    }

    public void testEditorTabFastForwardSendsTheRequest() throws Exception {
        workflowPanel.setDeliveryModeForTest(DeliveryMode.EDITOR_TAB);

        workflowPanel.startFastForwardGeneration(CHANGE, REQUEST);
        waitFor(() -> workflowPanel.lastEditorTabPromptForTest() != null);

        assertTrue(workflowPanel.lastEditorTabPromptForTest().getContent().toString()
                .contains(ArtifactInstruction.REQUEST_HEADING + "\n" + REQUEST + "\n"));
    }

    public void testDirectApiFastForwardRunCarriesTheRequest() throws Exception {
        workflowPanel.setDeliveryModeForTest(DeliveryMode.DIRECT_API);

        workflowPanel.startFastForwardGeneration(CHANGE, REQUEST);
        waitFor(() -> !runRequests.isEmpty());
        dispatch();

        assertEquals(List.of(REQUEST), runRequests);
        assertFalse("the stubbed failure writes nothing", Files.exists(changeDir.resolve("proposal.md")));
    }

    public void testALaterGenerationDoesNotCarryTheRequest() throws Exception {
        workflowPanel.setDeliveryModeForTest(DeliveryMode.CLIPBOARD);
        workflowPanel.startFastForwardGeneration(CHANGE, REQUEST);
        waitFor(() -> workflowPanel.lastPromptForTest() != null);
        String fastForward = workflowPanel.lastPromptForTest();

        workflowPanel.generateNextForTest();
        waitFor(() -> workflowPanel.lastPromptForTest() != fastForward);

        String later = workflowPanel.lastPromptForTest();
        assertFalse(later, later.contains(ArtifactInstruction.REQUEST_HEADING));
        assertFalse(later.contains(REQUEST));
    }

    // --- helpers ---

    private static String fixture(String name) {
        return PanelStatusTestSupport.fixture("1.15.0/new-change/" + name);
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
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

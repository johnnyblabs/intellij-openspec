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
 * Fast-Forward and Propose send the user's description as the request for the change: into every artifact of their
 * Direct API run, and never into a later generation. (With clipboard or editor tab the actions hand off the AI tool's
 * own workflow instead; ProposeActionTest and FastForwardActionTest cover that.) Runs the
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
        orchestration.setStatusCliForTest((p, cliArgs) -> {
            String[] args = cliArgs.requested().toArray(String[]::new);
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

    public void testDirectApiFastForwardRunCarriesTheRequest() throws Exception {
        workflowPanel.setDeliveryModeForTest(DeliveryMode.DIRECT_API);

        workflowPanel.startFastForwardGeneration(CHANGE, REQUEST, null);
        waitFor(() -> !runRequests.isEmpty());
        dispatch();

        assertEquals(List.of(REQUEST), runRequests);
        assertFalse("the stubbed failure writes nothing", Files.exists(changeDir.resolve("proposal.md")));
    }

    public void testARunGeneratesWithDirectApiWhateverThePanelsSelector() throws Exception {
        // A Propose or Fast-Forward run is a Direct API run: the clipboard and editor tab hand off the AI tool's workflow
        // at the action instead, so a panel set to clipboard can't make the run a one-artifact one.
        workflowPanel.setDeliveryModeForTest(DeliveryMode.CLIPBOARD);

        workflowPanel.startFastForwardGeneration(CHANGE, REQUEST, null);
        waitFor(() -> !runRequests.isEmpty());
        dispatch();

        assertEquals(List.of(REQUEST), runRequests);
        assertNull("nothing was copied for the clipboard", workflowPanel.lastPromptForTest());
    }

    public void testTheEmptyCardOffersProposeAndNeverFastForward() {
        for (boolean configured : new boolean[]{true, false}) {
            when(getProject().getService(DirectApiService.class).isConfigured()).thenReturn(configured);
            java.util.List<String> texts = new java.util.ArrayList<>();
            collectTexts(workflowPanel.noChangesCardForTest(), texts);
            assertTrue(texts.toString(), texts.contains("Propose a change"));
            assertTrue(texts.toString(), texts.contains("Explore an idea first"));
            assertTrue("Direct API " + configured + ": " + texts, texts.stream().noneMatch(t -> t.contains("Fast-Forward")));
        }
    }

    private static void collectTexts(java.awt.Component c, java.util.List<String> out) {
        if (c instanceof javax.swing.JLabel l && l.getText() != null) out.add(l.getText());
        if (c instanceof com.intellij.ui.HyperlinkLabel h) out.add(h.getText());
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) collectTexts(child, out);
        }
    }

    public void testALaterGenerationDoesNotCarryTheRequest() throws Exception {
        workflowPanel.setDeliveryModeForTest(DeliveryMode.DIRECT_API);
        workflowPanel.startFastForwardGeneration(CHANGE, REQUEST, null);
        waitFor(() -> !runRequests.isEmpty());
        dispatch();
        assertEquals("sanity: the run carried it", List.of(REQUEST), runRequests);

        workflowPanel.setDeliveryModeForTest(DeliveryMode.CLIPBOARD);
        workflowPanel.generateNextForTest();
        waitFor(() -> workflowPanel.lastPromptForTest() != null);

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

package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.johnnyblabs.openspec.dialogs.NewChangeDialog;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * New Change... does what OpenSpec's new workflow does: it creates a change without any artifact. It is gated on
 * {@code new}, so on the core profile it is shown disabled with the profile suffix; it sits after Propose in the
 * menu and never in the toolbar.
 */
public class NewChangeActionTest extends OpenSpecIntegrationTestBase {

    private WorkflowProfileService profile;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        profile = mock(WorkflowProfileService.class);
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profile, getTestRootDisposable());
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecNewChangeAction.setDialogFactoryForTest(null);
        } finally {
            super.tearDown();
        }
    }

    private static AnAction action() {
        return ActionManager.getInstance().getAction("OpenSpec.New");
    }

    private AnActionEvent event() {
        return TestActionEvent.createTestEvent(action(),
                SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject()).build());
    }

    public void testItIsGatedOnTheNewWorkflowAndSaysWhatItDoes() {
        assertEquals("new", ((OpenSpecBaseAction) action()).getWorkflowId());
        assertTrue(action() instanceof DumbAware);
        assertEquals("Start a new change, then create its artifacts one at a time",
                action().getTemplatePresentation().getDescription());
    }

    public void testOnTheCoreProfileItIsDisabledWithTheProfileSuffix() {
        when(profile.isWorkflowEnabled(anyString())).thenAnswer(inv -> !"new".equals(inv.getArgument(0)));
        when(profile.getActiveProfileName()).thenReturn("core");
        AnActionEvent e = event();
        action().update(e);
        assertFalse(e.getPresentation().isEnabled());
        assertEquals("New Change..." + OpenSpecBaseAction.CUSTOM_PROFILE_SUFFIX, e.getPresentation().getText());
    }

    public void testEnabledWhenACustomProfileHasNew() {
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        when(profile.getActiveProfileName()).thenReturn("custom");
        AnActionEvent e = event();
        action().update(e);
        assertTrue(e.getPresentation().isEnabled());
        assertEquals("New Change...", e.getPresentation().getText());
    }

    public void testItCreatesTheChangeWithoutAnyArtifact() {
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        OpenSpecNewChangeAction.setDialogFactoryForTest(project -> new NewChangeDialog(project) {
            @Override
            public boolean showAndGet() {
                fillForTest("add-export");
                return true;
            }
        });
        action().actionPerformed(event());

        VirtualFile change = OpenSpecFileUtil.getChangesDir(getProject()).findChild("add-export");
        assertNotNull(change);
        Set<String> written = Arrays.stream(change.getChildren()).map(VirtualFile::getName).collect(Collectors.toSet());
        assertEquals("no artifact, only what `openspec new change` writes", Set.of(".openspec.yaml"), written);
    }

    public void testWithTheCliItRunsNewChangeWithTheSchemaAndNoDescription() throws Exception {
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        CliDetectionTestSupport.fakeAvailable(getProject().getService(CliDetectionService.class), "2.1.1");
        java.util.List<java.util.List<String>> runs = new java.util.concurrent.CopyOnWriteArrayList<>();
        OpenSpecNewChangeAction.setCliForTest((project, args) -> {
            runs.add(args.requested());
            return new com.johnnyblabs.openspec.util.CliRunner.CliResult(0, "", "");
        });
        try {
            OpenSpecNewChangeAction.create(getProject(), "add-export", "research-first");
            long end = System.currentTimeMillis() + 10_000;
            while (runs.isEmpty() && System.currentTimeMillis() < end) {
                com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(20);
            }
            assertEquals(1, runs.size());
            List<String> argv = runs.get(0);
            assertTrue(argv.toString(), argv.containsAll(List.of("new", "change", "add-export", "--schema", "research-first")));
            assertFalse("never --description, which writes a README", argv.contains("--description"));
            assertFalse(argv.contains("--goal"));
        } finally {
            OpenSpecNewChangeAction.setCliForTest(null);
        }
    }

    public void testACliFailureIsReported() throws Exception {
        CliDetectionTestSupport.fakeAvailable(getProject().getService(CliDetectionService.class), "2.1.1");
        java.util.List<com.intellij.notification.Notification> raised = new java.util.concurrent.CopyOnWriteArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(com.intellij.notification.Notifications.TOPIC,
                new com.intellij.notification.Notifications() {
                    @Override
                    public void notify(@org.jetbrains.annotations.NotNull com.intellij.notification.Notification n) {
                        raised.add(n);
                    }
                });
        OpenSpecNewChangeAction.setCliForTest((project, args) ->
                new com.johnnyblabs.openspec.util.CliRunner.CliResult(1, "", "Change 'add-export' already exists"));
        try {
            OpenSpecNewChangeAction.create(getProject(), "add-export", null);
            long end = System.currentTimeMillis() + 10_000;
            while (raised.isEmpty() && System.currentTimeMillis() < end) {
                com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(20);
            }
            assertFalse(raised.isEmpty());
            assertTrue(raised.get(0).getContent(), raised.get(0).getContent().contains("already exists"));
        } finally {
            OpenSpecNewChangeAction.setCliForTest(null);
        }
    }

    public void testCancellingCreatesNothing() {
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        VirtualFile changes = OpenSpecFileUtil.getChangesDir(getProject());
        List<String> before = changes == null ? List.of()
                : Arrays.stream(changes.getChildren()).map(VirtualFile::getName).toList();
        OpenSpecNewChangeAction.setDialogFactoryForTest(project -> new NewChangeDialog(project) {
            @Override
            public boolean showAndGet() {
                fillForTest("never-made");
                return false;
            }
        });
        action().actionPerformed(event());
        VirtualFile after = OpenSpecFileUtil.getChangesDir(getProject());
        assertEquals(before, after == null ? List.of() : Arrays.stream(after.getChildren()).map(VirtualFile::getName).toList());
    }
}

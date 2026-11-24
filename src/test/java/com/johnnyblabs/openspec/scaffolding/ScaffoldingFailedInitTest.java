package com.johnnyblabs.openspec.scaffolding;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliRunner;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.*;

/**
 * The setup wizard's init on a real project directory, with the CLI's run replaced by what the real CLI printed:
 * a failure is reported, the openspec/ a failing CLI already wrote is kept, and otherwise the built-in init runs.
 */
public class ScaffoldingFailedInitTest extends HeavyPlatformTestCase {

    private final List<Notification> raised = new ArrayList<>();
    private Path base;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Path.of(getProject().getBasePath());
        Files.createDirectories(base);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base);
        raised.clear();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });
    }

    private void cliAt(String version) {
        CliDetectionService detection = mock(CliDetectionService.class);
        when(detection.isAvailable()).thenReturn(true);
        when(detection.getDetectedVersion()).thenReturn(version);
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
    }

    private void write(String rel, String text) {
        try {
            Files.createDirectories(base.resolve(rel).getParent());
            Files.writeString(base.resolve(rel), text);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Runs the init as the UI does and waits for its result on the EDT. */
    private VirtualFile init(ScaffoldingService.InitRunner cli) throws Exception {
        AtomicReference<VirtualFile> done = new AtomicReference<>();
        AtomicReference<Exception> failed = new AtomicReference<>();
        ScaffoldingService service = new ScaffoldingService(getProject());
        service.setCliRunnerForTest(cli);
        service.initOpenSpec(ModalityState.nonModal(), done::set, failed::set);
        long deadline = System.currentTimeMillis() + 30_000;
        while (done.get() == null && failed.get() == null && System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        if (failed.get() != null) throw failed.get();
        assertNotNull("the init finished", done.get());
        return done.get();
    }

    /** 2.1.0 with no Git: the CLI writes openspec/ and then fails; the plugin keeps that tree and says why. */
    public void testAFailureAfterTheCliWroteOpenspecKeepsItsTree() throws Exception {
        cliAt("2.1.0");
        CliRunner.CliResult noGit = FailedInitNoticeContractTest.captured("2.1.0", "init-no-git");
        AtomicReference<com.johnnyblabs.openspec.util.CliArgs> sent = new AtomicReference<>();
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        VirtualFile dir = init((p, args) -> {
            sent.set(args);
            onEdt.set(com.intellij.openapi.application.ApplicationManager.getApplication().isDispatchThread());
            write("openspec/config.yaml", "schema: spec-driven\n# written by the CLI\n");
            return noGit;
        });

        assertEquals(base.resolve("openspec").toString(), dir.getPath());
        assertEquals("the CLI's init runs off the UI thread", Boolean.FALSE, onEdt.get());
        assertEquals("the CLI's config is kept, not replaced", "schema: spec-driven\n# written by the CLI\n",
                Files.readString(base.resolve("openspec/config.yaml")));
        assertTrue("the wizard's init asks for the project scope",
                sent.get().requested().containsAll(List.of("--scope", "project")));
        assertEquals(1, raised.size());
        String content = raised.getFirst().getContent();
        assertTrue(content, content.contains(FailedInitNoticeContractTest.reasonLine(noGit)));
        assertTrue(content, content.endsWith(ScaffoldingService.GIT_NETWORK_HINT));
    }

    /** A failure that wrote nothing: the built-in init runs, after the warning. */
    public void testAFailureThatWroteNothingRunsTheBuiltInInit() throws Exception {
        cliAt("2.1.1");
        CliRunner.CliResult refused = FailedInitNoticeContractTest.captured("2.1.1", "init-home-global");
        init((p, args) -> refused);
        assertTrue("the built-in init wrote its config", Files.isRegularFile(base.resolve("openspec/config.yaml")));
        assertEquals(1, raised.size());
        String content = raised.getFirst().getContent();
        assertTrue(content, content.contains("This is your home folder, where the global skills live."));
        assertTrue(content, content.contains("built-in setup ran instead"));
        assertFalse(content, content.contains(ScaffoldingService.GIT_NETWORK_HINT));
    }

    /** A successful CLI init raises nothing. */
    public void testASuccessfulInitIsQuiet() throws Exception {
        cliAt("2.1.1");
        init((p, args) -> {
            write("openspec/config.yaml", "schema: spec-driven\n");
            return new CliRunner.CliResult(0, "", "");
        });
        assertEquals(List.of(), raised);
    }
}

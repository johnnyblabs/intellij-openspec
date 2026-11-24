package com.johnnyblabs.openspec.scaffolding;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliArgs;
import com.johnnyblabs.openspec.util.CliRunner;
import org.jetbrains.annotations.NotNull;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
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

    /** 2.1.0 with no Git: the CLI writes openspec/ and then fails; the plugin keeps that tree and says why. */
    public void testAFailureAfterTheCliWroteOpenspecKeepsItsTree() throws Exception {
        cliAt("2.1.0");
        CliRunner.CliResult noGit = FailedInitNoticeContractTest.captured("2.1.0", "init-no-git");
        AtomicReference<CliArgs> sent = new AtomicReference<>();
        try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
            cli.when(() -> CliRunner.run(any(), any(CliArgs.class))).thenAnswer(inv -> {
                sent.set(inv.getArgument(1));
                Files.createDirectories(base.resolve("openspec/specs"));
                Files.writeString(base.resolve("openspec/config.yaml"), "schema: spec-driven\n# written by the CLI\n");
                return noGit;
            });

            VirtualFile dir = new ScaffoldingService(getProject()).initOpenSpec();

            assertEquals(base.resolve("openspec").toString(), dir.getPath());
        }
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
        try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
            cli.when(() -> CliRunner.run(any(), any(CliArgs.class))).thenReturn(refused);

            new ScaffoldingService(getProject()).initOpenSpec();
        }
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
        try (MockedStatic<CliRunner> cli = mockStatic(CliRunner.class)) {
            cli.when(() -> CliRunner.run(any(), any(CliArgs.class))).thenAnswer(inv -> {
                Files.createDirectories(base.resolve("openspec"));
                Files.writeString(base.resolve("openspec/config.yaml"), "schema: spec-driven\n");
                return new CliRunner.CliResult(0, "", "");
            });
            new ScaffoldingService(getProject()).initOpenSpec();
        }
        assertEquals(List.of(), raised);
    }
}

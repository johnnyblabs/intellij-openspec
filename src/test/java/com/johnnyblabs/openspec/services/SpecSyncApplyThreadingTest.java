package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import com.johnnyblabs.openspec.validation.BuiltInValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SpecSyncService#applySync} hands its VFS refresh to the EDT with {@code invokeAndWait} and the caller's
 * modality. It must work from a background thread (how every caller runs it) and from the EDT itself, where the
 * previous {@code invokeLater} + latch deadlocked. Uses a real temp directory, because applySync writes with java.nio,
 * and the captured 1.13.2 {@code renamed-star} main spec and archive result as the original and projected content.
 */
public class SpecSyncApplyThreadingTest extends BasePlatformTestCase {

    private Path base;
    private SpecSyncService service;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = FileUtil.createTempDirectory("spec-sync-apply", null).toPath();
        Files.createDirectories(base.resolve("openspec/specs/demo"));
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(base.toString());
        when(project.getService(BuiltInValidator.class)).thenReturn(getProject().getService(BuiltInValidator.class));
        service = new SpecSyncService(project);
    }

    private static String fixture(String path) {
        try (InputStream is = SpecSyncApplyThreadingTest.class.getResourceAsStream("/fixtures/cli/1.13.2/renamed-star/" + path)) {
            assertNotNull("missing fixture " + path, is);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SpecSyncResult result() throws IOException {
        Path mainSpec = base.resolve("openspec/specs/demo/spec.md");
        String original = fixture("openspec/specs/demo/spec.md");
        Files.writeString(mainSpec, original, StandardCharsets.UTF_8);
        return new SpecSyncResult("demo", mainSpec.toString(), original, fixture("archived-spec.md"), List.of(), List.of());
    }

    private void assertApplied(SpecSyncResult result) throws IOException {
        assertEquals(result.projectedContent(), Files.readString(Path.of(result.mainSpecPath()), StandardCharsets.UTF_8));
        assertNotNull("the IDE's file layer sees the written spec",
                LocalFileSystem.getInstance().findFileByPath(result.mainSpecPath()));
    }

    public void testApplySyncFromABackgroundThread() throws Exception {
        SpecSyncResult result = result();
        Future<List<String>> future = ApplicationManager.getApplication().executeOnPooledThread(
                () -> service.applySync(List.of(result)));
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end && !future.isDone()) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertTrue("applySync must finish", future.isDone());
        future.get();
        assertApplied(result);
    }

    public void testApplySyncOnTheEdt() throws Exception {
        // The previous invokeLater + latch blocked the EDT waiting for a runnable queued behind itself.
        assertTrue(ApplicationManager.getApplication().isDispatchThread());
        SpecSyncResult result = result();
        service.applySync(List.of(result));
        assertApplied(result);
    }
}

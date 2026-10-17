package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.util.CliRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Future;

/**
 * After the CLI archives a change (it moves the folder on disk, outside the IDE), the plugin refreshes openspec/ so the
 * tree and editors see the move without a manual refresh. Real disk, so a heavy project.
 */
public class ArchiveCliVfsRefreshTest extends HeavyPlatformTestCase {

    public void testTheCliMoveIsVisibleThroughTheVfsWhenArchiveReturns() throws Exception {
        Path base = Paths.get(getProject().getBasePath());
        Path change = base.resolve("openspec/changes/alpha");
        Files.createDirectories(change);
        Files.writeString(base.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(change.resolve("proposal.md"), "## Why\n\nx\n");
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base);
        com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true,
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base));

        ChangeService changes = getProject().getService(ChangeService.class);
        CliDetectionTestSupport.fakeAvailable(getProject().getService(CliDetectionService.class), "1.14.3");
        String captured = Files.readString(Paths.get("src/test/resources/fixtures/cli/archive-1.14.3/modified-ok/archive.json"));
        Path target = base.resolve("openspec/changes/archive/2026-10-17-alpha");
        changes.setArchiveCliForTest((project, args) -> {
            try {
                Files.createDirectories(target.getParent());
                Files.move(change, target); // what the real CLI does, behind the IDE's back
            } catch (java.io.IOException e) {
                throw new CliRunner.CliException("stub move failed", e);
            }
            return new CliRunner.CliResult(0, captured, "");
        });
        Change alpha = changes.getActiveChanges().stream().filter(c -> c.getName().equals("alpha")).findFirst().orElseThrow();

        Future<?> work = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                changes.archiveChange(alpha, true);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        long end = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < end && !work.isDone()) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        work.get();

        // No refresh here: the archive itself must have made the move visible.
        assertNotNull("the archived folder is visible through the VFS",
                LocalFileSystem.getInstance().findFileByNioFile(target.resolve("proposal.md")));
        assertTrue("no longer listed as active", changes.getActiveChanges().stream().noneMatch(c -> c.getName().equals("alpha")));
    }
}

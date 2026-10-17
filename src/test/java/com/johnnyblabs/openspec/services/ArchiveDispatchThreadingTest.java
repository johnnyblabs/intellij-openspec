package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.util.CliRunner;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** The archive runs off the UI thread and reports once, on it. */
public class ArchiveDispatchThreadingTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            getProject().getService(ChangeService.class).setArchiveCliForTest(null);
            CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        } finally {
            super.tearDown();
        }
    }

    public void testArchiveRunsInTheBackgroundAndReportsOnTheUiThread() throws Exception {
        CliDetectionTestSupport.fakeAvailable(getProject().getService(CliDetectionService.class), "1.14.3");
        String captured = Files.readString(Paths.get("src/test/resources/fixtures/cli/archive-1.14.3/modified-ok/archive.json"));
        List<Boolean> cliOnUi = new ArrayList<>();
        ChangeService changes = getProject().getService(ChangeService.class);
        changes.setArchiveCliForTest((project, args) -> {
            cliOnUi.add(ApplicationManager.getApplication().isDispatchThread());
            return new CliRunner.CliResult(0, captured, "");
        });
        myFixture.addFileToProject("openspec/changes/alpha/proposal.md", "## Why\n\nx\n");
        Change alpha = changes.getActiveChanges().stream().filter(c -> c.getName().equals("alpha")).findFirst().orElseThrow();

        List<String> done = new ArrayList<>();
        List<Boolean> doneOnUi = new ArrayList<>();
        Future<?> work = ApplicationManager.getApplication().executeOnPooledThread(() -> ArchiveDispatch.run(getProject(), alpha, true,
                outcome -> {
                    done.add(outcome.archivedAs());
                    doneOnUi.add(ApplicationManager.getApplication().isDispatchThread());
                },
                error -> fail("unexpected: " + error)));
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end && (!work.isDone() || done.isEmpty())) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        work.get();

        assertEquals("the CLI ran once, off the UI thread", List.of(false), cliOnUi);
        assertEquals(List.of("<DATE>-c1"), done);
        assertEquals("reported on the UI thread", List.of(true), doneOnUi);
    }

    public void testRefusalIsReportedWithItsFix() {
        assertEquals("other MODIFIED failed - not found Fix it.",
                ArchiveDispatch.reason(new ArchiveRefusedException("other MODIFIED failed - not found", "Fix it.")));
        assertEquals("Archive 'x' already exists.", ArchiveDispatch.reason(new ArchiveRefusedException("Archive 'x' already exists.", null)));
    }

    private static ArchiveJson.Outcome captured(String version, String captureCase) throws Exception {
        return ArchiveJson.parse(Files.readString(Paths.get("src/test/resources/fixtures/cli/" + version + "/" + captureCase + "/archive.json")), "", 0);
    }

    public void testSummaryNamesTheArchivedFolderSpecUpdatesAndWarnings() throws Exception {
        assertEquals("alpha archived as <DATE>-c1; main specs updated.",
                ArchiveDispatch.summary("alpha", captured("archive-1.14.3", "modified-ok")));
        assertEquals("alpha archived as <DATE>-c1.", ArchiveDispatch.summary("alpha", captured("archive-1.14.3", "skip-specs-ok")));
        assertEquals("alpha archived as <DATE>-c1. demo - REMOVED requirement \"Missing\" is not in the current spec; treating it as already removed.",
                ArchiveDispatch.summary("alpha", captured("archive-1.14.3", "removed-gone")));
    }
}

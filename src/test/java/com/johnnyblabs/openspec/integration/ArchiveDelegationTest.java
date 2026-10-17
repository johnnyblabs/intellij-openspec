package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ArchiveJson;
import com.johnnyblabs.openspec.services.ArchiveRefusedException;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.util.CliRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ChangeService#archiveChange} delegates to {@code openspec archive --yes --json} on CLI 1.5+, fed here from
 * captured runs ({@code fixtures/cli/archive-1.14.3/}), and archives itself on older or missing CLIs.
 */
public class ArchiveDelegationTest extends OpenSpecIntegrationTestBase {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures/cli");
    private static final Path CAPTURES = FIXTURES.resolve("archive-1.14.3");

    private final List<List<String>> calls = new ArrayList<>();
    private final List<Boolean> onUiThread = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        try {
            // The light project is shared across test classes: leave no fake CLI, stub or clock behind.
            service().setArchiveCliForTest(null);
            service().setClockForTest(null);
            CliDetectionTestSupport.fakeUnavailable(detection());
        } finally {
            super.tearDown();
        }
    }

    private ChangeService service() {
        return getProject().getService(ChangeService.class);
    }

    private CliDetectionService detection() {
        return getProject().getService(CliDetectionService.class);
    }

    /** A CLI stub answering with a captured run; it records each call's arguments and thread. */
    private void cliAnswers(String captureCase) throws Exception {
        cliAnswers(CAPTURES.resolve(captureCase));
    }

    private void cliAnswers(Path capture) throws Exception {
        String stdout = Files.readString(capture.resolve("archive.json"));
        int exit = Integer.parseInt(Files.readString(capture.resolve("exit.txt")).strip());
        service().setArchiveCliForTest((project, args) -> {
            calls.add(List.of(args));
            onUiThread.add(ApplicationManager.getApplication().isDispatchThread());
            return new CliRunner.CliResult(exit, stdout, "");
        });
    }

    private void cliMustNotRun() {
        service().setArchiveCliForTest((project, args) -> {
            throw new AssertionError("the CLI must not run here: " + List.of(args));
        });
    }

    private Change addChange(String name) {
        myFixture.addFileToProject("openspec/changes/" + name + "/proposal.md", "## Why\n\nx\n");
        return service().getActiveChanges().stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    private boolean active(String name) {
        return service().getActiveChanges().stream().anyMatch(c -> c.getName().equals(name));
    }

    public void testCli14ArchivesWithSyncAndReportsTheCliResult() throws Exception {
        CliDetectionTestSupport.fakeAvailable(detection(), "1.14.3");
        cliAnswers("modified-ok");
        Change alpha = addChange("alpha");

        ArchiveJson.Outcome outcome = offEdt(() -> service().archiveChange(alpha, true));

        assertEquals(List.of(List.of("archive", "alpha", "--yes", "--json")), calls);
        assertEquals(List.of(false), onUiThread);
        assertEquals("<DATE>-c1", outcome.archivedAs());
        assertTrue(outcome.specsUpdated());
        assertTrue("the CLI does the move; the plugin's own archive must not also run", active("alpha"));
    }

    public void testArchiveWithoutSyncingPassesSkipSpecs() throws Exception {
        CliDetectionTestSupport.fakeAvailable(detection(), "1.5.0");
        Path capture = FIXTURES.resolve("archive-1.5.0/skip-specs-ok");
        cliAnswers(capture);
        Change alpha = addChange("alpha");

        ArchiveJson.Outcome outcome = offEdt(() -> service().archiveChange(alpha, false));

        assertEquals(List.of(List.of("archive", "alpha", "--yes", "--json", "--skip-specs")), calls);
        assertEquals("the flag the real 1.5.0 run was captured with",
                Files.readString(capture.resolve("args.txt")).strip(), calls.get(0).get(4));
        assertFalse(outcome.specsUpdated());
    }

    public void testCliRefusalCarriesItsMessageAndFixAndLeavesTheChangeActive() throws Exception {
        CliDetectionTestSupport.fakeAvailable(detection(), "1.14.3");
        cliAnswers("two-caps-one-refused");
        Change alpha = addChange("alpha");

        try {
            offEdt(() -> service().archiveChange(alpha, true));
            fail("the CLI refused");
        } catch (ArchiveRefusedException e) {
            assertEquals("other MODIFIED failed for header \"### Requirement: Missing\" - not found", e.getMessage());
            assertEquals("Fix the change delta specs and rerun. No files were changed.", e.fix());
        }
        assertTrue(active("alpha"));
    }

    public void testCli14WithoutJsonFallsBackToThePluginArchive() throws Exception {
        CliDetectionTestSupport.fakeAvailable(detection(), "1.4.1");
        cliMustNotRun();
        service().setClockForTest(java.time.Clock.fixed(java.time.Instant.parse("2026-10-17T12:00:00Z"), java.time.ZoneOffset.UTC));
        Change alpha = addChange("alpha");

        ArchiveJson.Outcome outcome = offEdt(() -> service().archiveChange(alpha, true));

        assertEquals("2026-10-17-alpha", outcome.archivedAs());
        assertFalse(active("alpha"));
        assertNotNull(getChangesDir().findFileByRelativePath("archive/2026-10-17-alpha"));
    }

    public void testNoCliFallsBackAndTheFallbackRefusesWhatOpenSpecRefuses() throws Exception {
        CliDetectionTestSupport.fakeUnavailable(detection());
        cliMustNotRun();
        myFixture.addFileToProject("openspec/specs/ghost/spec.md",
                "# ghost Specification\n\n## Purpose\nA spec without the requirement the delta modifies, used here.\n\n## Requirements\n");
        myFixture.addFileToProject("openspec/changes/orphan/specs/ghost/spec.md",
                "## MODIFIED Requirements\n\n### Requirement: Gone\nThe system SHALL x.\n\n#### Scenario: S\n- **WHEN** a\n- **THEN** b\n");
        Change orphan = addChange("orphan");

        try {
            offEdt(() -> service().archiveChange(orphan, true));
            fail("the plugin's archive refuses a MODIFIED with no target");
        } catch (ArchiveRefusedException e) {
            assertEquals("ghost MODIFIED failed for header \"### Requirement: Gone\" - not found", e.getMessage());
        }
        assertTrue(active("orphan"));
    }

    public void testArchiveRefusesToRunOnTheUiThread() {
        CliDetectionTestSupport.fakeUnavailable(detection());
        Change alpha = addChange("alpha");
        try {
            service().archiveChange(alpha, false);
            fail("archiveChange must not run on the UI thread");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("must not run on the UI thread"));
        } catch (java.io.IOException unexpected) {
            fail("refused for the wrong reason: " + unexpected);
        }
        assertTrue("still active", active("alpha"));
    }
}

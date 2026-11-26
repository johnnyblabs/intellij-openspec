package com.johnnyblabs.openspec.services;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import com.johnnyblabs.openspec.util.PluginVersions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * A new main spec follows the release the plugin follows, on the captured archive runs
 * ({@code fixtures/cli/archive-<version>/}): the merge given a release, and Sync Specs given a detected CLI or none.
 */
public class SpecMergeReleaseTest extends BasePlatformTestCase {

    private static final Path CLI = Paths.get("src/test/resources/fixtures/cli");

    private CliDetectionService detection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        detection = new CliDetectionService(getProject());
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
    }

    private static String captured(String release, String caseName, String file) throws IOException {
        return Files.readString(CLI.resolve("archive-" + release).resolve(caseName).resolve(file)).replace("\r\n", "\n");
    }

    private static String merged(String caseName, String release) throws IOException {
        SpecMerge.Result r = SpecMerge.merge("newcap", "c1", captured("2.1.1", caseName, "newcap/delta.md"), null,
                "openspec/specs/newcap/spec.md", release);
        assertNull(r.refusal(), r.refusal());
        return r.projected();
    }

    public void testTheSameDeltaGetsEachReleasesLayout() throws IOException {
        String before = captured("2.1.0", "new-cap-purpose", "newcap/main-after.md");
        String after = captured("2.1.1", "new-cap-purpose", "newcap/main-after.md");
        assertFalse("the captures differ", before.equals(after));
        assertEquals(before, merged("new-cap-purpose", "2.1.0"));
        assertEquals(after, merged("new-cap-purpose", "2.1.1"));
        assertEquals("a later release keeps the 2.1.1 layout", after, merged("new-cap-purpose", "2.2.0"));
    }

    /** A release that can't be read, or none, merges as the releases before 2.1.1 do. */
    public void testNoOrAnUnreadableReleaseMergesAsBefore() throws IOException {
        String before = captured("2.1.0", "new-cap-purpose-bom", "newcap/main-after.md");
        for (String release : new String[]{null, "garbage", ""}) {
            assertEquals(String.valueOf(release), before, merged("new-cap-purpose-bom", release));
        }
    }

    /** An existing main spec merges the same on both sides of the boundary: only a new spec's layout changed. */
    public void testAnExistingMainSpecIsUnchangedAcrossTheBoundary() throws IOException {
        List<Path> cases;
        try (Stream<Path> dirs = Files.list(CLI.resolve("archive-2.1.1"))) {
            // A run with extra flags (--skip-specs) didn't merge, so its after is no merge's output.
            cases = dirs.filter(d -> !Files.exists(d.resolve("args.txt")))
                    .filter(d -> Files.isDirectory(d.resolve("demo")) && Files.exists(d.resolve("demo/main-before.md"))
                    && Files.exists(d.resolve("demo/main-after.md"))).sorted().toList();
        }
        assertTrue("sanity: existing-spec cases", cases.size() > 10);
        for (Path c : cases) {
            String delta = Files.readString(c.resolve("demo/delta.md"));
            String main = Files.readString(c.resolve("demo/main-before.md"));
            String old = SpecMerge.merge("demo", "c1", delta, main, "openspec/specs/demo/spec.md", "2.1.0").projected();
            assertEquals(c.getFileName().toString(), old,
                    SpecMerge.merge("demo", "c1", delta, main, "openspec/specs/demo/spec.md", "2.1.1").projected());
            assertEquals(c.getFileName().toString(), Files.readString(c.resolve("demo/main-after.md")), old);
        }
    }

    /** Sync Specs merges as the detected CLI's archive does, and as the plugin's target release with none. */
    public void testSyncFollowsTheDetectedCliOrTheTarget() throws IOException {
        Path base = Path.of(getProject().getBasePath());
        Path deltaFile = base.resolve("openspec/changes/c1/specs/newcap/spec.md");
        Files.createDirectories(deltaFile.getParent());
        Files.writeString(deltaFile, captured("2.1.1", "new-cap-purpose", "newcap/delta.md"));
        LocalFileSystem.getInstance().refreshNioFiles(List.of(base), false, true, null);
        try {
            SpecSyncService sync = new SpecSyncService(getProject());
            CliDetectionTestSupport.fakeAvailable(detection, "2.1.1");
            assertEquals(captured("2.1.1", "new-cap-purpose", "newcap/main-after.md"), projected(sync));
            CliDetectionTestSupport.fakeAvailable(detection, "2.1.0");
            assertEquals(captured("2.1.0", "new-cap-purpose", "newcap/main-after.md"), projected(sync));

            CliDetectionTestSupport.fakeUnavailable(detection);
            // With no CLI, sync follows the target release, which is 2.1.1 or later: the 2.1.1 layout.
            assertTrue("the target predates 2.1.1: compare with archive-2.1.0/new-cap-purpose instead",
                    com.johnnyblabs.openspec.validation.ValidationRelease.has(PluginVersions.targetCliVersion(),
                            com.johnnyblabs.openspec.validation.ValidationRelease.NEW_SPEC_LAYOUT));
            assertEquals(captured("2.1.1", "new-cap-purpose", "newcap/main-after.md"), projected(sync));
        } finally {
            FileUtil.delete(base.resolve("openspec").toFile());
        }
    }

    private static String projected(SpecSyncService sync) {
        List<SpecSyncResult> results = sync.computeSync("c1");
        assertEquals(1, results.size());
        assertTrue(results.get(0).refusals().toString(), results.get(0).refusals().isEmpty());
        return results.get(0).projectedContent().replace("\r\n", "\n");
    }
}

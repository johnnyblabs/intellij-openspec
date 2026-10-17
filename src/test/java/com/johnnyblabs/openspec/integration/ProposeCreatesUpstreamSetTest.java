package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.scaffolding.ScaffoldingService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The built-in Propose creates what {@code openspec new change} creates plus the proposal, and nothing the CLI would
 * read as a finished artifact. Its output matches the change {@code fixtures/cli/1.14.3/status-propose-upstream-set/}
 * was captured from, where the real CLI reports proposal done, design and specs ready, and tasks blocked.
 */
public class ProposeCreatesUpstreamSetTest extends OpenSpecIntegrationTestBase {

    private static final Path CAPTURE = Paths.get("src/test/resources/fixtures/cli/1.14.3/status-propose-upstream-set");

    public void testProposeWritesOnlyTheUpstreamSetPlusTheProposal() throws Exception {
        ScaffoldingService scaffolding = getProject().getService(ScaffoldingService.class);

        VirtualFile change = scaffolding.createChange("alpha", "Add a small capability for the walkthrough.", "- Add it.");

        Set<String> created = Arrays.stream(change.getChildren()).map(VirtualFile::getName).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(".openspec.yaml", "proposal.md", "specs"), created);
        assertEquals("specs/ starts empty", 0, change.findChild("specs").getChildren().length);
        assertTrue(new String(change.findChild("proposal.md").contentsToByteArray()).contains("Add a small capability for the walkthrough."));

        Set<String> captured;
        try (Stream<Path> files = Files.list(CAPTURE.resolve("change"))) {
            captured = files.map(p -> p.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals("the same set the captured CLI status was taken on", captured, created);
        String status = Files.readString(CAPTURE.resolve("status.json"));
        assertTrue("the CLI reads it as incomplete: " + status, status.contains("\"isComplete\": false"));
    }
}

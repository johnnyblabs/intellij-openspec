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

/**
 * Without the CLI, Propose and New Change create a change as {@code openspec new change} does: only
 * {@code .openspec.yaml}, no artifact. The CLI reports such a change, captured just after {@code new change}
 * ({@code fixtures/cli/1.15.0/new-change/status.json}), with nothing done and the proposal ready.
 */
public class EmptyChangeMatchesNewChangeTest extends OpenSpecIntegrationTestBase {

    private static final Path CAPTURE = Paths.get("src/test/resources/fixtures/cli/1.15.0/new-change/status.json");

    public void testItWritesOnlyTheChangeMetadata() throws Exception {
        ScaffoldingService scaffolding = getProject().getService(ScaffoldingService.class);

        VirtualFile change = scaffolding.createEmptyChange("alpha", null);

        Set<String> created = Arrays.stream(change.getChildren()).map(VirtualFile::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(".openspec.yaml"), created);
        String yaml = new String(change.findChild(".openspec.yaml").contentsToByteArray());
        assertTrue(yaml, yaml.contains("schema: spec-driven"));
        assertTrue(yaml, yaml.contains("created: "));

        String status = Files.readString(CAPTURE);
        assertFalse("the CLI reads a just-created change as having nothing done: " + status,
                status.contains("\"status\": \"done\""));
        assertTrue(status.contains("\"status\": \"ready\""));
    }

    public void testItUsesTheChosenSchema() throws Exception {
        ScaffoldingService scaffolding = getProject().getService(ScaffoldingService.class);
        VirtualFile change = scaffolding.createEmptyChange("beta", "research-first");
        String yaml = new String(change.findChild(".openspec.yaml").contentsToByteArray());
        assertTrue(yaml, yaml.contains("schema: research-first"));
    }
}

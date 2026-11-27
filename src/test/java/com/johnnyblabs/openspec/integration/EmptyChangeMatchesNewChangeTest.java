package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.scaffolding.ScaffoldingService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Without the CLI, Propose and New Change create a change as {@code openspec new change} does. The oracle is what the
 * real CLI left in the change folder, captured for 1.17.0 and 2.1.1 by {@code scripts/capture-new-change-fixtures.py}
 * ({@code new-change-past-apply/change-files.txt} and {@code openspec-yaml-keys.txt}).
 */
public class EmptyChangeMatchesNewChangeTest extends OpenSpecIntegrationTestBase {

    private static List<String> captured(String version, String name) throws Exception {
        return Files.readAllLines(Path.of("src/test/resources/fixtures/cli", version, "new-change-past-apply", name))
                .stream().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    private static Set<String> keys(String yaml) {
        return yaml.lines().filter(l -> !l.isBlank() && !l.startsWith(" ") && !l.startsWith("#") && l.contains(":"))
                .map(l -> l.substring(0, l.indexOf(':'))).collect(Collectors.toCollection(TreeSet::new));
    }

    public void testItWritesWhatNewChangeWrites() throws Exception {
        ScaffoldingService scaffolding = getProject().getService(ScaffoldingService.class);
        VirtualFile change = scaffolding.createEmptyChange("alpha", null);

        Set<String> created = Arrays.stream(change.getChildren()).map(VirtualFile::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        String yaml = new String(change.findChild(".openspec.yaml").contentsToByteArray());
        for (String version : List.of("1.17.0", "2.1.1", "2.2.0")) {
            assertEquals("the files CLI " + version + " left", new TreeSet<>(captured(version, "change-files.txt")), created);
            assertEquals("the .openspec.yaml keys CLI " + version + " wrote",
                    new TreeSet<>(captured(version, "openspec-yaml-keys.txt")), keys(yaml));
        }
        assertTrue(yaml, yaml.contains("schema: spec-driven"));
    }

    public void testItUsesTheChosenSchema() throws Exception {
        ScaffoldingService scaffolding = getProject().getService(ScaffoldingService.class);
        VirtualFile change = scaffolding.createEmptyChange("beta", "research-first");
        String yaml = new String(change.findChild(".openspec.yaml").contentsToByteArray());
        assertTrue(yaml, yaml.contains("schema: research-first"));
    }
}

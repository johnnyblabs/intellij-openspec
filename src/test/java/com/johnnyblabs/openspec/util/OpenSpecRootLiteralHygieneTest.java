package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Every OpenSpec root path comes from the resolver ({@code OpenSpecFileUtil}, {@code com.johnnyblabs.openspec.root}),
 * so a project whose root is a store or an ancestor reads and writes the right tree. A literal that is an
 * {@code openspec/} path ({@code basePath + "/openspec/changes/"}, {@code Path.of(base, "openspec", ...)}) would
 * hard-wire {@code <project>/openspec}.
 *
 * <p>Literals that name something else, or match any {@code openspec/} tree (a store's included), are exempt by file,
 * each with its reason. A constant built from pieces ({@code DIR + "/changes"}) gets past this scan; the store-project
 * surface tests are the behavioral catch.
 */
class OpenSpecRootLiteralHygieneTest {

    static final Path MAIN = CliFlagLiteralHygieneTest.MAIN;
    static final String RESOLVER_PACKAGE = "com/johnnyblabs/openspec/root/";

    /** A literal that is an {@code openspec} directory path, with or without a leading slash. */
    static final Pattern ROOT_PATH = Pattern.compile("^/?openspec(/\\S*)?$");

    static final Map<String, String> EXEMPT = Map.ofEntries(
            entry("com/johnnyblabs/openspec/coordination/CoordinationPaths.java:\"openspec\"",
                    "the CLI's global data directory name"),
            entry("com/johnnyblabs/openspec/util/CliRunner.java:\"openspec\"", "the CLI binary name"),
            entry("com/johnnyblabs/openspec/services/CliDetectionService.java:\"openspec\"", "the CLI binary name"),
            entry("com/johnnyblabs/openspec/services/UpdatePreviewService.java:\"openspec\"", "a tool id"),
            entry("com/johnnyblabs/openspec/services/AiToolDetectionService.java:\"openspec\"",
                    "part of a skill file name"),
            entry("com/johnnyblabs/openspec/scaffolding/ScaffoldingService.java:\"/openspec\"",
                    "init writes the project's own openspec/"),
            entry("com/johnnyblabs/openspec/scaffolding/ScaffoldingService.java:\"openspec\"",
                    "init writes the project's own openspec/"),
            entry("com/johnnyblabs/openspec/dialogs/SkillsScopeLine.java:\"openspec/config.yaml\"",
                    "skillsScope is read from the project's own config, as upstream's init does"),
            entry("com/johnnyblabs/openspec/services/ReleasedSkillFingerprints.java:"
                    + "\"/openspec/released-skill-fingerprints.txt\"", "a bundled resource"),
            entry("com/johnnyblabs/openspec/services/ReleasedSkillFingerprints.java:"
                    + "\"/openspec/router-reference-fingerprints.txt\"", "a bundled resource"),
            entry("com/johnnyblabs/openspec/services/MachineToolMarkers.java:\"/openspec/machine-tool-markers.txt\"",
                    "a bundled resource"),
            entry("com/johnnyblabs/openspec/filetype/OpenSpecIconProvider.java:\"/openspec/changes/\"",
                    "matches any openspec/ tree, a store's included"),
            entry("com/johnnyblabs/openspec/filetype/OpenSpecIconProvider.java:\"/openspec/specs/\"",
                    "matches any openspec/ tree, a store's included"),
            entry("com/johnnyblabs/openspec/actions/OpenSpecValidateFromProjectViewAction.java:\"/openspec/\"",
                    "a cheap pre-filter before the resolver's check"),
            entry("com/johnnyblabs/openspec/actions/ValidationConsoleFormatter.java:\"openspec/\"",
                    "trims a displayed path at any openspec/ tree"),
            entry("com/johnnyblabs/openspec/actions/OpenSpecValidateAction.java:\"openspec\"",
                    "display fallback when the project has no base path"),
            entry("com/johnnyblabs/openspec/toolwindow/OpenSpecConsolePanel.java:\"openspec/\"",
                    "recognizes a root-relative path, then resolves it against the resolved root"),
            entry("com/johnnyblabs/openspec/toolwindow/SpecPreviewRenderer.java:\"openspec/config.yaml\"",
                    "a root-relative display path"),
            entry("com/johnnyblabs/openspec/services/SpecMerge.java:\"openspec/specs/\"",
                    "a root-relative display path, as the CLI prints it"),
            entry("com/johnnyblabs/openspec/services/AiToolRegistry.java:\"openspec\"",
                    "CLI 2.2's router skill folder name, not a root path"),
            entry("com/johnnyblabs/openspec/validation/ConfigValidationInspection.java:\"openspec\"",
                    "the config's parent directory name, true for a store's config too"));

    @Test
    void noRootPathIsBuiltOutsideTheResolver() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path f : CliFlagLiteralHygieneTest.javaFiles()) {
            if (rel(f).startsWith(RESOLVER_PACKAGE)) continue;
            for (CliFlagLiteralHygieneTest.Literal l : CliFlagLiteralHygieneTest.literals(f)) {
                if (ROOT_PATH.matcher(l.text()).matches() && !EXEMPT.containsKey(l.key())) {
                    found.add(rel(f) + ":" + l.line() + " \"" + l.text() + "\"");
                }
            }
        }
        assertEquals(List.of(), found, "get OpenSpec root paths from OpenSpecFileUtil (openspecPath, getOpenSpecRoot)");
    }

    @Test
    void everyExemptionStillExists() throws IOException {
        List<String> keys = new ArrayList<>();
        for (Path f : CliFlagLiteralHygieneTest.javaFiles()) {
            for (CliFlagLiteralHygieneTest.Literal l : CliFlagLiteralHygieneTest.literals(f)) keys.add(l.key());
        }
        for (String exempt : EXEMPT.keySet()) assertTrue(keys.contains(exempt), "stale exemption " + exempt);
    }

    @Test
    void thePatternCatchesHandBuiltRootPaths() {
        for (String built : List.of("/openspec/changes/", "openspec/specs/", "/openspec", "openspec",
                "openspec/config.yaml")) {
            assertTrue(ROOT_PATH.matcher(built).matches(), built);
        }
        for (String other : List.of("/openspec-", "openspec-target.properties", "no main spec at openspec/specs/",
                "Run openspec init")) {
            assertFalse(ROOT_PATH.matcher(other).matches(), other);
        }
    }

    private static String rel(Path f) {
        return MAIN.relativize(f).toString().replace('\\', '/');
    }
}

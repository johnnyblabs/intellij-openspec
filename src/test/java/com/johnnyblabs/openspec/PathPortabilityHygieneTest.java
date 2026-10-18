package com.johnnyblabs.openspec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Catches, on any OS, the two ways a test has passed on Linux and macOS but failed on Windows: comparing a
 * {@code File}/{@code Path} as a string with {@code /} separators, and reading a captured fixture whose line endings
 * git converted on checkout.
 */
class PathPortabilityHygieneTest {

    private static final Path TEST_SOURCES = Path.of("src/test/java");

    // assertEquals("<literal with a />", <actual>): the actual side is checked for path APIs below.
    private static final Pattern LITERAL_ASSERT =
            Pattern.compile("assert(?:Equals|That)\\(\\s*\"([^\"\\\\]*/[^\"\\\\]*)\"\\s*,([^;]*?)\\)\\s*;");

    // Calls whose result is a File or Path, or that render one with the platform separator.
    private static final Pattern PATH_API = Pattern.compile(
            "getWorkDirectory\\(\\)|getAbsolutePath\\(\\)|getCanonicalPath\\(\\)|toAbsolutePath\\(\\)|toRealPath\\("
                    + "|getParentFile\\(\\)|\\.toFile\\(\\)|\\.toPath\\(\\)|Path\\.of\\(|Paths\\.get\\(|new File\\("
                    + "|\\.relativize\\(|\\.resolve\\(|\\.getFileName\\(\\)|\\.getParent\\(\\)|\\.getPath\\(\\)");

    // "<file>: <literal>" pairs whose actual side is a String getter, not a File or Path, so it reads the same on Windows.
    private static final Set<String> STRING_PATHS = Set.of(
            "model/ModelTest.java: /project/openspec/changes/my-feature" // Change.getPath() returns the String it was given
    );

    private static final String FIXTURES_RULE = "src/test/resources/fixtures/** -text";

    @Test
    void testsCompareFilesAndPathsAsObjectsNotSlashStrings() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String rel = TEST_SOURCES.relativize(file).toString().replace('\\', '/');
                String name = rel.substring(rel.indexOf("openspec/") + "openspec/".length());
                if (name.equals("PathPortabilityHygieneTest.java")) continue;
                for (String literal : slashLiteralPathAsserts(Files.readString(file))) {
                    if (!STRING_PATHS.contains(name + ": " + literal)) offenders.add(name + ": \"" + literal + "\"");
                }
            }
        }
        assertEquals(List.of(), offenders, "Compare File/Path objects (e.g. new File(\"/work\")), not strings with '/': "
                + "Windows renders them with '\\'. If the actual value is a plain String, add it to STRING_PATHS.");
    }

    @Test
    void theScannerFlagsTheCliRunnerWindowsFailure() {
        // The assertion that failed on Windows as "expected: </work> but was: <\work>".
        assertEquals(List.of("/work"), slashLiteralPathAsserts(
                "assertEquals(\"/work\", cmd.getWorkDirectory().getPath());"));
        assertEquals(List.of("/a/b"), slashLiteralPathAsserts(
                "assertEquals(\"/a/b\",\n        root.resolve(\"b\")\n            .toString());"));
        // Its fix, and an assertion with no path API on the actual side, both pass.
        assertEquals(List.of(), slashLiteralPathAsserts(
                "assertEquals(new java.io.File(\"/work\"), cmd.getWorkDirectory());"));
        assertEquals(List.of(), slashLiteralPathAsserts("assertEquals(\"a/b\", json.get(\"path\").getAsString());"));
    }

    @Test
    void capturedFixturesAreCheckedOutByteForByte() throws IOException {
        assertTrue(protectsFixtures(Files.readString(Path.of(".gitattributes"))),
                ".gitattributes must keep '" + FIXTURES_RULE + "': captured CLI output is compared as captured, "
                        + "and git on Windows would otherwise convert it to CRLF on checkout");
    }

    @Test
    void theRuleCheckFlagsTheArchiveFixtureWindowsFailure() {
        // Before the archive-fixture fix, only the task-grammar captures were protected.
        assertFalse(protectsFixtures("src/test/resources/fixtures/cli/1.13.2/task-grammar/** -text\n"));
        // That fix protected one more directory; captures elsewhere could still be converted.
        assertFalse(protectsFixtures("src/test/resources/fixtures/cli/archive-*/** -text\n"));
        assertFalse(protectsFixtures("# " + FIXTURES_RULE + "\n"));
        assertTrue(protectsFixtures("# captures\n" + FIXTURES_RULE + "\n"));
        assertTrue(protectsFixtures("src/test/resources/fixtures/**   -text   \n"));
    }

    /** The slash literals of {@code assertEquals("…/…", actual)} calls whose actual side uses a File or Path API. */
    static List<String> slashLiteralPathAsserts(String source) {
        List<String> found = new ArrayList<>();
        Matcher m = LITERAL_ASSERT.matcher(stripComments(source));
        while (m.find()) {
            if (PATH_API.matcher(m.group(2)).find()) found.add(m.group(1));
        }
        return found;
    }

    /** Whether {@code gitattributes} has an uncommented rule that stops git converting any fixture's line endings. */
    static boolean protectsFixtures(String gitattributes) {
        return gitattributes.lines()
                .map(String::strip)
                .filter(line -> !line.startsWith("#"))
                .map(line -> line.replaceAll("\\s+", " "))
                .anyMatch(FIXTURES_RULE::equals);
    }

    private static String stripComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}

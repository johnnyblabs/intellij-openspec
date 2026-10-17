package com.johnnyblabs.openspec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the migration off platform APIs deprecated in newer IDEs whose replacements exist at the 2024.2 floor:
 * {@code ReadAction.compute} (use {@code Application.runReadAction(ThrowableComputable)}) and the two-argument
 * {@code FileEditorManager.openFile(file, boolean)} (use {@code new OpenFileDescriptor(project, file).navigate(true)}).
 * {@code build}/{@code test} compile against 2024.2, where neither is deprecated, so only this scan (and
 * {@code verifyPlugin} against the newer IDEs) can catch a regression. For {@code openFile} this scan is the only CI
 * check until 2026.3 joins the verified set: no stable IDE in it deprecates that method yet.
 */
class DeprecatedPlatformApiHygieneTest {

    /** A call, not a mention: {@code ReadAction.compute(} — javadoc may name the API without calling it. */
    static final Pattern READ_ACTION_COMPUTE = Pattern.compile("ReadAction\\s*\\.\\s*compute\\s*\\(");
    /**
     * The deprecated two-argument {@code openFile(<file>, true|false)} call, matched on its argument shape rather than
     * its receiver so a {@code FileEditorManager} held in a local is caught too. The plugin's own one-argument
     * {@code openFile(String)} helper does not match.
     */
    static final Pattern OPEN_FILE_WITH_FOCUS_FLAG =
            Pattern.compile("\\.\\s*openFile\\s*\\(\\s*\\w+\\s*,\\s*(?:true|false)\\s*\\)");

    private static List<Path> mainJavaSources() throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/java/com/johnnyblabs/openspec"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "could not locate the repository root from " + Paths.get("").toAbsolutePath());
        try (Stream<Path> walk = Files.walk(dir.resolve("src/main/java"))) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static List<String> offenders(List<Path> files, Pattern pattern) {
        return files.stream().filter(p -> {
            try {
                return pattern.matcher(Files.readString(p)).find();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).map(Path::toString).toList();
    }

    @Test
    void noMainSourceCallsReadActionCompute() throws IOException {
        List<Path> javaFiles = mainJavaSources();
        // Anti-vacuity: the scan must actually see the plugin's sources, including a migrated site.
        assertTrue(javaFiles.size() > 50, "expected to scan the plugin's sources, found " + javaFiles.size());
        assertTrue(javaFiles.stream().anyMatch(p -> p.endsWith("services/SpecParsingService.java")),
                "a migrated read-action site was not scanned");

        List<String> offenders = offenders(javaFiles, READ_ACTION_COMPUTE);
        assertTrue(offenders.isEmpty(),
                "use ApplicationManager.getApplication().runReadAction((ThrowableComputable<T, E>) ...) instead of the "
                        + "deprecated ReadAction.compute: " + offenders);
    }

    @Test
    void theReadActionPatternMatchesACallButNotAMention() {
        assertTrue(READ_ACTION_COMPUTE.matcher("content = ReadAction.compute(() -> read());").find());
        assertTrue(READ_ACTION_COMPUTE.matcher("ReadAction\n        .compute(parsing::parseAllSpecs)").find());
        assertFalse(READ_ACTION_COMPUTE.matcher("unlike the deprecated {@code ReadAction.compute}").find());
        assertFalse(READ_ACTION_COMPUTE.matcher("ReadAction.computeBlocking(() -> read())").find(),
                "computeBlocking is a different method");
    }

    @Test
    void noMainSourceCallsTheDeprecatedTwoArgumentOpenFile() throws IOException {
        List<Path> javaFiles = mainJavaSources();
        assertTrue(javaFiles.size() > 50, "expected to scan the plugin's sources, found " + javaFiles.size());
        assertTrue(javaFiles.stream().anyMatch(p -> p.endsWith("toolwindow/WorkflowActionPanel.java")),
                "a migrated file-opening site was not scanned");

        List<String> offenders = offenders(javaFiles, OPEN_FILE_WITH_FOCUS_FLAG);
        assertTrue(offenders.isEmpty(),
                "use new OpenFileDescriptor(project, file).navigate(true) instead of the deprecated "
                        + "FileEditorManager.openFile(file, boolean): " + offenders);
    }

    @Test
    void theOpenFilePatternMatchesTheDeprecatedCallShapesOnly() {
        assertTrue(OPEN_FILE_WITH_FOCUS_FLAG.matcher("FileEditorManager.getInstance(project).openFile(vf, true);").find());
        assertTrue(OPEN_FILE_WITH_FOCUS_FLAG.matcher(
                "FileEditorManager.getInstance(project)\n        .openFile(vf, true);").find(), "split across lines");
        assertTrue(OPEN_FILE_WITH_FOCUS_FLAG.matcher("editors.openFile(scratch, false);").find(), "a local receiver");
        assertFalse(OPEN_FILE_WITH_FOCUS_FLAG.matcher("openFile(relativePath);").find(),
                "the plugin's own one-argument helper must not match");
        assertFalse(OPEN_FILE_WITH_FOCUS_FLAG.matcher("private void openFile(String relativePath) {").find());
        assertFalse(OPEN_FILE_WITH_FOCUS_FLAG.matcher("new OpenFileDescriptor(project, vf).navigate(true);").find());
    }
}

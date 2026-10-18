package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ArchiveJson} against every captured {@code openspec archive --json} run (1.14.3, the 1.5.0 floor, 1.4.1's rejection). */
class ArchiveJsonContractTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures/cli");

    static List<Path> cases() throws IOException {
        List<Path> cases = new ArrayList<>();
        for (String v : List.of("archive-1.14.3", "archive-1.5.0", "archive-1.4.1")) {
            try (Stream<Path> dirs = Files.list(FIXTURES.resolve(v))) {
                dirs.filter(Files::isDirectory).sorted().forEach(cases::add);
            }
        }
        return cases;
    }

    @Test
    void thereAreCasesToCheck() throws IOException {
        assertTrue(cases().size() >= 60, "found " + cases().size());
    }

    private static String readOrEmpty(Path p) throws IOException {
        return Files.exists(p) ? Files.readString(p) : "";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void readsEveryCapturedRun(Path dir) throws IOException {
        int exit = Integer.parseInt(Files.readString(dir.resolve("exit.txt")).strip());
        String stdout = readOrEmpty(dir.resolve("archive.json"));
        String stderr = readOrEmpty(dir.resolve("stderr.txt"));
        JsonObject json = stdout.isBlank() ? null : JsonParser.parseString(stdout).getAsJsonObject();
        if (exit == 0) {
            ArchiveJson.Outcome o = ArchiveJson.parse(stdout, stderr, exit);
            JsonObject archive = json.getAsJsonObject("archive");
            assertEquals(archive.get("archivedAs").getAsString(), o.archivedAs(), dir.toString());
            assertEquals(archive.get("specsUpdated").getAsBoolean(), o.specsUpdated(), dir.toString());
            List<String> warnings = new ArrayList<>();
            if (archive.has("warnings")) archive.getAsJsonArray("warnings").forEach(w -> warnings.add(w.getAsString()));
            assertEquals(warnings, o.warnings(), dir.toString());
        } else {
            ArchiveRefusedException e = assertThrows(ArchiveRefusedException.class, () -> ArchiveJson.parse(stdout, stderr, exit));
            if (json != null) {
                JsonObject status = json.getAsJsonArray("status").get(0).getAsJsonObject();
                assertEquals(status.get("message").getAsString(), e.getMessage(), dir.toString());
                assertEquals(status.has("fix") ? status.get("fix").getAsString() : null, e.fix(), dir.toString());
            } else {
                assertEquals(stderr.strip(), e.getMessage(), dir.toString());
            }
        }
    }

    @Test
    void pinnedValues() throws IOException {
        Path base = FIXTURES.resolve("archive-1.14.3");
        ArchiveRefusedException refused = assertThrows(ArchiveRefusedException.class, () -> ArchiveJson.parse(
                Files.readString(base.resolve("two-caps-one-refused/archive.json")), "", 1));
        assertEquals("other MODIFIED failed for header \"### Requirement: Missing\" - not found", refused.getMessage());
        assertEquals("Fix the change delta specs and rerun. No files were changed.", refused.fix());
        assertNull(assertThrows(ArchiveRefusedException.class, () -> ArchiveJson.parse(
                Files.readString(base.resolve("existing-target/archive.json")), "", 1)).fix(), "existing-target carries no fix");

        assertEquals("2026-01-02-c1", ArchiveJson.parse(Files.readString(base.resolve("already-dated-name/archive.json")), "", 0).archivedAs());
        assertEquals("<DATE>-c1", ArchiveJson.parse(Files.readString(base.resolve("modified-ok/archive.json")), "", 0).archivedAs());
        ArchiveJson.Outcome gone = ArchiveJson.parse(Files.readString(base.resolve("removed-gone/archive.json")), "", 0);
        assertEquals(List.of("demo - REMOVED requirement \"Missing\" is not in the current spec; treating it as already removed."), gone.warnings());
        assertFalse(ArchiveJson.parse(Files.readString(base.resolve("skip-specs-ok/archive.json")), "", 0).specsUpdated());
        assertEquals("error: unknown option '--json'", assertThrows(ArchiveRefusedException.class, () -> ArchiveJson.parse("",
                Files.readString(FIXTURES.resolve("archive-1.4.1/unknown-json/stderr.txt")), 1)).getMessage());
    }

    @Test
    void neverReadsJunkOrAContradictionAsSuccess() throws IOException {
        assertThrows(ArchiveRefusedException.class, () -> ArchiveJson.parse("not json", "", 0));
        assertThrows(ArchiveRefusedException.class, () -> ArchiveJson.parse("{\"archive\":null}", "", 0));
        String success = Files.readString(FIXTURES.resolve("archive-1.14.3/modified-ok/archive.json"));
        assertThrows(ArchiveRefusedException.class, () -> ArchiveJson.parse(success, "", 1), "a non-zero exit is a refusal whatever stdout says");
        assertEquals("openspec archive exited with code 2",
                assertThrows(ArchiveRefusedException.class, () -> ArchiveJson.parse("", "", 2)).getMessage());
    }

    @Test
    void aCompletedArchiveWithTextBeforeItsJsonIsACompletion() throws IOException {
        // Used to read as "reported success without an archive result", after the archive had happened on disk.
        String success = Files.readString(FIXTURES.resolve("archive-1.14.3/added-ok/archive.json"));
        ArchiveJson.Outcome plain = ArchiveJson.parse(success, "", 0);
        ArchiveJson.Outcome prefixed = ArchiveJson.parse("Note: printed first\n" + success, "", 0);
        assertEquals(plain, prefixed);
    }

    @Test
    void theCapturedUnknownChangeRefusalCarriesTheCliMessage() {
        com.johnnyblabs.openspec.util.CliRunner.CliResult r = com.johnnyblabs.openspec.util.CliFixtures.error("archive-unknown");
        ArchiveRefusedException e = assertThrows(ArchiveRefusedException.class,
                () -> ArchiveJson.parse(r.stdout(), r.stderr(), r.exitCode()));
        assertTrue(e.getMessage().startsWith("Change 'nosuch' not found"), e.getMessage());
    }
}

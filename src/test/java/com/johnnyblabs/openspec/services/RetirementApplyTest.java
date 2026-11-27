package com.johnnyblabs.openspec.services;

import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.CliFixtureSuite;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Applying a retirement as upstream's {@code retireSpec} does, and reporting it as the CLI does: only {@code spec.md}
 * goes, then folders left empty (never {@code specs/} itself); and the plugin's archive reports a retirement with the
 * CLI's own warning, which {@code ArchiveJson} reads back as the CLI path's.
 */
@CliFixtureSuite
class RetirementApplyTest {

    @TempDir
    Path tmp;

    @Test
    void aCleanRetirementDeletesTheSpecAndItsEmptyFoldersButNotSpecs() throws IOException {
        Path specs = tmp.resolve("openspec/specs");
        Path spec = specs.resolve("group/foo/spec.md");
        Files.createDirectories(spec.getParent());
        Files.writeString(spec, "# foo\n");

        SpecSyncService.deleteAndPrune(spec, specs);

        assertFalse(Files.exists(spec.getParent()), "the capability folder");
        assertFalse(Files.exists(specs.resolve("group")), "a parent left empty");
        assertTrue(Files.isDirectory(specs), "specs/ itself stays");
    }

    @Test
    void aFolderHoldingAnotherFileIsKept() throws IOException {
        Path specs = tmp.resolve("openspec/specs");
        Path spec = specs.resolve("foo/spec.md");
        Files.createDirectories(spec.getParent());
        Files.writeString(spec, "# foo\n");
        Files.writeString(spec.resolveSibling("notes.txt"), "keep me\n");

        SpecSyncService.deleteAndPrune(spec, specs);

        assertFalse(Files.exists(spec));
        assertEquals("keep me\n", Files.readString(spec.resolveSibling("notes.txt")));
    }

    /** The plugin's archive reports a retirement in the CLI's own words, captured on every release that retires. */
    @Test
    void thePluginsRetirementWarningIsTheClis() throws IOException {
        int checked = 0;
        for (String v : List.of("1.8.0", "1.11.0", "1.16.1", "2.2.0")) {
            Path c = FixtureVersions.CLI.resolve(v).resolve("retire/clean");
            var input = JsonParser.parseString(Files.readString(c.resolve("case.json"))).getAsJsonObject();
            var archive = JsonParser.parseString(Files.readString(c.resolve("json.out"))).getAsJsonObject()
                    .getAsJsonObject("archive");
            String cli = archive.getAsJsonArray("warnings").get(0).getAsString();
            SpecSyncResult r = new SpecSyncResult("foo", "/p/openspec/specs/foo/spec.md", input.get("spec").getAsString(),
                    "", List.of(), List.of(), List.of(), true);
            assertEquals(cli, SpecSyncService.retiredWarning(r, "/p"), v);
            checked++;
        }
        assertEquals(4, checked);
    }

    /** The CLI path: the retired capabilities are read from {@code archive --json}'s warnings. */
    @Test
    void theRetiredCapabilitiesAreReadFromTheClisArchiveResult() throws Exception {
        for (String v : List.of("1.8.0", "1.11.0", "1.16.1", "2.2.0")) {
            Path c = FixtureVersions.CLI.resolve(v).resolve("retire");
            assertEquals(List.of("foo"), ArchiveJson.parse(Files.readString(c.resolve("clean/json.out")), "", 0).retired(), v);
            assertEquals(List.of(), ArchiveJson.parse(Files.readString(c.resolve("already-retired/json.out")), "", 0).retired(),
                    v + ": an already-retired capability isn't retired again");
        }
        String summary = ArchiveDispatch.summary("c", ArchiveJson.parse(
                Files.readString(FixtureVersions.CLI.resolve("2.2.0/retire/clean/json.out")), "", 0));
        assertTrue(summary.contains("Retired: foo."), summary);
        assertTrue(summary.contains("git checkout HEAD -- \":(top)openspec/specs/foo/spec.md\""), summary);
    }
}

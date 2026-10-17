package com.johnnyblabs.openspec.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test: the plugin's merge against real {@code openspec archive} runs captured from CLI 1.14.3
 * ({@code fixtures/cli/archive-1.14.3/}, re-captured by {@code scripts/capture-archive-fixtures.py}).
 * For every case the plugin must write the main spec byte for byte as upstream did, or refuse with upstream's message,
 * and report the same warnings. Each case also runs with CRLF input.
 */
class ArchiveMergeContractTest {

    private static final Path ROOT = Paths.get("src/test/resources/fixtures/cli/archive-1.14.3");

    /** Every captured case except runs with extra flags (e.g. --skip-specs), whose merge isn't the default archive's. */
    static Stream<String> cases() throws IOException {
        try (Stream<Path> dirs = Files.list(ROOT)) {
            return dirs.filter(Files::isDirectory).filter(p -> !Files.exists(p.resolve("args.txt")))
                    .map(p -> p.getFileName().toString()).sorted().toList().stream();
        }
    }

    private record Merged(Map<String, SpecMerge.Result> byCap, Map<String, String> expectedAfter) {
        List<String> refusals() {
            return byCap.values().stream().map(SpecMerge.Result::refusal).filter(Objects::nonNull).toList();
        }

        List<String> warnings() {
            return byCap.values().stream().flatMap(r -> r.warnings().stream()).toList();
        }
    }

    private static Merged merge(String name, boolean crlf) throws IOException {
        Path dir = ROOT.resolve(name);
        String change = Files.readString(dir.resolve("change.txt")).strip();
        Map<String, SpecMerge.Result> byCap = new TreeMap<>();
        Map<String, String> expected = new TreeMap<>();
        try (Stream<Path> caps = Files.list(dir)) {
            for (Path cap : caps.filter(Files::isDirectory).sorted().toList()) {
                String delta = read(cap.resolve("delta.md"), crlf);
                Path before = cap.resolve("main-before.md");
                String main = Files.exists(before) ? read(before, crlf) : null;
                String capName = cap.getFileName().toString();
                byCap.put(capName, SpecMerge.merge(capName, change, delta, main, "/fixture/openspec/specs/" + capName + "/spec.md"));
                Path after = cap.resolve("main-after.md");
                if (Files.exists(after)) expected.put(capName, Files.readString(after));
            }
        }
        return new Merged(byCap, expected);
    }

    private static String read(Path p, boolean crlf) throws IOException {
        String s = Files.readString(p);
        return crlf ? s.replace("\n", "\r\n") : s;
    }

    /** The archive run's JSON: the no-validate run when validation refused, since that carries the merge's own wording. */
    private static JsonObject archiveJson(String name) throws IOException {
        Path novalidate = ROOT.resolve(name).resolve("archive-novalidate.json");
        Path json = Files.exists(novalidate) ? novalidate : ROOT.resolve(name).resolve("archive.json");
        return JsonParser.parseString(Files.readString(json)).getAsJsonObject();
    }

    private static Optional<JsonObject> status(JsonObject json) {
        JsonArray status = json.has("status") ? json.getAsJsonArray("status") : new JsonArray();
        return status.isEmpty() ? Optional.empty() : Optional.of(status.get(0).getAsJsonObject());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void mergesOrRefusesLikeTheCli(String name) throws IOException {
        JsonObject json = archiveJson(name);
        Optional<JsonObject> status = status(json);
        boolean mergeRefused = status.isPresent() && "archive_spec_update_failed".equals(status.get().get("code").getAsString());

        for (boolean crlf : new boolean[]{false, true}) {
            Merged merged = merge(name, crlf);
            String mode = crlf ? " (CRLF input)" : "";
            if (mergeRefused) {
                String captured = status.get().get("message").getAsString();
                // Exactly OpenSpec's one refusal: a stray refusal on another capability is a divergence too.
                assertEquals(List.of(captured), merged.refusals(), name + mode);
                continue;
            }
            assertEquals(List.of(), merged.refusals(), name + mode + ": the CLI applied this change");
            for (Map.Entry<String, String> e : merged.expectedAfter().entrySet()) {
                String projected = merged.byCap().get(e.getKey()).projected();
                assertNotNull(projected, name + "/" + e.getKey() + mode);
                assertEquals(e.getValue(), projected.replace("\r\n", "\n"), name + "/" + e.getKey() + mode + ": merged spec differs from the CLI's");
            }
            if (status.isEmpty()) {
                List<String> cliWarnings = new ArrayList<>();
                JsonObject archive = json.getAsJsonObject("archive");
                if (archive != null && archive.has("warnings")) {
                    for (JsonElement w : archive.getAsJsonArray("warnings")) cliWarnings.add(w.getAsString());
                }
                assertEquals(cliWarnings, merged.warnings(), name + mode + ": warnings differ from the CLI's");
            }
        }
    }

    /** The archive folder: the plugin names it as the CLI did, refuses an existing one with its words, never makes openspec/archive. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void archiveFolderMatchesTheCli(String name) throws IOException {
        Path dir = ROOT.resolve(name);
        String change = Files.readString(dir.resolve("change.txt")).strip();
        List<String> layout = Files.readAllLines(dir.resolve("layout.txt"));
        assertEquals("openspec/archive exists: False", layout.get(1), name);
        // The capture's date is <DATE>; name with a sentinel date and swap it in.
        String target = ChangeService.archiveTargetName(change, java.time.LocalDate.of(2000, 1, 1)).replace("2000-01-01", "<DATE>");
        Optional<JsonObject> status = status(JsonParser.parseString(Files.readString(dir.resolve("archive.json"))).getAsJsonObject());
        if (status.isEmpty()) {
            assertEquals("changes/archive: " + target, layout.get(0), name + ": archived under a different name");
        } else if ("archive_target_exists".equals(status.get().get("code").getAsString())) {
            assertEquals(status.get().get("message").getAsString(), ChangeService.archiveExistsMessage(target), name);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void fixtureIsSelfConsistent(String name) throws IOException {
        String exit = Files.readString(ROOT.resolve(name).resolve("exit.txt")).strip();
        boolean refused = status(JsonParser.parseString(Files.readString(ROOT.resolve(name).resolve("archive.json")))
                .getAsJsonObject()).isPresent();
        assertEquals(refused ? "1" : "0", exit, name + ": exit code and status disagree");
    }
}

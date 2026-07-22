package com.johnnyblabs.openspec;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.services.SpecParsingService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test: {@link SpecParsingService} must recover the same spec <em>structure</em> the real
 * OpenSpec CLI 1.6.0 reports. The truth side is captured {@code openspec show <id> --json --type spec}
 * output committed under {@code fixtures/cli/1.6.0/spec-structure/<id>.show.json} (only {@code
 * root.path} sanitized); the corpus specs are committed under {@code fixtures/cli/1.6.0/parity-corpus}.
 * For every corpus spec we assert requirement count and per-requirement scenario count equal the
 * captured CLI numbers. This is the anti-regression proof of the fence-aware / any-level-4-scenario
 * rewrite: it passes on the current parser and fails on the pre-fix regex parser (e.g. {@code
 * fenced-scenario}, whose fenced {@code #### Scenario:} the CLI counts as 0 but the old parser
 * counted as 1).
 *
 * <p><b>Title note:</b> the CLI's {@code show} {@code title} field is the spec <em>id</em> (the
 * directory name), not the markdown H1. The plugin recovers the H1 as its title, so title parity is
 * asserted against the H1 read from the spec markdown itself rather than against {@code show.json}.
 */
class SpecParserCliStructureContractTest {

    private static final String CORPUS_DIR = "/fixtures/cli/1.6.0/parity-corpus/openspec/specs";
    private static final String STRUCTURE_DIR = "/fixtures/cli/1.6.0/spec-structure";

    /** Every corpus spec id, discovered from the committed corpus directory on the test classpath. */
    private static List<String> corpusSpecIds() throws Exception {
        Path specsDir = Path.of(SpecParserCliStructureContractTest.class.getResource(CORPUS_DIR).toURI());
        try (Stream<Path> entries = Files.list(specsDir)) {
            return entries.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private static String resource(String path) throws Exception {
        try (InputStream is = SpecParserCliStructureContractTest.class.getResourceAsStream(path)) {
            assertNotNull(is, "missing test resource: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String expectedH1(String specMarkdown) {
        for (String line : specMarkdown.split("\n", -1)) {
            if (line.startsWith("# ")) return line.substring(2).trim();
        }
        return null;
    }

    @Test
    void everyCorpusSpecStructureMatchesCapturedCli() throws Exception {
        List<String> ids = corpusSpecIds();
        assertFalse(ids.isEmpty(), "corpus must not be empty");

        // Corpus-drift guard: exactly one captured .show.json per corpus spec dir, and vice versa.
        Path structureDir = Path.of(SpecParserCliStructureContractTest.class.getResource(STRUCTURE_DIR).toURI());
        List<String> capturedIds;
        try (Stream<Path> entries = Files.list(structureDir)) {
            capturedIds = entries.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".show.json"))
                    .map(n -> n.substring(0, n.length() - ".show.json".length()))
                    .sorted()
                    .toList();
        }
        assertEquals(ids, capturedIds,
                "corpus drift: the set of corpus spec dirs must equal the set of captured .show.json files");

        SpecParsingService service = new SpecParsingService(null);
        List<String> failures = new ArrayList<>();

        for (String id : ids) {
            String specMd = resource(CORPUS_DIR + "/" + id + "/spec.md");
            JsonObject show = JsonParser.parseString(resource(STRUCTURE_DIR + "/" + id + ".show.json"))
                    .getAsJsonObject();

            SpecFile parsed = service.parseSpecContent(specMd, id, "/fixture/" + id + "/spec.md");

            // Title parity against the markdown H1 (see class javadoc: show.json title is the id).
            String h1 = expectedH1(specMd);
            if (!java.util.Objects.equals(h1, parsed.getTitle())) {
                failures.add(id + ": title expected <" + h1 + "> but parser gave <" + parsed.getTitle() + ">");
            }

            int cliReqCount = show.get("requirementCount").getAsInt();
            if (parsed.getRequirements().size() != cliReqCount) {
                failures.add(id + ": requirement count expected " + cliReqCount
                        + " but parser gave " + parsed.getRequirements().size());
                continue; // per-req scenario compare is meaningless if counts differ
            }

            JsonArray cliReqs = show.getAsJsonArray("requirements");
            for (int i = 0; i < cliReqCount; i++) {
                int cliScenarios = cliReqs.get(i).getAsJsonObject().has("scenarios")
                        ? cliReqs.get(i).getAsJsonObject().getAsJsonArray("scenarios").size()
                        : 0;
                Requirement req = parsed.getRequirements().get(i);
                if (req.getScenarios().size() != cliScenarios) {
                    failures.add(id + ": requirement[" + i + "] scenario count expected " + cliScenarios
                            + " but parser gave " + req.getScenarios().size());
                }
            }
        }

        assertTrue(failures.isEmpty(), "CLI structure parity failures:\n  " + String.join("\n  ", failures));
    }
}

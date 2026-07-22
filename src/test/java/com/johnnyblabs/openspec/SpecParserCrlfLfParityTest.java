package com.johnnyblabs.openspec;

import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.services.SpecParsingService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Host-independent CRLF-vs-LF parse invariance for the spec parser, modeled on
 * {@code coordination.CrlfLfParseParityTest}. Each committed corpus spec is parsed with LF endings,
 * again with every {@code \n} rewritten to {@code \r\n}, and once more with a trailing lone {@code \r}
 * (the classic CRLF-corruption tail); all three MUST yield an equal {@link SpecFile}. This relies on
 * the model's value equality added by {@code align-spec-parser-with-cli} and guards the line scanner's
 * {@code \r\n}/{@code \r} normalization against regressing on a Windows checkout.
 */
class SpecParserCrlfLfParityTest {

    private static final String CORPUS_DIR = "/fixtures/cli/1.6.0/parity-corpus/openspec/specs";

    static List<String> corpusSpecIds() throws Exception {
        Path specsDir = Path.of(SpecParserCrlfLfParityTest.class.getResource(CORPUS_DIR).toURI());
        try (Stream<Path> entries = Files.list(specsDir)) {
            return entries.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static String lfContent(String id) {
        try (InputStream is = SpecParserCrlfLfParityTest.class
                .getResourceAsStream(CORPUS_DIR + "/" + id + "/spec.md")) {
            assertNotNull(is, "missing corpus spec: " + id);
            // Normalize to LF first so the baseline is deterministic regardless of git's checkout EOL.
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusSpecIds")
    void parsesIdenticallyAcrossLineEndings(String id) {
        SpecParsingService service = new SpecParsingService(null);
        String lf = lfContent(id);
        assertFalse(lf.contains("\r"), "LF baseline must contain no carriage returns");

        SpecFile base = service.parseSpecContent(lf, id, "/x/spec.md");
        // Guard against a vacuous test: the baseline actually recovered structure.
        assertFalse(base.getRequirements().isEmpty(), id + ": baseline must parse at least one requirement");

        SpecFile crlf = service.parseSpecContent(lf.replace("\n", "\r\n"), id, "/x/spec.md");
        SpecFile trailingCr = service.parseSpecContent(lf + "\r", id, "/x/spec.md");

        assertEquals(base, crlf, id + ": CRLF must yield an equal SpecFile");
        assertEquals(base, trailingCr, id + ": a trailing lone CR must yield an equal SpecFile");
    }
}

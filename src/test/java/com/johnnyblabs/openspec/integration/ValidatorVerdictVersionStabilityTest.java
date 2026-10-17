package com.johnnyblabs.openspec.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durable, version-agnostic verdict-stability contract. {@link ValidatorVerdictParityTest} proves the
 * plugin's built-in validator matches the real CLI's verdicts on the parity corpus for the DEFAULT
 * anchor ({@code 1.8.0}, the laxest captured generation) and the STRICT anchor (the {@code 1.6.0}
 * default map). This lightweight, platform-free test proves the CLI's <em>own</em> verdicts over that
 * shared corpus stay consistent across every captured generation: it discovers every committed
 * {@code fixtures/cli/<gen>/validate-parity-corpus.json} (and its {@code -strict} twin).
 *
 * <p><b>Default arm — subset-of-the-laxest-anchor.</b> Generations may legitimately RELAX a default
 * verdict: 1.8 demoted the missing-SHALL rule to a non-failing warning, so more corpus items validate
 * in default than under 1.6/1.7. The default invariant is therefore NOT map-equality but a subset
 * relation — every captured generation's default valid-set is a subset of the laxest anchor's
 * ({@code 1.8.0}). Because the plugin is proven to match the laxest anchor in
 * {@link ValidatorVerdictParityTest}, "no generation is valid on an item the plugin rejects" ⇒ the
 * plugin is never more restrictive than any captured generation, by transitivity — without cloning the
 * heavy platform harness per generation. A future generation that relaxes FURTHER than the current
 * anchor makes the anchor no longer the laxest and fails {@link ValidatorVerdictParityTest} first,
 * prompting the anchor to be advanced (a deliberate human decision). The known 1.6→1.8 relaxation is
 * pinned ({@link #DEMOTION_FLIPS}) so the subset check can never pass vacuously.
 *
 * <p><b>Strict arm — map-equality (unchanged).</b> Under {@code --strict} every captured generation's
 * map equals the {@code 1.6.0} default map — 1.8 re-promotes the demoted warning under strict,
 * reproducing the 1.6 anchor — a stable cross-generation invariant.
 *
 * <p>Discovery is the durable part: a future {@code 1.9.0} capture is picked up with zero edits, and a
 * relaxation past 1.8 surfaces via the parity test. Pre-1.6 generations are intentionally absent from
 * these sets — their CLI validation rules predate this corpus dialect and legitimately verdict it
 * differently (fence-aware scenario counting arrived at 1.4; multi-line requirement-body keyword
 * reading at 1.6) — their shape is covered by their own era-appropriate fixtures (e.g.
 * {@code fixtures/cli/1.3.0/validate.json}).
 */
class ValidatorVerdictVersionStabilityTest {

    private static final String CLI_DIR = "/fixtures/cli";
    /** Strict-arm anchor: {@code --strict} reproduces this generation's default map on every generation. */
    private static final String STRICT_ANCHOR = "1.6.0";
    /** Default-arm anchor: the laxest captured generation, which the plugin's fallback matches exactly. */
    private static final String DEFAULT_ANCHOR = "1.8.0";
    private static final String NON_STRICT = "validate-parity-corpus.json";
    private static final String STRICT = "validate-parity-corpus-strict.json";
    private static final int CORPUS_SIZE = 13;
    /** Vacuity floor — these corpora must always be present, or discovery has silently broken. */
    private static final Set<String> FLOOR = Set.of("1.6.0", "1.7.0", "1.8.0", "1.9.0", "1.10.0", "1.11.0", "1.12.0", "1.13.2");
    /**
     * The exact, known relaxation between the {@link #STRICT_ANCHOR} default map and the
     * {@link #DEFAULT_ANCHOR} default map: 1.8 demoted the missing-SHALL rule to a default WARNING, so
     * these three body-carrying-but-keywordless corpus items flip false→true. Pinned so the subset
     * check below can never pass vacuously — a silent collapse of the divergence fails loudly.
     */
    private static final Set<String> DEMOTION_FLIPS = Set.of("fenced-keyword", "header-only-keyword", "should-only");

    /** id → valid map parsed from a captured {@code validate --all [--strict] --json} fixture. */
    private static Map<String, Boolean> verdicts(String resourcePath) {
        try (InputStream is = ValidatorVerdictVersionStabilityTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "missing fixture: " + resourcePath);
            JsonObject root = JsonParser.parseString(
                    new String(is.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Boolean> map = new LinkedHashMap<>();
            for (JsonElement el : root.getAsJsonArray("items")) {
                JsonObject item = el.getAsJsonObject();
                map.put(item.get("id").getAsString(), item.get("valid").getAsBoolean());
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The ids reported {@code valid:true} in a verdict map (sorted for stable messages/set algebra). */
    private static Set<String> validIds(Map<String, Boolean> map) {
        Set<String> valid = new TreeSet<>();
        map.forEach((id, ok) -> {
            if (ok) valid.add(id);
        });
        return valid;
    }

    /**
     * Generations (dir names under {@code fixtures/cli}) that committed a fixture with the exact given
     * name, as a {@code gen -> resource-path} map (sorted for stable messages). The exact-filename
     * match is load-bearing: it keeps {@link #NON_STRICT} and {@link #STRICT} discovery disjoint and
     * excludes unrelated files like {@code 1.3.0/validate.json}. Fails loud if {@code /fixtures/cli}
     * does not resolve (a classpath regression must surface as a failure, never a silent empty glob).
     */
    private static Map<String, String> discover(String fileName) throws Exception {
        var url = ValidatorVerdictVersionStabilityTest.class.getResource(CLI_DIR);
        assertNotNull(url, "test resource " + CLI_DIR + " did not resolve — classpath regression");
        Path cliDir = Path.of(url.toURI());
        Map<String, String> found = new TreeMap<>();
        try (Stream<Path> entries = Files.list(cliDir)) {
            entries.filter(Files::isDirectory).forEach(genDir -> {
                if (Files.exists(genDir.resolve(fileName))) {
                    String gen = genDir.getFileName().toString();
                    found.put(gen, CLI_DIR + "/" + gen + "/" + fileName);
                }
            });
        }
        return found;
    }

    @Test
    void cliDefaultVerdictsAreNeverStricterThanTheLaxestGeneration() throws Exception {
        Map<String, String> corpora = discover(NON_STRICT);

        // Vacuity guards — the invariant must never pass on nothing.
        assertTrue(corpora.containsKey(DEFAULT_ANCHOR),
                "laxest default anchor " + DEFAULT_ANCHOR + " must have a corpus");
        assertTrue(corpora.keySet().containsAll(FLOOR),
                "expected at least the " + FLOOR + " corpora — found " + corpora.keySet());
        assertTrue(corpora.size() >= 2, "need >= 2 captured generations to compare; found " + corpora.keySet());

        Map<String, Boolean> laxest = verdicts(corpora.get(DEFAULT_ANCHOR));
        assertEquals(CORPUS_SIZE, laxest.size(), "the anchor parity corpus must have " + CORPUS_SIZE + " items");
        Set<String> laxestValid = validIds(laxest);

        for (Map.Entry<String, String> e : corpora.entrySet()) {
            Map<String, Boolean> gen = verdicts(e.getValue());
            assertEquals(laxest.keySet(), gen.keySet(),
                    "generation " + e.getKey() + " parity corpus has a different item-id set than the "
                            + DEFAULT_ANCHOR + " anchor — a corpus drifted; re-capture, don't edit the fixture");
            // Subset-of-laxest: no generation may be valid on an item the plugin (== laxest anchor)
            // rejects, or the plugin would be MORE restrictive than that generation.
            Set<String> genValid = validIds(gen);
            Set<String> stricterThanPlugin = new TreeSet<>(genValid);
            stricterThanPlugin.removeAll(laxestValid);
            assertTrue(stricterThanPlugin.isEmpty(),
                    "generation " + e.getKey() + " reports items valid that the laxest anchor " + DEFAULT_ANCHOR
                            + " rejects: " + stricterThanPlugin + " — the plugin (matching the laxest anchor) would "
                            + "be MORE restrictive than " + e.getKey() + "; investigate the CLI change and advance "
                            + "the default anchor before shipping");
        }

        // Anti-vacuity pin: lock the known 1.6→1.8 relaxation so the subset check can't pass on a
        // silently collapsed divergence. The laxest (1.8) default valid-set must equal the strict-anchor
        // (1.6) default valid-set PLUS exactly the missing-SHALL demotion flips.
        Set<String> anchor16Valid = validIds(verdicts(CLI_DIR + "/" + STRICT_ANCHOR + "/" + NON_STRICT));
        Set<String> expectedLaxestValid = new TreeSet<>(anchor16Valid);
        expectedLaxestValid.addAll(DEMOTION_FLIPS);
        assertEquals(expectedLaxestValid, laxestValid,
                "the " + DEFAULT_ANCHOR + " default valid-set must equal the " + STRICT_ANCHOR + " default valid-set "
                        + "plus exactly the missing-SHALL demotion flips " + DEMOTION_FLIPS + " — if this diverges, "
                        + "the demotion's scope changed; re-verify against the real CLI, don't edit the fixture");
    }

    @Test
    void strictCliVerdictsAreStableAcrossAllCapturedGenerations() throws Exception {
        Map<String, String> strictCorpora = discover(STRICT);
        assertFalse(strictCorpora.isEmpty(), "expected at least one captured strict corpus (" + STRICT + ")");

        // Symmetric coverage guard: every generation with a non-strict corpus (except the strict anchor,
        // whose CLI is gone and which has no strict fixture) must also carry a strict twin — the capture
        // recipe produces both, so the strict dimension can't silently lag when a generation is added.
        Set<String> nonStrictNonAnchor = new TreeSet<>(discover(NON_STRICT).keySet());
        nonStrictNonAnchor.remove(STRICT_ANCHOR);
        assertEquals(nonStrictNonAnchor, strictCorpora.keySet(),
                "every generation with a non-strict parity corpus (other than the strict anchor " + STRICT_ANCHOR
                        + ") must also have a strict twin (" + STRICT + ") — capture both per the fixtures README");

        // Under --strict every generation reproduces the 1.6.0 default map: a demoted warning re-promotes
        // and nothing else on this corpus is valid-with-only-a-CLI-warning. Map-equality holds.
        Map<String, Boolean> strictAnchor = verdicts(CLI_DIR + "/" + STRICT_ANCHOR + "/" + NON_STRICT);
        for (Map.Entry<String, String> e : strictCorpora.entrySet()) {
            Map<String, Boolean> strict = verdicts(e.getValue());
            assertEquals(strictAnchor.keySet(), strict.keySet(),
                    "strict corpus " + e.getKey() + " has a different item-id set than the " + STRICT_ANCHOR + " anchor");
            assertEquals(strictAnchor, strict,
                    "strict CLI verdicts for " + e.getKey() + " differ from the " + STRICT_ANCHOR + " default verdicts — "
                            + "an item became valid-with-only-a-CLI-warning under strict, or a verdict drifted; "
                            + "investigate before shipping");
        }
    }
}

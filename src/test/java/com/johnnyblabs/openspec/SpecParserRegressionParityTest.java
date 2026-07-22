package com.johnnyblabs.openspec;

import com.johnnyblabs.openspec.legacy.LegacyRegexSpecParser;
import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.Scenario;
import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.services.SpecParsingService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential parity test: the new line-oriented {@link SpecParsingService} must agree with the
 * retired regex parser ({@link LegacyRegexSpecParser}) on the projection
 * {@code {title, requirement names, keywords, scenario names, clauses}} for every input <em>except</em>
 * the explicitly enumerated {@link #KNOWN_DIVERGENCE} set. Each divergence is an intended correction
 * toward the OpenSpec CLI, so the set makes the behavior changes auditable: a divergence that is not
 * enumerated fails the test (an accidental behavior change), and an enumerated divergence must both
 * actually differ from the legacy output <em>and</em> match the CLI-correct expectation asserted in
 * {@link #assertCliCorrect}.
 *
 * <p>The projection deliberately excludes the requirement <em>body</em>: the two parsers compute body
 * text differently (the new one drops metadata lines and fenced content) even where the structural
 * projection agrees, so body is not part of the parity contract.
 */
class SpecParserRegressionParityTest {

    private final SpecParsingService current = new SpecParsingService(null);
    private final LegacyRegexSpecParser legacy = new LegacyRegexSpecParser();

    /**
     * Case id → the CLI-correct behavior the new parser now exhibits and the legacy parser did not.
     * Each key is an input in {@link #inputs()}; {@link #assertCliCorrect} pins the concrete expectation.
     */
    private static final Map<String, String> KNOWN_DIVERGENCE = Map.of(
            "fenced-keyword",
            "fenced scenario/keyword: a SHALL that appears only inside a code fence is not normative -> keyword null",
            "fenced-scenario",
            "fenced scenario: a #### Scenario header inside a code fence is not a scenario -> 0 scenarios",
            "header-only-keyword",
            "header-only keyword: SHALL only in the requirement header is not normative (body only) -> keyword null",
            "should-only",
            "SHOULD/MAY not normative: the CLI normative set is exactly SHALL/MUST -> keyword null",
            "inline-must-keyword",
            "MUST keyword: MUST is normative to the CLI; the legacy set omitted it -> keyword MUST",
            "inline-bold-scenario",
            "bold **Scenario:** form is not a scenario to the CLI -> 0 scenarios");

    private static String resource(String path) {
        try (InputStream is = SpecParserRegressionParityTest.class.getResourceAsStream(path)) {
            assertNotNull(is, "missing test resource: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String corpus(String id) {
        return resource("/fixtures/cli/1.6.0/parity-corpus/openspec/specs/" + id + "/spec.md");
    }

    /** All differential inputs: the CLI corpus plus focused inline cases isolating each divergence. */
    private static Map<String, String> inputs() {
        Map<String, String> in = new LinkedHashMap<>();
        // --- CLI corpus (11 specs) ---
        for (String id : new String[]{
                "clean-spec", "fenced-keyword", "fenced-scenario", "header-only-keyword",
                "second-line-keyword", "should-only", "indented-code", "setext-header",
                "table-keyword", "html-comment-req", "nested-list-scenario"}) {
            in.put(id, corpus(id));
        }
        // --- inline cases (agreeing: regression net that the enrichment survives) ---
        in.put("inline-shall-not",
                "# Spec\n\n### Requirement: Negative\nThe system SHALL NOT expose secrets.\n");
        in.put("inline-multi-scenario", """
                # Spec

                ### Requirement: Multi
                The system SHALL handle multiple scenarios.

                #### Scenario: Happy path
                - GIVEN valid input
                - WHEN processed
                - THEN success

                #### Scenario: Error path
                - GIVEN invalid input
                - WHEN processed
                - THEN error is reported
                """);
        in.put("inline-and-clauses", """
                # Spec

                ### Requirement: With And
                The system SHALL validate inputs.

                #### Scenario: Full validation
                - GIVEN valid credentials
                - AND the account is active
                - WHEN login is attempted
                - THEN access is granted
                - AND a session token is returned
                """);
        // --- inline cases (diverging: the MUST-keyword and bold-Scenario corrections) ---
        in.put("inline-must-keyword",
                "# Spec\n\n### Requirement: Mandatory\nThe system MUST persist the record.\n");
        in.put("inline-bold-scenario", """
                # Spec

                ### Requirement: Bold scenarios
                The system SHALL not count bold scenario lines.

                **Scenario: Not counted**
                - **WHEN** invoked
                - **THEN** nothing
                """);
        return in;
    }

    /** Canonical projection over {title, requirement names, keywords, scenario names, clauses}. */
    private static String project(SpecFile spec) {
        StringBuilder sb = new StringBuilder("title=").append(spec.getTitle());
        for (Requirement req : spec.getRequirements()) {
            sb.append("\n  req name=").append(req.getName()).append(" keyword=").append(req.getKeyword());
            for (Scenario sc : req.getScenarios()) {
                sb.append("\n    scenario=").append(sc.getName()).append(" clauses=").append(sc.getClauses());
            }
        }
        return sb.toString();
    }

    @Test
    void newParserMatchesLegacyExceptForEnumeratedDivergences() {
        Map<String, String> inputs = inputs();
        // Guard: every enumerated divergence must correspond to a real input (no stale entries).
        for (String id : KNOWN_DIVERGENCE.keySet()) {
            assertTrue(inputs.containsKey(id), "KNOWN_DIVERGENCE lists unknown input '" + id + "'");
        }

        for (Map.Entry<String, String> e : inputs.entrySet()) {
            String id = e.getKey();
            SpecFile now = current.parseSpecContent(e.getValue(), id, "/x/spec.md");
            SpecFile old = legacy.parseSpecContent(e.getValue(), id, "/x/spec.md");
            String nowP = project(now);
            String oldP = project(old);

            if (KNOWN_DIVERGENCE.containsKey(id)) {
                assertNotEquals(oldP, nowP,
                        "'" + id + "' is enumerated as a divergence but new/legacy agree — "
                                + "the case no longer exercises " + KNOWN_DIVERGENCE.get(id));
                assertCliCorrect(id, now);
            } else {
                assertEquals(oldP, nowP,
                        "unenumerated divergence for '" + id + "'. If intentional, add it to "
                                + "KNOWN_DIVERGENCE with the CLI-correct expectation; if not, it is a regression.");
            }
        }
    }

    /** Pins the concrete CLI-correct output for each enumerated divergence. */
    private static void assertCliCorrect(String id, SpecFile now) {
        Requirement r0 = now.getRequirements().get(0);
        switch (id) {
            case "fenced-keyword", "header-only-keyword", "should-only" ->
                    assertNull(r0.getKeyword(), id + ": keyword must be null (not normative to the CLI)");
            case "fenced-scenario", "inline-bold-scenario" ->
                    assertTrue(r0.getScenarios().isEmpty(), id + ": must have 0 scenarios");
            case "inline-must-keyword" ->
                    assertEquals("MUST", r0.getKeyword(), id + ": MUST must be recognized as normative");
            default -> fail("no CLI-correct assertion wired for divergence '" + id + "'");
        }
    }
}

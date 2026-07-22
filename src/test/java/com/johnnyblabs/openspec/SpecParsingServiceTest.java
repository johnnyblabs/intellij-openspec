package com.johnnyblabs.openspec;

import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.Scenario;
import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.services.SpecParsingService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SpecParsingServiceTest {

    private String loadTestResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(is, "Test resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parseSpecContent_parsesTitle() throws IOException {
        String content = loadTestResource("openspec/specs/test-domain/spec.md");
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test-domain", "/test/spec.md");

        assertEquals("Test Domain", spec.getTitle());
        assertEquals("test-domain", spec.getDomain());
    }

    @Test
    void parseSpecContent_parsesRequirements() throws IOException {
        String content = loadTestResource("openspec/specs/test-domain/spec.md");
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test-domain", "/test/spec.md");

        assertEquals(2, spec.getRequirements().size());

        Requirement first = spec.getRequirements().get(0);
        assertEquals("Basic Feature", first.getName());
        assertEquals("SHALL", first.getKeyword());

        Requirement second = spec.getRequirements().get(1);
        assertEquals("Optional Feature", second.getName());
        // align-spec-parser-with-cli: MAY is not a normative keyword to the CLI, so it is not
        // reported as a keyword (only SHALL/MUST are). Previously the parser surfaced "MAY".
        assertNull(second.getKeyword());
    }

    @Test
    void parseSpecContent_recognizesNonCanonicalHeaderCasing() {
        String content = """
                # Case Parity

                ### requirement: Lowercase token
                The system SHALL parse this.

                #### Scenario: Parse
                - **WHEN** parsed
                - **THEN** listed

                ### REQUIREMENT: Uppercase token
                The system MAY parse this too.

                #### Scenario: Parse again
                - **WHEN** parsed
                - **THEN** listed
                """;
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "case-parity", "/test/spec.md");

        assertEquals(2, spec.getRequirements().size(),
                "CLI 1.4+ parses the header token case-insensitively; tree parsing must match");
        assertEquals("Lowercase token", spec.getRequirements().get(0).getName());
        assertEquals("Uppercase token", spec.getRequirements().get(1).getName());
    }

    @Test
    void parseSpecContent_parsesScenarios() throws IOException {
        String content = loadTestResource("openspec/specs/test-domain/spec.md");
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test-domain", "/test/spec.md");

        Requirement first = spec.getRequirements().get(0);
        assertEquals(1, first.getScenarios().size());

        Scenario scenario = first.getScenarios().get(0);
        assertEquals("Feature works", scenario.getName());
        assertEquals(3, scenario.getClauses().size());
        assertTrue(scenario.getClauses().get(0).startsWith("GIVEN"));
        assertTrue(scenario.getClauses().get(1).startsWith("WHEN"));
        assertTrue(scenario.getClauses().get(2).startsWith("THEN"));
    }

    @Test
    void parseSpecContent_handlesEmptyContent() {
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent("", "empty", "/test/empty.md");

        assertNotNull(spec);
        assertNull(spec.getTitle());
        assertTrue(spec.getRequirements().isEmpty());
    }

    @Test
    void parseSpecContent_handlesContentWithoutScenarios() {
        String content = "# Simple\n\n### Requirement: Solo\n\nThe system SHALL work.\n";
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "simple", "/test/simple.md");

        assertEquals(1, spec.getRequirements().size());
        assertEquals("Solo", spec.getRequirements().get(0).getName());
        assertEquals("SHALL", spec.getRequirements().get(0).getKeyword());
        assertTrue(spec.getRequirements().get(0).getScenarios().isEmpty());
    }

    @Test
    void parseSpecContent_extractsShallNotKeyword() {
        String content = "# Spec\n\n### Requirement: Negative\n\nThe system SHALL NOT expose secrets.\n";
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        assertEquals("SHALL NOT", spec.getRequirements().getFirst().getKeyword());
    }

    @Test
    void parseSpecContent_shouldNotIsNotNormative() {
        String content = "# Spec\n\n### Requirement: Advisory\n\nThe system SHOULD NOT block the UI thread.\n";
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        // align-spec-parser-with-cli: the CLI's normative set is exactly SHALL/MUST, so a
        // requirement whose only modal is SHOULD (NOT) has no normative keyword.
        assertNull(spec.getRequirements().getFirst().getKeyword());
    }

    @Test
    void parseSpecContent_extractsMustKeyword() {
        String content = "# Spec\n\n### Requirement: Mandatory\n\nThe system MUST persist the record.\n";
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        // align-spec-parser-with-cli: MUST is normative to the CLI; the old parser omitted it.
        assertEquals("MUST", spec.getRequirements().getFirst().getKeyword());
    }

    @Test
    void parseSpecContent_parsesMultipleScenariosPerRequirement() {
        String content = """
                # Spec

                ### Requirement: Multi-Scenario

                The system SHALL handle multiple scenarios.

                #### Scenario: Happy path
                - GIVEN valid input
                - WHEN processed
                - THEN success

                #### Scenario: Error path
                - GIVEN invalid input
                - WHEN processed
                - THEN error is reported
                """;
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        Requirement req = spec.getRequirements().getFirst();
        assertEquals(2, req.getScenarios().size());
        assertEquals("Happy path", req.getScenarios().get(0).getName());
        assertEquals("Error path", req.getScenarios().get(1).getName());
        assertEquals(3, req.getScenarios().get(0).getClauses().size());
        assertEquals(3, req.getScenarios().get(1).getClauses().size());
    }

    @Test
    void parseSpecContent_parsesAndClauses() {
        String content = """
                # Spec

                ### Requirement: With And

                The system SHALL validate inputs.

                #### Scenario: Full validation
                - GIVEN valid credentials
                - AND the account is active
                - WHEN login is attempted
                - THEN access is granted
                - AND a session token is returned
                """;
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        Scenario scenario = spec.getRequirements().getFirst().getScenarios().getFirst();
        assertEquals(5, scenario.getClauses().size());
        assertTrue(scenario.getClauses().get(0).startsWith("GIVEN"));
        assertTrue(scenario.getClauses().get(1).startsWith("AND"));
        assertTrue(scenario.getClauses().get(2).startsWith("WHEN"));
        assertTrue(scenario.getClauses().get(3).startsWith("THEN"));
        assertTrue(scenario.getClauses().get(4).startsWith("AND"));
    }

    @Test
    void parseSpecContent_requirementWithoutKeyword() {
        String content = "# Spec\n\n### Requirement: No Keyword\n\nThis requirement has no RFC 2119 keyword.\n";
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        assertNull(spec.getRequirements().getFirst().getKeyword());
    }

    @Test
    void parseSpecContent_titleOnlyContent() {
        String content = "# Just A Title\n\nSome description text but no requirements.\n";
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        assertEquals("Just A Title", spec.getTitle());
        assertTrue(spec.getRequirements().isEmpty());
    }

    @Test
    void parseSpecContent_extractsBodyBeforeScenario() {
        String content = """
                # Spec

                ### Requirement: Body Test

                The system SHALL parse body text correctly.
                This is additional body content.

                #### Scenario: Check body
                - GIVEN a requirement
                - WHEN parsed
                - THEN body is extracted
                """;
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        String body = spec.getRequirements().getFirst().getBody();
        assertTrue(body.contains("SHALL parse body text correctly"));
        assertTrue(body.contains("additional body content"));
        assertFalse(body.contains("Scenario"));
    }

    @Test
    void parseSpecContent_multipleRequirementsWithMixedKeywords() {
        String content = """
                # Mixed Keywords

                ### Requirement: Must Do

                The system SHALL do this.

                ### Requirement: Should Do

                The system SHOULD do that.

                ### Requirement: May Do

                The system MAY optionally do this.

                ### Requirement: Must Not Do

                The system SHALL NOT do this.
                """;
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        assertEquals(4, spec.getRequirements().size());
        // align-spec-parser-with-cli: only SHALL/MUST are normative to the CLI. SHOULD and MAY
        // therefore report no keyword; SHALL NOT keeps its negated display (it contains SHALL).
        assertEquals("SHALL", spec.getRequirements().get(0).getKeyword());
        assertNull(spec.getRequirements().get(1).getKeyword());
        assertNull(spec.getRequirements().get(2).getKeyword());
        assertEquals("SHALL NOT", spec.getRequirements().get(3).getKeyword());
    }

    @Test
    void parseSpecContent_ignoresMarkersInsideCodeFence() {
        String content = """
                # Fenced

                ### Requirement: Hidden
                The system SHALL work.

                ```
                #### Scenario: only an example
                - **WHEN** x
                - **THEN** y
                ```
                """;
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        // align-spec-parser-with-cli: a scenario header inside a fence is not a scenario.
        assertEquals(1, spec.getRequirements().size());
        assertTrue(spec.getRequirements().getFirst().getScenarios().isEmpty());
    }

    @Test
    void parseSpecContent_keywordInsideFenceIsNotNormative() {
        String content = """
                # Fenced Keyword

                ### Requirement: Example only
                This requirement only shows an example.

                ```
                The system SHALL do the thing.
                ```
                """;
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        // align-spec-parser-with-cli: SHALL only inside a fence does not make the requirement normative.
        assertNull(spec.getRequirements().getFirst().getKeyword());
    }

    @Test
    void parseSpecContent_anyLevel4HeaderIsAScenario() {
        String content = """
                # Any Header

                ### Requirement: Given form
                The system SHALL support given/when/then headers.

                #### Given a running system
                - **WHEN** invoked
                - **THEN** it responds
                """;
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        // align-spec-parser-with-cli: the CLI counts ANY level-4 header as a scenario, not only
        // "Scenario:"-labelled ones.
        assertEquals(1, spec.getRequirements().getFirst().getScenarios().size());
        assertEquals("Given a running system",
                spec.getRequirements().getFirst().getScenarios().getFirst().getName());
    }

    @Test
    void parseSpecContent_boldScenarioFormIsNotAScenario() {
        String content = """
                # Bold Form

                ### Requirement: Bold scenarios
                The system SHALL not count bold scenario lines.

                **Scenario: Not counted**
                - **WHEN** invoked
                - **THEN** nothing
                """;
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        // align-spec-parser-with-cli: the bold **Scenario:** form is not recognized by the CLI.
        assertTrue(spec.getRequirements().getFirst().getScenarios().isEmpty());
    }
}

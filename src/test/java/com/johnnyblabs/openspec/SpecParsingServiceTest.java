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
        assertEquals("MAY", second.getKeyword());
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
    void parseSpecContent_extractsShouldNotKeyword() {
        String content = "# Spec\n\n### Requirement: Advisory\n\nThe system SHOULD NOT block the UI thread.\n";
        SpecParsingService service = new SpecParsingService(null);
        SpecFile spec = service.parseSpecContent(content, "test", "/test.md");

        assertEquals("SHOULD NOT", spec.getRequirements().getFirst().getKeyword());
    }

    @Test
    void parseSpecContent_parsesMultipleScenariosPerRequirement() {
        String content = """
                # Spec

                ### Requirement: Multi-Scenario

                The system SHALL handle multiple scenarios.

                **Scenario: Happy path**
                - GIVEN valid input
                - WHEN processed
                - THEN success

                **Scenario: Error path**
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

                **Scenario: Full validation**
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

                **Scenario: Check body**
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
        assertEquals("SHALL", spec.getRequirements().get(0).getKeyword());
        assertEquals("SHOULD", spec.getRequirements().get(1).getKeyword());
        assertEquals("MAY", spec.getRequirements().get(2).getKeyword());
        assertEquals("SHALL NOT", spec.getRequirements().get(3).getKeyword());
    }
}

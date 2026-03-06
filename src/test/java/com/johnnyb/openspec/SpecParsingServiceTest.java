package com.johnnyb.openspec;

import com.johnnyb.openspec.model.Requirement;
import com.johnnyb.openspec.model.Scenario;
import com.johnnyb.openspec.model.SpecFile;
import com.johnnyb.openspec.services.SpecParsingService;
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
}

package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.model.SchemaResolution;
import com.johnnyblabs.openspec.model.SchemaValidationReport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Degradation-path unit tests for the schema tooling parsers: malformed output,
 * banner preambles, missing fields, CLI-failure wrapping. The happy paths are
 * covered against captured real output in {@link SchemaToolingContractTest}.
 */
class SchemaToolingParsingTest {

    @Test
    void validation_malformedJson_returnsNull() {
        assertNull(SchemaService.parseSchemaValidation("not json at all"));
        assertNull(SchemaService.parseSchemaValidation(""));
        assertNull(SchemaService.parseSchemaValidation(null));
    }

    @Test
    void validation_jsonWithoutValidField_returnsNull() {
        assertNull(SchemaService.parseSchemaValidation("{\"name\": \"x\"}"));
    }

    @Test
    void validation_toleratesBannerPreamble() {
        String raw = "Note: Schema commands are experimental and may change.\n"
                + "{\"name\":\"x\",\"valid\":true,\"issues\":[]}";
        SchemaValidationReport report = SchemaService.parseSchemaValidation(raw);
        assertNotNull(report);
        assertTrue(report.valid());
    }

    @Test
    void validation_cliFailure_carriesStderr() {
        SchemaValidationReport report = SchemaValidationReport.cliFailure("x", "boom");
        assertTrue(report.isCliFailure());
        assertEquals("boom", report.cliError());
        assertFalse(report.valid());
    }

    @Test
    void validation_issueMissingFields_defaultsApplied() {
        SchemaValidationReport report = SchemaService.parseSchemaValidation(
                "{\"valid\":false,\"issues\":[{}]}");
        assertNotNull(report);
        SchemaValidationReport.Issue issue = report.issues().getFirst();
        assertEquals("error", issue.level());
        assertEquals("", issue.path());
        assertEquals("", issue.message());
    }

    @Test
    void resolution_malformedOrMissingSource_returnsNull() {
        assertNull(SchemaService.parseSchemaResolution("nope"));
        assertNull(SchemaService.parseSchemaResolution("{\"name\":\"x\"}"));
        assertNull(SchemaService.parseSchemaResolution(null));
    }

    @Test
    void resolution_missingShadows_isNotShadowing() {
        SchemaResolution resolution = SchemaService.parseSchemaResolution(
                "{\"name\":\"x\",\"source\":\"user\",\"path\":\"/p\"}");
        assertNotNull(resolution);
        assertEquals("user", resolution.source());
        assertFalse(resolution.isShadowing());
    }

    @Test
    void templates_malformedJson_returnsEmptyMap() {
        assertTrue(SchemaService.parseTemplatePaths("garbage").isEmpty());
        assertTrue(SchemaService.parseTemplatePaths(null).isEmpty());
    }

    @Test
    void templates_entriesWithoutPath_areOmitted() {
        Map<String, String> paths = SchemaService.parseTemplatePaths(
                "{\"proposal\":{\"path\":\"/t/proposal.md\",\"source\":\"package\"},"
                        + "\"design\":{\"source\":\"package\"},"
                        + "\"tasks\":{\"path\":null,\"source\":\"package\"},"
                        + "\"note\":\"not-an-object\"}");
        assertEquals(Map.of("proposal", "/t/proposal.md"), paths);
    }
}

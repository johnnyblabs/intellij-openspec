package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.model.SchemaResolution;
import com.johnnyblabs.openspec.model.SchemaValidationReport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests parsing REAL output captured from OpenSpec CLI 1.4.1 (isolated
 * XDG_DATA_HOME, machine paths sanitized) for the schema tooling surface:
 * {@code schema validate --json}, {@code schema which --json}, {@code templates --json}.
 * If the CLI output format changes, re-capture the fixtures under
 * src/test/resources/fixtures/cli/ and fix the failures.
 */
class SchemaToolingContractTest {

    private static String loadFixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = SchemaToolingContractTest.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    @Nested
    class SchemaValidateContract {

        @Test
        void parsesCleanValidation() {
            SchemaValidationReport report =
                    SchemaService.parseSchemaValidation(loadFixture("schema-validate-clean.json"));

            assertNotNull(report);
            assertEquals("custom-flow", report.name());
            assertTrue(report.valid());
            assertTrue(report.issues().isEmpty());
            assertFalse(report.isCliFailure());
        }

        @Test
        void parsesMissingTemplateIssue() {
            SchemaValidationReport report =
                    SchemaService.parseSchemaValidation(loadFixture("schema-validate-missing-template.json"));

            assertNotNull(report);
            assertFalse(report.valid());
            assertEquals(1, report.issues().size());
            SchemaValidationReport.Issue issue = report.issues().getFirst();
            assertEquals("error", issue.level());
            assertEquals("artifacts.design.template", issue.path());
            assertTrue(issue.message().contains("design.md"));
        }

        @Test
        void parsesSchemaYamlParseError() {
            SchemaValidationReport report =
                    SchemaService.parseSchemaValidation(loadFixture("schema-validate-broken.json"));

            assertNotNull(report);
            assertFalse(report.valid());
            assertEquals(1, report.issues().size());
            assertEquals("schema.yaml", report.issues().getFirst().path());
            assertTrue(report.issues().getFirst().message().startsWith("Parse error"));
        }
    }

    @Nested
    class SchemaWhichContract {

        @Test
        void parsesPackageBuiltIn() {
            SchemaResolution resolution =
                    SchemaService.parseSchemaResolution(loadFixture("schema-which-builtin.json"));

            assertNotNull(resolution);
            assertEquals("spec-driven", resolution.name());
            assertEquals("package", resolution.source());
            assertTrue(resolution.path().contains("@fission-ai/openspec/schemas/spec-driven"));
            assertFalse(resolution.isShadowing());
        }

        @Test
        void parsesProjectFork() {
            SchemaResolution resolution =
                    SchemaService.parseSchemaResolution(loadFixture("schema-which-project.json"));

            assertNotNull(resolution);
            assertEquals("custom-flow", resolution.name());
            assertEquals("project", resolution.source());
            assertFalse(resolution.isShadowing());
        }

        @Test
        void parsesProjectCopyShadowingPackageBuiltIn() {
            SchemaResolution resolution =
                    SchemaService.parseSchemaResolution(loadFixture("schema-which-shadowing.json"));

            assertNotNull(resolution);
            assertEquals("spec-driven", resolution.name());
            assertEquals("project", resolution.source());
            assertTrue(resolution.isShadowing());
            assertEquals(java.util.List.of("package"), resolution.shadowedSources());
        }
    }

    @Nested
    class TemplatesContract {

        @Test
        void parsesBuiltInTemplateMap() {
            Map<String, String> paths =
                    SchemaService.parseTemplatePaths(loadFixture("templates-builtin.json"));

            assertEquals(4, paths.size());
            // CLI emits artifacts in schema order; LinkedHashMap preserves it.
            assertEquals(java.util.List.of("proposal", "specs", "design", "tasks"),
                    java.util.List.copyOf(paths.keySet()));
            assertTrue(paths.get("proposal").endsWith("templates/proposal.md"));
            assertTrue(paths.get("specs").endsWith("templates/spec.md"));
        }
    }
}

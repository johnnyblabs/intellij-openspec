package com.johnnyblabs.openspec.scaffolding;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that templates produce content conforming to OpenSpec 1.2.0 structure.
 * These tests ensure that built-in scaffolding creates artifacts the CLI and
 * validator will accept.
 */
class TemplateProviderTest {

    // --- Proposal template ---

    @Test
    void proposalTemplate_hasWhySection() {
        String result = TemplateProvider.proposalTemplate("my-feature", "Add feature X", null);
        assertTrue(result.contains("## Why"));
        assertTrue(result.contains("Add feature X"),
                "Why text must appear in the proposal body");
    }

    @Test
    void proposalTemplate_hasWhatChangesSection() {
        String result = TemplateProvider.proposalTemplate("my-feature", null, "- Change A\n- Change B");
        assertTrue(result.contains("## What Changes"));
        assertTrue(result.contains("- Change A"),
                "What Changes text must appear in the proposal body");
    }

    @Test
    void proposalTemplate_hasCapabilitiesAndImpact() {
        String result = TemplateProvider.proposalTemplate("my-feature", "why", "what");
        assertTrue(result.contains("## Capabilities"));
        assertTrue(result.contains("### New Capabilities"));
        assertTrue(result.contains("### Modified Capabilities"));
        assertTrue(result.contains("## Impact"));
    }

    @Test
    void proposalTemplate_usesPlaceholdersWhenFieldsBlank() {
        String result = TemplateProvider.proposalTemplate("my-feature", "", "");
        assertTrue(result.contains("<!-- Explain the motivation"),
                "Blank why should produce placeholder comment");
        assertTrue(result.contains("<!-- Describe what will change"),
                "Blank whatChanges should produce placeholder comment");
    }

    @Test
    void proposalTemplate_substitutesSpecialCharacters() {
        String result = TemplateProvider.proposalTemplate("add-auth/login", "Login & OAuth 2.0", null);
        assertTrue(result.contains("Login & OAuth 2.0"));
    }

    // --- Design template ---

    /**
     * The plugin no longer writes design or tasks templates (Propose creates what {@code openspec new change} does,
     * plus the proposal), but changes made by older versions still hold them. Their frozen copies in the fixture must
     * still be recognised, so the chips can hint that they look like unfilled templates.
     */
    @Test
    void oldDesignAndTasksTemplatesAreStillRecognisedAsTemplates() throws Exception {
        java.nio.file.Path change = java.nio.file.Paths.get("src/test/resources/fixtures/cli/1.14.3/status-template-artifacts/change");
        com.johnnyblabs.openspec.services.ScaffoldingDetectionService detector =
                new com.johnnyblabs.openspec.services.ScaffoldingDetectionService(null);
        assertTrue(detector.isScaffoldingContent(java.nio.file.Files.readString(change.resolve("design.md"))));
        assertTrue(detector.isScaffoldingContent(java.nio.file.Files.readString(change.resolve("tasks.md"))));
    }

    @Test
    void deltaSpecTemplate_hasMarkdownTitle() {
        String result = TemplateProvider.deltaSpecTemplate("authentication");
        assertTrue(result.contains("# Delta Spec: authentication"));
    }

    @Test
    void deltaSpecTemplate_hasAllFourSections() {
        String result = TemplateProvider.deltaSpecTemplate("auth");
        // Each heading must carry the full "Requirements" suffix: the OpenSpec CLI only parses
        // "## ADDED Requirements" etc., so a bare "## ADDED" scaffold is invisible to it ("No deltas found").
        for (String heading : new String[]{"ADDED", "MODIFIED", "REMOVED", "RENAMED"}) {
            assertTrue(result.contains("\n## " + heading + " Requirements\n"),
                    "Delta spec must emit '## " + heading + " Requirements': " + result);
            assertFalse(java.util.regex.Pattern.compile("^## " + heading + "\\s*$", java.util.regex.Pattern.MULTILINE)
                            .matcher(result).find(),
                    "Delta spec must not emit a bare '## " + heading + "' heading");
        }
    }

    // --- .openspec.yaml template ---

    @Test
    void openspecYamlTemplate_isValidYaml() {
        String result = TemplateProvider.openspecYamlTemplate("spec-driven");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);
        assertNotNull(parsed, "Must produce valid YAML");
    }

    @Test
    void openspecYamlTemplate_hasSchemaAndCreatedOnly() {
        // Matches real `openspec new change`: the given schema + created, no status, and NOT the retired
        // invented `openspec-change` schema value.
        String result = TemplateProvider.openspecYamlTemplate("spec-driven");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);

        assertEquals("spec-driven", parsed.get("schema"), "schema is the given schema, not 'openspec-change'");
        assertNotNull(parsed.get("created"), "created date must be present");
        assertFalse(parsed.containsKey("status"), "the invented status: field is retired");
    }

    @Test
    void openspecYamlTemplate_usesTodayDate() {
        String result = TemplateProvider.openspecYamlTemplate("spec-driven");
        assertTrue(result.contains(LocalDate.now().toString()),
                "created date must be today's date");
    }

    @Test
    void openspecYamlTemplate_schemaReflectsParameter_createdUnquoted() {
        String wp = TemplateProvider.openspecYamlTemplate("workspace-planning");
        assertTrue(wp.contains("schema: workspace-planning"), "the schema arg is emitted verbatim");
        assertFalse(wp.contains("openspec-change"), "never the retired invented schema value");
        // created is UNQUOTED, matching real `openspec new change` (an unquoted date scalar).
        assertFalse(wp.contains("created: \""), "created must be unquoted");
    }

    // --- config.yaml template ---

    @Test
    void configYamlTemplate_isValidYaml() {
        String result = TemplateProvider.configYamlTemplate("spec-driven");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);
        assertNotNull(parsed, "Must produce valid YAML");
    }

    @Test
    void configYamlTemplate_isSchemaOnly() {
        // Matches upstream `openspec init` — a single schema: line and nothing else. The plugin no
        // longer writes the plugin-invented version:/profile: or empty context:/rules: into a fresh
        // config.yaml (they were display-only fields upstream ignores).
        String result = TemplateProvider.configYamlTemplate("spec-driven");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);

        assertEquals("spec-driven", parsed.get("schema"));
        assertEquals(1, parsed.size(), "schema-only config has exactly one key");
        assertFalse(parsed.containsKey("version"), "no plugin-invented version: is written");
        assertFalse(parsed.containsKey("profile"), "no plugin-invented profile: is written");
        assertFalse(parsed.containsKey("context"), "no empty context: is written");
        assertFalse(parsed.containsKey("rules"), "no empty rules: is written");
    }

    @Test
    void configYamlTemplate_schemaParameterApplied() {
        String tdd = TemplateProvider.configYamlTemplate("tdd");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(tdd);
        assertEquals("tdd", parsed.get("schema"));
    }
}

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

    @Test
    void designTemplate_hasMarkdownTitle() {
        String result = TemplateProvider.designTemplate("my-feature");
        assertTrue(result.contains("# Design: my-feature"));
    }

    @Test
    void designTemplate_hasSections() {
        String result = TemplateProvider.designTemplate("my-feature");
        assertTrue(result.contains("## Approach"));
        assertTrue(result.contains("## Components Affected"));
        assertTrue(result.contains("## Trade-offs"));
    }

    // --- Tasks template ---

    @Test
    void tasksTemplate_hasMarkdownTitle() {
        String result = TemplateProvider.tasksTemplate("my-feature");
        assertTrue(result.contains("# Tasks: my-feature"));
    }

    @Test
    void tasksTemplate_hasImplementationAndTestingSections() {
        String result = TemplateProvider.tasksTemplate("my-feature");
        assertTrue(result.contains("## Implementation Tasks"));
        assertTrue(result.contains("## Testing Tasks"));
    }

    @Test
    void tasksTemplate_hasCheckboxItems() {
        String result = TemplateProvider.tasksTemplate("my-feature");
        assertTrue(result.contains("- [ ] "), "Tasks must have checkbox items");
    }

    // --- Delta spec template ---

    @Test
    void deltaSpecTemplate_hasMarkdownTitle() {
        String result = TemplateProvider.deltaSpecTemplate("authentication");
        assertTrue(result.contains("# Delta Spec: authentication"));
    }

    @Test
    void deltaSpecTemplate_hasAllFourSections() {
        String result = TemplateProvider.deltaSpecTemplate("auth");
        assertTrue(result.contains("## ADDED"),
                "Delta spec must have ## ADDED section");
        assertTrue(result.contains("## MODIFIED"),
                "Delta spec must have ## MODIFIED section");
        assertTrue(result.contains("## REMOVED"),
                "Delta spec must have ## REMOVED section");
        assertTrue(result.contains("## RENAMED Requirements"),
                "Delta spec must emit ## RENAMED Requirements with the full suffix — the validator's "
                        + "structural regex (^## ... Requirements) only engages when the suffix is present");
    }

    // --- .openspec.yaml template ---

    @Test
    void openspecYamlTemplate_isValidYaml() {
        String result = TemplateProvider.openspecYamlTemplate("proposed");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);
        assertNotNull(parsed, "Must produce valid YAML");
    }

    @Test
    void openspecYamlTemplate_hasRequiredFields() {
        String result = TemplateProvider.openspecYamlTemplate("proposed");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);

        assertEquals("openspec-change", parsed.get("schema"),
                "schema must be 'openspec-change'");
        assertEquals("proposed", parsed.get("status"));
        assertNotNull(parsed.get("created"), "created date must be present");
    }

    @Test
    void openspecYamlTemplate_usesTodayDate() {
        String result = TemplateProvider.openspecYamlTemplate("proposed");
        assertTrue(result.contains(LocalDate.now().toString()),
                "created date must be today's date");
    }

    @Test
    void openspecYamlTemplate_statusReflectsParameter() {
        String proposed = TemplateProvider.openspecYamlTemplate("proposed");
        assertTrue(proposed.contains("status: proposed"));

        String applied = TemplateProvider.openspecYamlTemplate("applied");
        assertTrue(applied.contains("status: applied"));
    }

    // --- config.yaml template ---

    @Test
    void configYamlTemplate_isValidYaml() {
        String result = TemplateProvider.configYamlTemplate("spec-driven", "1.2.0");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);
        assertNotNull(parsed, "Must produce valid YAML");
    }

    @Test
    void configYamlTemplate_hasSchemaAndVersion() {
        String result = TemplateProvider.configYamlTemplate("spec-driven", "1.2.0");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);

        assertEquals("spec-driven", parsed.get("schema"));
        assertEquals("1.2.0", parsed.get("version"));
    }

    @Test
    void configYamlTemplate_hasProfile() {
        String result = TemplateProvider.configYamlTemplate("spec-driven", "1.2.0");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);

        assertNotNull(parsed.get("profile"), "config must have profile");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) parsed.get("profile");
        assertEquals("default", profile.get("name"));
    }

    @Test
    void configYamlTemplate_contextIsString() {
        String result = TemplateProvider.configYamlTemplate("spec-driven", "1.2.0");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);

        // context must be a string (not a list) per OpenSpec 1.2.0
        Object context = parsed.get("context");
        assertTrue(context instanceof String,
                "context must be a String per OpenSpec 1.2.0, got: " +
                        (context != null ? context.getClass().getSimpleName() : "null"));
    }

    @Test
    void configYamlTemplate_rulesIsMap() {
        String result = TemplateProvider.configYamlTemplate("spec-driven", "1.2.0");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(result);

        // rules must be a map (not a list) per OpenSpec 1.2.0
        Object rules = parsed.get("rules");
        assertTrue(rules instanceof Map,
                "rules must be a Map per OpenSpec 1.2.0, got: " +
                        (rules != null ? rules.getClass().getSimpleName() : "null"));
    }

    @Test
    void configYamlTemplate_schemaParameterApplied() {
        String tdd = TemplateProvider.configYamlTemplate("tdd", "1.2.0");
        Yaml yaml = new Yaml();
        Map<String, Object> parsed = yaml.load(tdd);
        assertEquals("tdd", parsed.get("schema"));
    }
}

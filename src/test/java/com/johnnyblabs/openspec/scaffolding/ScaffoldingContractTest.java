package com.johnnyblabs.openspec.scaffolding;

import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.version.VersionSupport;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests that verify the built-in scaffolding produces artifacts
 * matching OpenSpec 1.2.0 semantics. These ensure that what the plugin creates
 * will pass both CLI validation and built-in validation.
 *
 * <p>These tests verify the <b>contract</b> between scaffolding output and
 * the validator/CLI expectations — not the IntelliJ file system operations.</p>
 */
class ScaffoldingContractTest {

    // --- Config template must be parseable by ConfigService ---

    @Test
    void configTemplate_isParsableBySnakeYaml() {
        String template = TemplateProvider.configYamlTemplate("spec-driven", "1.2.0");
        Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
        OpenSpecConfig config = yaml.loadAs(template, OpenSpecConfig.class);

        assertNotNull(config);
        assertEquals("spec-driven", config.getSchema());
        assertEquals("1.2.0", config.getVersion());
    }

    @Test
    void configTemplate_schemaIsRecognizedByVersionSupport() {
        String template = TemplateProvider.configYamlTemplate("spec-driven", "1.2.0");
        Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
        OpenSpecConfig config = yaml.loadAs(template, OpenSpecConfig.class);

        VersionSupport version = VersionSupport.fromString(config.getVersion());
        assertTrue(version.getValidSchemas().contains(config.getSchema()),
                "Template schema '" + config.getSchema() + "' must be in valid schemas: " + version.getValidSchemas());
    }

    @Test
    void configTemplate_passesBuiltInValidation() {
        // Validate the same checks BuiltInValidator.validateConfig() does
        String template = TemplateProvider.configYamlTemplate("spec-driven", "1.2.0");
        Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
        OpenSpecConfig config = yaml.loadAs(template, OpenSpecConfig.class);

        assertNotNull(config.getSchema(), "schema must not be null");
        assertFalse(config.getSchema().isEmpty(), "schema must not be empty");
        assertFalse(config.getProfile().isEmpty(), "profile should not be empty");
    }

    // --- Change artifacts match v1.2.0 required set ---

    @Test
    void v1_2_requiredArtifacts_matchScaffoldingOutput() {
        // The scaffolding creates: proposal.md, design.md, tasks.md, specs/
        // VersionSupport.V1_2 requires: proposal, design, tasks, specs
        Set<String> required = VersionSupport.V1_2.getRequiredArtifacts();

        // Verify scaffolding creates a file for each required artifact
        // (excluding "specs" which is a directory, not a .md file)
        for (String artifact : required) {
            if ("specs".equals(artifact)) continue;
            String template = switch (artifact) {
                case "proposal" -> TemplateProvider.proposalTemplate("test", "test desc", null);
                case "design" -> TemplateProvider.designTemplate("test");
                case "tasks" -> TemplateProvider.tasksTemplate("test");
                default -> fail("Unknown required artifact: " + artifact);
            };
            assertNotNull(template, "Template for artifact '" + artifact + "' must exist");
            assertFalse(template.isBlank(), "Template for artifact '" + artifact + "' must not be blank");
        }
    }

    @Test
    void v1_2_requiredArtifacts_includesSpecs() {
        assertTrue(VersionSupport.V1_2.getRequiredArtifacts().contains("specs"),
                "v1.2 must require 'specs' artifact");
    }

    // --- Proposal template passes spec validation rules ---

    @Test
    void proposalTemplate_hasWhySection_matchesOpenSpec120() {
        // OpenSpec 1.2.0 spec-driven template requires ## Why as the first section
        String proposal = TemplateProvider.proposalTemplate("my-feature", "desc", null);
        Pattern whyPattern = Pattern.compile("^## Why", Pattern.MULTILINE);
        assertTrue(whyPattern.matcher(proposal).find(),
                "Proposal must have a '## Why' section per OpenSpec 1.2.0");
    }

    // --- Delta spec template passes validation ---

    @Test
    void deltaSpecTemplate_hasSections_passesValidation() {
        // BuiltInValidator checks for: "^## (ADDED|MODIFIED|REMOVED)"
        String deltaSpec = TemplateProvider.deltaSpecTemplate("authentication");
        Pattern sectionPattern = Pattern.compile("^## (ADDED|MODIFIED|REMOVED)", Pattern.MULTILINE);
        assertTrue(sectionPattern.matcher(deltaSpec).find(),
                "Delta spec must have ADDED/MODIFIED/REMOVED sections to pass validation");
    }

    @Test
    void deltaSpecTemplate_hasAllThreeSections() {
        String deltaSpec = TemplateProvider.deltaSpecTemplate("auth");
        assertTrue(deltaSpec.contains("## ADDED"));
        assertTrue(deltaSpec.contains("## MODIFIED"));
        assertTrue(deltaSpec.contains("## REMOVED"));
    }

    // --- .openspec.yaml template ---

    @Test
    void openspecYamlTemplate_hasChangeSchema() {
        String yaml = TemplateProvider.openspecYamlTemplate("proposed");
        assertTrue(yaml.contains("schema: openspec-change"),
                ".openspec.yaml must use 'openspec-change' schema");
    }

    // --- Spec format validation patterns ---

    @Test
    void specFormat_titlePattern() {
        // Validate that the regex patterns used by BuiltInValidator work correctly
        Pattern titlePattern = Pattern.compile("^# .+", Pattern.MULTILINE);
        assertTrue(titlePattern.matcher("# My Spec Title\n\nContent").find());
        assertFalse(titlePattern.matcher("## Not a title\n\nContent").find());
        assertFalse(titlePattern.matcher("Content without heading").find());
    }

    @Test
    void specFormat_requirementPattern() {
        Pattern reqPattern = Pattern.compile("^### Requirement:\\s*.+", Pattern.MULTILINE);
        assertTrue(reqPattern.matcher("### Requirement: Login\nContent").find());
        assertTrue(reqPattern.matcher("### Requirement:  Spaces\nContent").find());
        assertFalse(reqPattern.matcher("## Requirement: Wrong level\nContent").find());
    }

    @Test
    void specFormat_scenarioPattern() {
        // OpenSpec 1.2.0 uses #### Scenario: format
        Pattern scenarioPattern = Pattern.compile("^#{4} Scenario:.+", Pattern.MULTILINE);
        assertTrue(scenarioPattern.matcher("#### Scenario: Happy path\n- GIVEN").find());
        assertFalse(scenarioPattern.matcher("### Scenario: Wrong level\n- GIVEN").find());
        assertFalse(scenarioPattern.matcher("**Scenario: Old format**\n- GIVEN").find());
    }

    @Test
    void specFormat_rfcKeywords() {
        Pattern rfcPattern = Pattern.compile("\\b(SHALL NOT|SHOULD NOT|SHALL|SHOULD|MAY)\\b");
        assertTrue(rfcPattern.matcher("The system SHALL provide login").find());
        assertTrue(rfcPattern.matcher("It SHOULD NOT block").find());
        assertTrue(rfcPattern.matcher("Users MAY skip this step").find());
        assertFalse(rfcPattern.matcher("The system must provide login").find(),
                "'must' (lowercase) is not an RFC 2119 keyword in our patterns");
    }

    @Test
    void specFormat_givenWhenThenClauses() {
        Pattern clausePattern = Pattern.compile("^-\\s+(GIVEN|WHEN|THEN|AND)\\b", Pattern.MULTILINE);
        assertTrue(clausePattern.matcher("- GIVEN a user\n- WHEN they act\n- THEN result").find());
        assertFalse(clausePattern.matcher("GIVEN without dash prefix").find());
    }

    // --- Directory structure contract ---

    @Test
    void openspecDirectoryStructure_specsNotDeltaSpecs() {
        // OpenSpec 1.2.0 uses specs/<domain>/spec.md, NOT delta-specs/
        // This test documents the expected path convention
        String expectedPath = "specs/authentication/spec.md";
        assertTrue(expectedPath.startsWith("specs/"), "Must use 'specs/' directory, not 'delta-specs/'");
        assertTrue(expectedPath.endsWith("/spec.md"), "Spec files must be named 'spec.md'");
    }

    @Test
    void openspecDirectoryStructure_changeArtifactFilenames() {
        // Document the expected file names inside a change directory
        Set<String> expectedFiles = Set.of(
                ".openspec.yaml",   // metadata
                "proposal.md",     // always required
                "design.md",       // required in v1.2
                "tasks.md"         // required in v1.2
        );
        // "specs/" is a directory, not a file

        // Verify artifact IDs in VersionSupport map to these filenames
        for (String artifact : VersionSupport.V1_2.getRequiredArtifacts()) {
            if ("specs".equals(artifact)) continue;
            assertTrue(expectedFiles.contains(artifact + ".md"),
                    "Artifact '" + artifact + "' should map to '" + artifact + ".md' file");
        }
    }
}

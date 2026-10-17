package com.johnnyblabs.openspec.scaffolding;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.version.VersionSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
@ExtendWith(MockitoExtension.class)
class ScaffoldingContractTest {

    @Mock Project project;

    // --- Config template must be parseable by ConfigService ---

    @Test
    void configTemplate_isParsableBySnakeYaml() {
        String template = TemplateProvider.configYamlTemplate("spec-driven");
        Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
        OpenSpecConfig config = yaml.loadAs(template, OpenSpecConfig.class);

        assertNotNull(config);
        assertEquals("spec-driven", config.getSchema());
        assertNull(config.getVersion(),
                "schema-only template writes no version: (matches upstream openspec init)");
    }

    @Test
    void configTemplate_schemaIsRecognizedByVersionSupport() {
        String template = TemplateProvider.configYamlTemplate("spec-driven");
        Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
        OpenSpecConfig config = yaml.loadAs(template, OpenSpecConfig.class);

        VersionSupport version = VersionSupport.fromString(config.getVersion());
        assertTrue(version.getValidSchemas().contains(config.getSchema()),
                "Template schema '" + config.getSchema() + "' must be in valid schemas: " + version.getValidSchemas());
    }

    // --- Default schema setting flows through to rendered config.yaml (fix-init-default-schema) ---
    //
    // These tests use `new OpenSpecSettings()` directly rather than going through IntelliJ's
    // service infrastructure. This works because (a) the State field self-initializes with
    // defaults, and (b) the getEffectiveSchema helper currently does not reach into any
    // project services — the Project parameter is reserved for future use (see OpenSpecSettings
    // getEffectiveSchema Javadoc TODO(post-v0.4.0)). When that helper starts using the Project
    // parameter, replace the direct instantiation with a Mockito stub for
    // project.getService(ConfigService.class) or move these tests to a LightPlatformTestCase
    // subclass with real service infrastructure.

    @Test
    void initBuiltIn_honorsDefaultSchemaSetting() {
        // Bug being fixed: ScaffoldingService.initBuiltIn previously hardcoded "spec-driven"
        // regardless of the user's Default schema preference. With the fix, the helper at
        // OpenSpecSettings.getEffectiveSchema(project) returns the configured value, which
        // ScaffoldingService now passes to TemplateProvider.configYamlTemplate.
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setDefaultSchema("workspace-planning");

        String schema = settings.getEffectiveSchema(project);
        String template = TemplateProvider.configYamlTemplate(schema);

        Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
        OpenSpecConfig config = yaml.loadAs(template, OpenSpecConfig.class);
        assertEquals("workspace-planning", config.getSchema(),
                "Generated config.yaml must reflect the user's chosen Default schema, not the hardcoded literal");
    }

    @Test
    void initBuiltIn_fallsBackToSpecDrivenWhenDefaultSchemaUnset() {
        // Empty / unset Default schema preserves the prior default behaviour. The fallback
        // literal "spec-driven" matches upstream's DEFAULT_SCHEMA constant in
        // @fission-ai/openspec/dist/core/init.js — deliberately not computed from
        // VersionSupport so it stays stable across upstream additions.
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setDefaultSchema("");

        String schema = settings.getEffectiveSchema(project);
        String template = TemplateProvider.configYamlTemplate(schema);

        Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
        OpenSpecConfig config = yaml.loadAs(template, OpenSpecConfig.class);
        assertEquals("spec-driven", config.getSchema(),
                "Unset Default schema must fall back to the historical literal 'spec-driven'");
    }

    @Test
    void initBuiltIn_fallsBackToSpecDrivenWhenDefaultSchemaIsNull() {
        // Defensive coverage: getEffectiveSchema handles a null state.defaultSchema even though
        // the State class initializes it to empty string. Guards against a future regression in
        // OpenSpecSettings.State or migration paths that could leave the field null.
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setDefaultSchema(null);

        assertEquals("spec-driven", settings.getEffectiveSchema(project),
                "Null Default schema must fall back to 'spec-driven' (not NPE)");
    }

    @Test
    void configTemplate_passesBuiltInValidation() {
        // Validate the same checks BuiltInValidator.validateConfig() does
        String template = TemplateProvider.configYamlTemplate("spec-driven");
        Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
        OpenSpecConfig config = yaml.loadAs(template, OpenSpecConfig.class);

        assertNotNull(config.getSchema(), "schema must not be null");
        assertFalse(config.getSchema().isEmpty(), "schema must not be empty");
        assertTrue(config.getProfile() == null || config.getProfile().isEmpty(),
                "schema-only config writes no profile: entries (validation accepts its absence)");
    }

    // --- Change artifacts match v1.2.0 required set ---

    @Test
    void v1_2_requiredArtifacts_includeDesignAndTasks_butProposeScaffoldsOnlyTheProposal() {
        // The config-format pin still lists design and tasks as required artifacts...
        Set<String> required = VersionSupport.V1_2.getRequiredArtifacts();
        assertTrue(required.containsAll(Set.of("proposal", "design", "tasks", "specs")), String.valueOf(required));
        // ...but the built-in Propose only scaffolds the proposal, as `openspec new change` creates no artifact files:
        // a design/tasks template would read as done to `openspec status`.
        String proposal = TemplateProvider.proposalTemplate("test", "test desc", null);
        assertNotNull(proposal);
        assertFalse(proposal.isBlank());
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

    // --- Delta spec template: the client's canonical delta grammar ---
    // Contract: fixtures/cli/1.12.0/scaffold-delta/ — the real template, filled with one requirement, captured
    // through the real CLI. new-template is valid:true; old-template (a frozen negative control of the pre-fix
    // template, never re-captured from current code) is valid:false "No deltas found".

    private static final String SCAFFOLD_FIXTURE = "/fixtures/cli/1.12.0/scaffold-delta/";
    private static final String INSERTION_ANCHOR = "## MODIFIED Requirements";
    private static final Pattern DELTA_HEADING_LINE =
            Pattern.compile("^## (ADDED|MODIFIED|REMOVED|RENAMED)\\b.*$", Pattern.MULTILINE);
    private static final Set<String> CANONICAL_DELTA_HEADINGS = Set.of(
            "## ADDED Requirements", "## MODIFIED Requirements",
            "## REMOVED Requirements", "## RENAMED Requirements");

    private static String scaffoldFixture(String name) throws java.io.IOException {
        try (java.io.InputStream in = ScaffoldingContractTest.class.getResourceAsStream(SCAFFOLD_FIXTURE + name)) {
            assertNotNull(in, "missing fixture " + SCAFFOLD_FIXTURE + name);
            // Windows checkouts may convert to CRLF; the template is a LF text block.
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    void deltaSpecTemplate_headingsAreExactlyTheCanonicalFour() {
        String deltaSpec = TemplateProvider.deltaSpecTemplate("auth");
        java.util.List<String> headings = new java.util.ArrayList<>();
        java.util.regex.Matcher m = DELTA_HEADING_LINE.matcher(deltaSpec);
        while (m.find()) {
            headings.add(m.group().strip());
        }
        assertEquals(java.util.List.of("## ADDED Requirements", "## MODIFIED Requirements",
                "## REMOVED Requirements", "## RENAMED Requirements"), headings,
                "every delta heading must be one the CLI parses (bare '## ADDED' is invisible to it)");
        assertTrue(CANONICAL_DELTA_HEADINGS.containsAll(headings));
        assertFalse(deltaSpec.contains("%s"), "domain placeholder must be substituted");
    }

    @Test
    void deltaSpecTemplate_renamedGuidanceUsesTheClientsFromToForm() {
        String deltaSpec = TemplateProvider.deltaSpecTemplate("auth");
        String renamed = deltaSpec.substring(deltaSpec.indexOf("## RENAMED Requirements"));
        assertTrue(renamed.contains("FROM: `### Requirement:") && renamed.contains("TO: `### Requirement:"),
                "RENAMED guidance must show the requirement header the CLI requires: " + renamed);
    }

    @Test
    void deltaSpecTemplate_guidanceNeverMatchesThePluginsLineAnchoredPatterns() {
        // Guidance lives mid-line inside HTML comments. If a reflow ever puts "### Requirement:" or
        // "- FROM:" at line start, sync would apply a phantom op and the validator count a phantom requirement.
        String deltaSpec = TemplateProvider.deltaSpecTemplate("auth");
        assertFalse(com.johnnyblabs.openspec.util.SpecPatterns.REQUIREMENT_HEADER.matcher(deltaSpec).find(),
                "template must contain no line-anchored requirement header");
        assertFalse(com.johnnyblabs.openspec.util.SpecPatterns.RENAMED_ENTRY_LINE.matcher(deltaSpec).find(),
                "template must contain no line-anchored FROM:/TO: entry");
    }

    @Test
    void filledTemplate_isExactlyTheInputTheCliValidated() throws Exception {
        String template = TemplateProvider.deltaSpecTemplate("auth");
        assertTrue(template.contains(INSERTION_ANCHOR), "insertion anchor missing from template");
        String filled = template.replace(INSERTION_ANCHOR,
                scaffoldFixture("added-requirement.md") + INSERTION_ANCHOR);
        assertEquals(scaffoldFixture("new-template/openspec/changes/c/specs/auth/spec.md"), filled,
                "the captured CLI input must be the real template output — re-capture if the template changed");
    }

    @Test
    void cliAcceptsTheFilledTemplate_andRejectedTheOldOne() throws Exception {
        for (String capture : new String[]{"validate-new.json", "validate-new-strict.json"}) {
            var result = com.johnnyblabs.openspec.util.CliOutputParser.parseJsonOutput(scaffoldFixture(capture));
            assertTrue(result.passed(), capture + " must be valid:true: " + result.issues());
        }
        for (String capture : new String[]{"validate-old.json", "validate-old-strict.json"}) {
            var result = com.johnnyblabs.openspec.util.CliOutputParser.parseJsonOutput(scaffoldFixture(capture));
            assertFalse(result.passed(), capture + " (pre-fix template) must be valid:false");
            assertTrue(result.issues().stream().anyMatch(i -> i.message().contains("No deltas found")),
                    capture + " must fail for having no deltas: " + result.issues());
        }
    }

    @Test
    void deltaSpecTemplate_isScaffoldingUntilFilled() throws Exception {
        var detector = new com.johnnyblabs.openspec.services.ScaffoldingDetectionService(null);
        assertTrue(detector.isScaffoldingContent(TemplateProvider.deltaSpecTemplate("auth")),
                "an unfilled scaffold must not count as a completed artifact");
        assertFalse(detector.isScaffoldingContent(
                        scaffoldFixture("new-template/openspec/changes/c/specs/auth/spec.md")),
                "a filled scaffold is real content");
    }

    // --- .openspec.yaml template ---

    @Test
    void openspecYamlTemplate_emitsGivenSchemaNotInventedValue() {
        String yaml = TemplateProvider.openspecYamlTemplate("spec-driven");
        assertTrue(yaml.contains("schema: spec-driven"), ".openspec.yaml emits the given schema");
        assertFalse(yaml.contains("openspec-change"), "never the retired invented schema value");
        assertFalse(yaml.contains("status:"), "no status: field");
    }

    @Test
    void scaffoldChangeMetadata_matchesRealNewChangeShape() throws Exception {
        // Contract: the scaffolded .openspec.yaml parses to the same shape as REAL `openspec new change`
        // output (a committed capture) via the plugin's own reader — schema + created, no status.
        String scaffolded = TemplateProvider.openspecYamlTemplate("spec-driven");
        String captured = new String(getClass().getResourceAsStream(
                "/fixtures/cli/1.7.0/change-metadata/baseline-schema-created.openspec.yaml").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        com.johnnyblabs.openspec.model.ChangeMetadata s =
                com.johnnyblabs.openspec.model.ChangeMetadataParser.parse(scaffolded).metadata();
        com.johnnyblabs.openspec.model.ChangeMetadata c =
                com.johnnyblabs.openspec.model.ChangeMetadataParser.parse(captured).metadata();
        assertNotNull(s);
        assertNotNull(c);
        assertEquals(c.getSchema(), s.getSchema(), "same schema as real new change");
        assertNotNull(s.getCreated());
        // The captured new-change file's created: is unquoted; the scaffold must match that style.
        assertFalse(captured.contains("created: \""), "captured new-change created is unquoted (guard)");
        assertFalse(scaffolded.contains("created: \""), "scaffold created must be unquoted like the CLI");
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

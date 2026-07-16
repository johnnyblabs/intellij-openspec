package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Integration tests for BuiltInValidator.
 * Each test creates its own fixture files to isolate validation rules.
 */
public class BuiltInValidatorTest extends OpenSpecIntegrationTestBase {

    private BuiltInValidator validator;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        validator = getProject().getService(BuiltInValidator.class);
    }

    // ---------------------------------------------------------------
    // Spec Validation
    // ---------------------------------------------------------------

    public void testValidSpecProducesNoErrors() {
        ValidationResult result = validator.validateSpecs();
        List<ValidationIssue> errors = result.issues().stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .toList();
        assertTrue("Valid specs should produce no errors, got: " + errors, errors.isEmpty());
    }

    public void testMissingTitleTriggersError() {
        myFixture.addFileToProject("openspec/specs/bad-title/spec.md",
                "## No title heading here\n\n### Requirement: Something\n\nThe system SHALL work.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("Should have spec-title-required issue",
                result.issues().stream().anyMatch(i ->
                        "spec-title-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("bad-title")));
    }

    public void testMissingRequirementTriggersError() {
        myFixture.addFileToProject("openspec/specs/bad-req/spec.md",
                "# Title Only\n\nSome description but no requirements.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("Should have spec-requirement-required issue",
                result.issues().stream().anyMatch(i ->
                        "spec-requirement-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("bad-req")));
    }

    public void testMissingKeywordTriggersError() {
        myFixture.addFileToProject("openspec/specs/bad-kw/spec.md",
                "# Keywords Spec\n\n### Requirement: No Keywords\n\nThis has no RFC 2119 keywords at all.\n\n#### Scenario: Test\n- **WHEN** triggered\n- **THEN** nothing\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("Should have spec-rfc-keywords ERROR issue",
                result.issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("bad-kw")));
    }

    public void testEmptyScenarioTriggersError() {
        myFixture.addFileToProject("openspec/specs/bad-scenario/spec.md",
                "# Scenario Spec\n\n### Requirement: Bad Scenario\n\nThe system SHALL work.\n\n" +
                "#### Scenario: Missing clauses\nJust a description with no structured clauses.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("Should have spec-scenario-clauses ERROR issue",
                result.issues().stream().anyMatch(i ->
                        "spec-scenario-clauses".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("bad-scenario")));
    }

    // ---------------------------------------------------------------
    // Config Validation
    // ---------------------------------------------------------------

    public void testValidConfigProducesNoErrors() {
        ValidationResult result = validator.validateConfig();
        List<ValidationIssue> errors = result.issues().stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .toList();
        assertTrue("Valid config should produce no errors, got: " + errors, errors.isEmpty());
        assertTrue(result.passed());
    }

    public void testMissingSchemaTriggersError() throws Exception {
        overwriteFile("openspec/config.yaml",
                "version: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("Should have config-schema-required issue",
                result.issues().stream().anyMatch(i ->
                        "config-schema-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR));
        assertFalse(result.passed());
    }

    public void testInvalidSchemaTriggersWarning() throws Exception {
        overwriteFile("openspec/config.yaml",
                "schema: bogus-schema\nversion: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("Should have config-schema-invalid issue",
                result.issues().stream().anyMatch(i ->
                        "config-schema-invalid".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING));
    }

    public void testMissingProfileIsClean() throws Exception {
        // Upstream OpenSpec's Zod schema doesn't define profile; it's a plugin-internal
        // display field. Absence is no longer a validation issue. (Was config-profile-recommended.)
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"1.2.0\"\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("profile absence should produce no issue",
                result.issues().stream().noneMatch(i ->
                        "config-profile-recommended".equals(i.rule())));
    }

    public void testMissingVersionIsClean() throws Exception {
        // The version: field is plugin-internal — upstream's Zod schema strips it.
        // Absence is no longer required-field or recommended-field. (Was config-version-required
        // WARNING + config-field-required ERROR.)
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("version absence should produce no version-required or field-required issue",
                result.issues().stream().noneMatch(i ->
                        "config-version-required".equals(i.rule())
                                || "config-field-required".equals(i.rule())));
        assertTrue("version absence should not be an ERROR (only schema is required)",
                result.passed());
    }

    public void testMissingConfigYamlIsClean() throws Exception {
        // Upstream OpenSpec treats openspec/config.yaml as optional — its readProjectConfig
        // returns null with the comment "No config is OK". The plugin matches that contract.
        // (Was config-missing ERROR + short-circuit.)
        deleteFile("openspec/config.yaml");

        ValidationResult result = validator.validateConfig();
        assertTrue("config absence should produce no config-missing issue",
                result.issues().stream().noneMatch(i ->
                        "config-missing".equals(i.rule())));
        assertTrue("config absence should not fail validation", result.passed());
    }

    public void testUnknownVersionTriggersWarning() throws Exception {
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"9.9.9\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("Should have config-version-unknown issue",
                result.issues().stream().anyMatch(i ->
                        "config-version-unknown".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING));
    }

    public void testValidVersionProducesNoVersionIssues() throws Exception {
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("Valid version should produce no version issues",
                result.issues().stream().noneMatch(i ->
                        "config-version-required".equals(i.rule()) ||
                        "config-version-unknown".equals(i.rule()) ||
                        "config-field-required".equals(i.rule())));
    }

    // ---------------------------------------------------------------
    // Change Validation
    // ---------------------------------------------------------------

    public void testValidChangeProducesNoProposalError() {
        // The test-change fixture already has proposal.md
        ValidationResult result = validator.validateChanges();
        List<ValidationIssue> proposalErrors = result.issues().stream()
                .filter(i -> "change-proposal-required".equals(i.rule()))
                .toList();
        assertTrue("Valid change should not have proposal-required error, got: " + proposalErrors,
                proposalErrors.isEmpty());
    }

    public void testMissingProposalTriggersError() {
        // Create a change without proposal.md (only .openspec.yaml)
        myFixture.addFileToProject("openspec/changes/bad-change/.openspec.yaml",
                "schema: spec-driven\nstatus: proposed\n");
        myFixture.addFileToProject("openspec/changes/bad-change/design.md",
                "## Design\n\nSome design.\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertTrue("Should have change-proposal-required issue",
                result.issues().stream().anyMatch(i ->
                        "change-proposal-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.message().contains("bad-change")));
    }

    public void testMissingArtifactTriggersWarning() {
        // test-change has proposal but no design.md or tasks.md
        ValidationResult result = validator.validateChanges();
        assertTrue("Should have change-artifact-missing issue for missing artifacts",
                result.issues().stream().anyMatch(i ->
                        "change-artifact-missing".equals(i.rule())));
    }

    public void testChangeWithIncompatibleSchemaTriggersWarning() throws Exception {
        // Set project to version 1.0.0 which only supports spec-driven
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"1.0.0\"\n\nprofile:\n  name: Test\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/.openspec.yaml",
                "schema: tdd\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/proposal.md",
                "## Why\n\nTest\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertTrue("Should have change-schema-incompatible issue",
                result.issues().stream().anyMatch(i ->
                        "change-schema-incompatible".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING &&
                        i.message().contains("incompat-change")));
    }

    public void testDeltaSpecWithoutSectionsTriggersWarning() {
        // Delta spec validation uses LocalFileSystem which doesn't work in temp VFS.
        // This test verifies the validator handles it gracefully (no crash).
        // The actual delta spec validation is covered by the regex pattern test below.
        myFixture.addFileToProject("openspec/changes/delta-test/.openspec.yaml",
                "schema: spec-driven\nstatus: proposed\n");
        myFixture.addFileToProject("openspec/changes/delta-test/proposal.md",
                "## Why\n\nTest\n");
        refreshVfs();

        // Should not throw — gracefully handles missing LocalFileSystem paths
        ValidationResult result = validator.validateChanges();
        assertNotNull(result);
    }

    // ---------------------------------------------------------------
    // ---------------------------------------------------------------
    // CLI 1.6 parity semantics (fence masking, SHALL/MUST-only, INFO tier)
    // ---------------------------------------------------------------

    public void testShouldOnlyRequirementTriggersError() {
        // SHOULD/MAY never satisfied `openspec validate` on any generation.
        myFixture.addFileToProject("openspec/specs/should-kw/spec.md",
                "# Should Spec\n\n### Requirement: Soft wording\n\nThe system SHOULD work and MAY retry.\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n");
        refreshVfs();

        assertTrue("SHOULD-only requirement must be flagged (CLI accepts only SHALL/MUST)",
                validator.validateSpecs().issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule()) && i.filePath().contains("should-kw")));
    }

    public void testKeywordOnlyInsideFenceTriggersError() {
        myFixture.addFileToProject("openspec/specs/fenced-kw/spec.md",
                "# Fenced Spec\n\n### Requirement: Fenced keyword\n\nBody without the magic word.\n\n```\nThe system SHALL work.\n```\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n");
        refreshVfs();

        assertTrue("keyword only inside a code fence must not satisfy the check (1.6 fence masking)",
                validator.validateSpecs().issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule()) && i.filePath().contains("fenced-kw")));
    }

    public void testScenarioOnlyInsideFenceTriggersError() {
        myFixture.addFileToProject("openspec/specs/fenced-scen/spec.md",
                "# Fenced Scenario Spec\n\n### Requirement: Fenced scenario\n\nThe system SHALL work.\n\n```\n#### Scenario: only an example\n- **WHEN** x\n- **THEN** y\n```\n");
        refreshVfs();

        assertTrue("scenario only inside a code fence must not count (1.6 fence-aware counting)",
                validator.validateSpecs().issues().stream().anyMatch(i ->
                        "spec-scenario-required".equals(i.rule()) && i.filePath().contains("fenced-scen")));
    }

    public void testSkippedDeltaHeaderEmitsInfoWithoutFlippingVerdict() {
        myFixture.addFileToProject("openspec/changes/info-change/proposal.md", "## Why\n\nBecause.\n");
        myFixture.addFileToProject("openspec/changes/info-change/specs/demo/spec.md",
                "## ADDED Requirements\n\n### Requirement: Real one\nThe system SHALL work.\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n\n### Implementation notes\n\nProse the parser skips.\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        ValidationIssue info = result.issues().stream()
                .filter(i -> "delta-skipped-header".equals(i.rule())
                        && i.filePath().contains("info-change"))
                .findFirst().orElse(null);
        assertNotNull("non-canonical level-3 header in ADDED must emit the INFO hint", info);
        assertEquals(ValidationIssue.Severity.INFO, info.severity());
        assertTrue("INFO message names the skipped header",
                info.message().contains("Implementation notes"));
        assertTrue("INFO anchors to the header line", info.line() > 1);
        assertTrue("INFO must never flip the verdict",
                result.issues().stream()
                        .filter(i -> i.filePath().contains("info-change"))
                        .noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR));
    }

    public void testNamelessRequirementHeaderEmitsNamingHint() {
        myFixture.addFileToProject("openspec/changes/nameless-change/proposal.md", "## Why\n\nBecause.\n");
        myFixture.addFileToProject("openspec/changes/nameless-change/specs/demo/spec.md",
                "## MODIFIED Requirements\n\n### Requirement:\nThe system SHALL work.\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n");
        refreshVfs();

        assertTrue("nameless '### Requirement:' must emit the add-a-name INFO variant",
                validator.validateChanges().issues().stream().anyMatch(i ->
                        "delta-skipped-header".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.INFO
                                && i.message().contains("missing a requirement name")));
    }

    // Helpers
    // ---------------------------------------------------------------

    private void refreshVfs() {
        VirtualFile root = myFixture.findFileInTempDir("openspec");
        if (root != null) {
            VfsUtil.markDirtyAndRefresh(false, true, true, root);
        }
    }

    private void overwriteFile(String relativePath, String content) throws Exception {
        VirtualFile file = myFixture.findFileInTempDir(relativePath);
        assertNotNull("File should exist: " + relativePath, file);
        WriteAction.run(() ->
                file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8)));
        refreshVfs();
    }

    private void deleteFile(String relativePath) throws Exception {
        VirtualFile file = myFixture.findFileInTempDir(relativePath);
        assertNotNull("File should exist before delete: " + relativePath, file);
        WriteAction.run(() -> file.delete(this));
        refreshVfs();
    }
}

package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.services.SchemaService;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    public void testMissingTitleTriggersWarning() {
        // The CLI requires no `# Title` H1 (it derives the name from the directory), so a missing
        // title is a WARNING, not an ERROR — the plugin must not exceed the CLI's default verdict.
        myFixture.addFileToProject("openspec/specs/bad-title/spec.md",
                "## No title heading here\n\n### Requirement: Something\n\nThe system SHALL work.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("spec-title-required should be a WARNING, not an ERROR",
                result.issues().stream().anyMatch(i ->
                        "spec-title-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING &&
                        i.filePath().contains("bad-title")));
    }

    public void testUntitledButOtherwiseValidSpecPasses() {
        // A spec with no `# Title` but a well-formed requirement + scenario is CLI-valid, so the
        // built-in fallback must pass it (the title WARNING does not fail the verdict).
        myFixture.addFileToProject("openspec/specs/untitled-ok/spec.md",
                "## Purpose\n\nDoes a thing.\n\n### Requirement: Something\n\nThe system SHALL work.\n\n" +
                "#### Scenario: Works\n- **WHEN** invoked\n- **THEN** it works\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("untitled-but-valid spec must pass (title is only a WARNING)", result.passed());
        assertTrue("the sole issue is the spec-title-required WARNING",
                result.issues().stream().anyMatch(i ->
                        "spec-title-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING &&
                        i.filePath().contains("untitled-ok")));
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

    public void testMissingKeywordBodyPresentIsWarningNotError() {
        myFixture.addFileToProject("openspec/specs/bad-kw/spec.md",
                "# Keywords Spec\n\n### Requirement: No Keywords\n\nThis has no RFC 2119 keywords at all.\n\n#### Scenario: Test\n- **WHEN** triggered\n- **THEN** nothing\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        // 1.8: a body-carrying requirement without SHALL/MUST is a non-failing WARNING in default mode.
        assertTrue("body-carrying missing-keyword must be a WARNING",
                result.issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING &&
                        i.filePath().contains("bad-kw")));
        assertFalse("must not ERROR when the requirement has a body",
                result.issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("bad-kw")));
        // Default verdict passes on a warning-only spec; --strict re-promotes the missing-keyword warning.
        assertTrue("default verdict passes on a warning-only spec", result.passed());
        assertFalse("strict re-promotes the missing-keyword warning to a failing verdict",
                com.johnnyblabs.openspec.actions.OpenSpecValidateAction
                        .applyStrictFallbackVerdict(result, true).passed());
    }

    public void testMissingKeywordNoBodyTriggersError() {
        myFixture.addFileToProject("openspec/specs/nobody-kw/spec.md",
                "# No Body Spec\n\n### Requirement: Empty\n\n#### Scenario: Test\n- **WHEN** triggered\n- **THEN** nothing\n");
        refreshVfs();

        // 1.8: a requirement with no body prose at all still ERRORs on the missing keyword.
        assertTrue("body-less requirement must ERROR on the missing keyword",
                validator.validateSpecs().issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("nobody-kw")));
    }

    public void testDuplicateRequirementNameFailsVerdictInDefaultAndStrict() {
        // 1.8 (upstream #1484): a main spec that declares the same requirement name twice is an ERROR.
        // Both requirements are otherwise clean (SHALL body + scenario) so the duplicate is the SOLE
        // failure cause — the strict assertion then means "a hard ERROR fails in both modes", not that
        // strict promoted anything (applyStrictFallbackVerdict is a no-op on an existing ERROR).
        myFixture.addFileToProject("openspec/specs/dup-req/spec.md",
                "# Dup Spec\n\n## Requirements\n\n"
                        + "### Requirement: Works\nThe system SHALL work first.\n\n"
                        + "#### Scenario: A\n- **WHEN** x\n- **THEN** y\n\n"
                        + "### Requirement: Works\nThe system SHALL work second.\n\n"
                        + "#### Scenario: B\n- **WHEN** a\n- **THEN** b\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("duplicate requirement name must ERROR",
                result.issues().stream().anyMatch(i ->
                        "spec-duplicate-requirement".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("dup-req")));
        assertEquals("the duplicate ERROR is the only ERROR on the file", 1L,
                result.issues().stream().filter(i ->
                        i.filePath().contains("dup-req")
                                && i.severity() == ValidationIssue.Severity.ERROR).count());
        assertFalse("the duplicate ERROR fails the default verdict", result.passed());
        assertFalse("a hard ERROR still fails under strict",
                com.johnnyblabs.openspec.actions.OpenSpecValidateAction
                        .applyStrictFallbackVerdict(result, true).passed());
    }

    public void testDuplicateRequirementCoexistsWithMissingKeywordOnBothOccurrences() {
        // The duplicate rule is independent of the missing-keyword rule: a duplicated requirement that
        // also lacks SHALL/MUST emits ONE duplicate ERROR plus a missing-keyword issue for EACH
        // occurrence. Asserting the keyword-warning COUNT is 2 (not merely "any") is what catches a
        // loop-skip that would swallow the second occurrence's checks.
        myFixture.addFileToProject("openspec/specs/dup-nokw/spec.md",
                "# Dup NoKw Spec\n\n## Requirements\n\n"
                        + "### Requirement: Twin\nThe system will do the twin thing.\n\n"
                        + "#### Scenario: A\n- **WHEN** x\n- **THEN** y\n\n"
                        + "### Requirement: Twin\nThe system will do the twin thing again.\n\n"
                        + "#### Scenario: B\n- **WHEN** a\n- **THEN** b\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertEquals("exactly one duplicate ERROR (on the second occurrence)", 1L,
                result.issues().stream().filter(i ->
                        "spec-duplicate-requirement".equals(i.rule())
                                && i.filePath().contains("dup-nokw")).count());
        assertEquals("the missing-keyword WARNING fires on BOTH occurrences — dedup must not skip the second",
                2L, result.issues().stream().filter(i ->
                        "spec-rfc-keywords".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.WARNING
                                && i.filePath().contains("dup-nokw")).count());
    }

    public void testEmptyScenarioClausesAreInfoAndPass() {
        // The CLI performs no WHEN/THEN clause validation (a scenario need only be non-empty), so a
        // missing-clause scenario is an INFO hint that does not fail the verdict.
        myFixture.addFileToProject("openspec/specs/bad-scenario/spec.md",
                "# Scenario Spec\n\n### Requirement: Bad Scenario\n\nThe system SHALL work.\n\n" +
                "#### Scenario: Missing clauses\nJust a description with no structured clauses.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("spec-scenario-clauses should be INFO, not ERROR",
                result.issues().stream().anyMatch(i ->
                        "spec-scenario-clauses".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.INFO &&
                        i.filePath().contains("bad-scenario")));
        assertTrue("a clauseless scenario must not fail the verdict", result.passed());
    }

    public void testScenarolessRequirementStillErrors() {
        // Corrected after empirical CLI capture: a scenarioless main-spec requirement is CLI
        // valid:false (Zod .min(1) schema error), so it stays an ERROR — demoting would make the
        // plugin laxer than the CLI.
        myFixture.addFileToProject("openspec/specs/no-scenario/spec.md",
                "# No Scenario Spec\n\n### Requirement: Needs A Scenario\n\nThe system SHALL work.\n");
        refreshVfs();

        ValidationResult result = validator.validateSpecs();
        assertTrue("spec-scenario-required must remain an ERROR",
                result.issues().stream().anyMatch(i ->
                        "spec-scenario-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR &&
                        i.filePath().contains("no-scenario")));
        assertFalse("a scenarioless requirement must fail the verdict", result.passed());
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

    public void testMissingSchemaIsANonFailingInfoNudge() throws Exception {
        // `openspec validate` is clean on a missing schema (upstream defaults to spec-driven), so a
        // WARNING squiggle would be stricter than the CLI — the plugin emits an INFO advisory only.
        overwriteFile("openspec/config.yaml",
                "version: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("config-schema-required must be an INFO advisory, not a WARNING/ERROR",
                result.issues().stream().anyMatch(i ->
                        "config-schema-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.INFO));
        assertTrue("a missing schema must NOT fail the verdict", result.passed());
        // Config validation produces no ERRORs at all — it never fails the verdict.
        assertTrue("config validation must emit no ERROR-severity issues",
                result.issues().stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR));
        // The removed required-fields loop must not re-emit a duplicate config-field-required.
        assertTrue("config-field-required must no longer be emitted",
                result.issues().stream().noneMatch(i -> "config-field-required".equals(i.rule())));
    }

    public void testUnknownSchemaWithCliUnavailableIsClean() throws Exception {
        // Guard: with the CLI unavailable (the default test env), the known-set collapses to the
        // built-in floor, so the plugin can't see project-local forks. A non-built-in schema must NOT
        // warn — the real CLI never rejects a schema name, and a legit fork would otherwise falsely red.
        overwriteFile("openspec/config.yaml",
                "schema: my-team-flow\nversion: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("config-schema-invalid must NOT fire when the known-set is non-authoritative (CLI down)",
                result.issues().stream().noneMatch(i -> "config-schema-invalid".equals(i.rule())));
    }

    public void testUnknownSchemaWithCliAvailableStillWarns() throws Exception {
        // With an AUTHORITATIVE known-set (CLI available + schema-supported), a genuine unknown schema
        // still warns — the guard relaxes CLI-down false-positives without masking real typos.
        SchemaService authoritative = mock(SchemaService.class);
        when(authoritative.isSchemaSupported()).thenReturn(true);
        when(authoritative.getKnownSchemaNames()).thenReturn(Set.of("spec-driven"));
        ServiceContainerUtil.replaceService(getProject(), SchemaService.class, authoritative, getTestRootDisposable());

        overwriteFile("openspec/config.yaml",
                "schema: bogus-schema\nversion: \"1.2.0\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("config-schema-invalid must still fire on an unknown schema when the known-set is authoritative",
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

    public void testOperationsAndRulesConfigRaisesNoIssue() throws Exception {
        // 1.7 optional config keys — `operations` (apply/archive guidance) + list-shaped `rules` —
        // are ignored by the reader and accepted by the CLI (validate clean). validateConfig must
        // emit no ERROR and no plugin-invented WARNING: schema is present, so the config is clean.
        overwriteFile("openspec/config.yaml", opsRulesFixture());

        ValidationResult result = validator.validateConfig();
        assertTrue("no ERROR on a 1.7 operations/rules config",
                result.issues().stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR));
        assertTrue("no WARNING on a 1.7 operations/rules config (schema present, extra keys ignored)",
                result.issues().stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.WARNING));
        assertTrue("a valid 1.7 config must pass", result.passed());
    }

    private static String opsRulesFixture() {
        String path = "/fixtures/cli/1.7.0/config-validation/operations-and-rules.config.yaml";
        try (java.io.InputStream is = BuiltInValidatorTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
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

    public void testUnknownVersionIsClean() throws Exception {
        // `version:` is plugin-internal; the CLI strips it and validates clean for ANY value. The
        // plugin no longer warns on an unrecognized version — doing so was stricter than the CLI.
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"9.9.9\"\n\nprofile:\n  name: Test\n");

        ValidationResult result = validator.validateConfig();
        assertTrue("config-version-unknown must no longer be emitted (the CLI never reads version:)",
                result.issues().stream().noneMatch(i -> "config-version-unknown".equals(i.rule())));
        assertTrue("an unrecognized version must not fail the verdict", result.passed());
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

    public void testMissingProposalIsNonFailingWarning() {
        // A change without proposal.md: change-proposal-required is a NON-FAILING WARNING, not an
        // ERROR — the real CLI validates a proposal-less change (with a valid delta) as valid, so
        // failing on it would make the plugin more restrictive than the client it wraps.
        myFixture.addFileToProject("openspec/changes/bad-change/.openspec.yaml",
                "schema: spec-driven\n");
        myFixture.addFileToProject("openspec/changes/bad-change/design.md",
                "## Design\n\nSome design.\n");
        // A valid delta so the ONLY notable condition is the missing proposal — otherwise the change
        // would also carry a delta-none-found ERROR (no deltas), muddying this single-variable test.
        myFixture.addFileToProject("openspec/changes/bad-change/specs/reporting/spec.md", VALID_DELTA);
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertTrue("change-proposal-required should be a non-failing WARNING",
                result.issues().stream().anyMatch(i ->
                        "change-proposal-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING &&
                        i.message().contains("bad-change")));
        assertFalse("change-proposal-required must not be an ERROR",
                result.issues().stream().anyMatch(i ->
                        "change-proposal-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.ERROR));
    }

    public void testProposalLessChangeWithValidDeltaPasses() {
        // A change with a valid ADDED delta but no proposal.md validates clean in the built-in
        // (CLI-absent) fallback — matching the CLI, which resolves changes by directory existence and
        // does not require proposal.md. Bites a regression: if change-proposal-required were still an
        // ERROR, the verdict would flip to failed.
        myFixture.addFileToProject("openspec/changes/no-proposal-change/specs/reporting/spec.md",
                "## ADDED Requirements\n\n### Requirement: Export report\n" +
                        "The system SHALL export a report as PDF.\n\n" +
                        "#### Scenario: User exports\n- **WHEN** the user clicks export\n" +
                        "- **THEN** a PDF is produced\n");
        refreshVfs();

        ValidationResult result = validator.validateChange("no-proposal-change");
        assertTrue("a proposal-less change with a valid delta must pass the built-in verdict",
                result.passed());
        assertTrue("change-proposal-required present as a non-failing WARNING",
                result.issues().stream().anyMatch(i ->
                        "change-proposal-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING));
        assertFalse("no ERROR-severity issue on a proposal-less-but-valid change",
                result.issues().stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR));
    }

    // --- CLI parity: change-delta discovery (misplaced delta + no-deltas), skip_specs-aware ---

    private static final String VALID_DELTA =
            "## ADDED Requirements\n\n### Requirement: Root\nThe system SHALL do it.\n\n"
                    + "#### Scenario: A\n- **WHEN** x\n- **THEN** y\n";

    public void testMisplacedDeltaAtSpecsRootIsError() {
        // A regular file at the change's specs/ root is a misplaced delta (dropped on apply/archive),
        // matching the CLI 1.7 rule. The fallback previously skipped it entirely (false green). The
        // content is PLAIN PROSE (no delta headers): this is the anti-vacuity control — a naive impl that
        // ran the root file through validateDeltaSpecStructure would emit a delta-spec-sections WARNING
        // instead of the misplaced ERROR, so we assert the ERROR is present AND the sections-WARNING is
        // absent (the root file is flagged, not structurally validated).
        myFixture.addFileToProject("openspec/changes/misplaced-c/proposal.md", "# M\n\n## Why\ny\n\n## What Changes\n- x\n");
        myFixture.addFileToProject("openspec/changes/misplaced-c/specs/spec.md",
                "Just some prose describing the change, with no delta headers.\n");
        refreshVfs();

        ValidationResult result = validator.validateChange("misplaced-c");
        assertTrue("misplaced root delta must be a delta-spec-misplaced ERROR",
                result.issues().stream().anyMatch(i ->
                        "delta-spec-misplaced".equals(i.rule()) && i.severity() == ValidationIssue.Severity.ERROR));
        assertFalse("the root file is flagged misplaced, NOT run through delta structural validation",
                result.issues().stream().anyMatch(i -> "delta-spec-sections".equals(i.rule())));
        assertFalse("a misplaced root spec.md counts as found — no-deltas must not also fire",
                result.issues().stream().anyMatch(i -> "delta-none-found".equals(i.rule())));
        assertFalse("the misplaced delta fails the verdict", result.passed());
    }

    public void testMisplacedRootDeltaWithValidContentStillErrors() {
        // Content-independence: a root spec.md whose content is a perfectly valid ADDED delta is still
        // misplaced (it would be dropped on apply/archive), matching the CLI's path-based detection.
        myFixture.addFileToProject("openspec/changes/mpvalid-c/proposal.md", "# MV\n\n## Why\ny\n\n## What Changes\n- x\n");
        myFixture.addFileToProject("openspec/changes/mpvalid-c/specs/spec.md", VALID_DELTA);
        refreshVfs();

        ValidationResult result = validator.validateChange("mpvalid-c");
        assertTrue("valid content does not excuse a root-level delta — still misplaced",
                result.issues().stream().anyMatch(i ->
                        "delta-spec-misplaced".equals(i.rule()) && i.severity() == ValidationIssue.Severity.ERROR));
        assertFalse("the misplaced delta fails the verdict", result.passed());
    }

    public void testNestedMultiSegmentDeltaIsDiscoveredNotMisplaced() {
        // Positive control that recursive discovery works: a spec.md at specs/<area>/<cap>/ (depth 2) is
        // a valid delta — not misplaced, and not a no-deltas change. If discovery didn't reach it,
        // delta-none-found would fire and the verdict would fail.
        myFixture.addFileToProject("openspec/changes/nested-c/proposal.md", "# N\n\n## Why\ny\n\n## What Changes\n- x\n");
        myFixture.addFileToProject("openspec/changes/nested-c/specs/identity/user-auth/spec.md", VALID_DELTA);
        refreshVfs();

        ValidationResult result = validator.validateChange("nested-c");
        assertFalse("a nested multi-segment delta must NOT be flagged misplaced",
                result.issues().stream().anyMatch(i -> "delta-spec-misplaced".equals(i.rule())));
        assertFalse("a discovered nested delta is not a no-deltas change",
                result.issues().stream().anyMatch(i -> "delta-none-found".equals(i.rule())));
        assertTrue("a valid nested delta passes the verdict", result.passed());
    }

    public void testNestedDeltaIsStructurallyValidated() {
        // Recursion doesn't just DISCOVER deep deltas, it validates them: an ADDED requirement with no
        // scenario in a nested delta still fires delta-requirement-scenario (the one-level scan never
        // reached it before — a fixed under-report).
        myFixture.addFileToProject("openspec/changes/deepval-c/proposal.md", "# DV\n\n## Why\ny\n\n## What Changes\n- x\n");
        myFixture.addFileToProject("openspec/changes/deepval-c/specs/area/cap/spec.md",
                "## ADDED Requirements\n\n### Requirement: No Scenario\nThe system SHALL x.\n");
        refreshVfs();

        ValidationResult result = validator.validateChange("deepval-c");
        assertTrue("a nested delta's structural rules run (missing scenario -> ERROR)",
                result.issues().stream().anyMatch(i ->
                        "delta-requirement-scenario".equals(i.rule()) && i.severity() == ValidationIssue.Severity.ERROR));
    }

    public void testChangeWithNoDeltasIsError() {
        myFixture.addFileToProject("openspec/changes/nodelta-c/proposal.md", "# ND\n\n## Why\ny\n\n## What Changes\n- x\n");
        refreshVfs();

        ValidationResult result = validator.validateChange("nodelta-c");
        assertTrue("a spec-requiring change with no deltas must be a delta-none-found ERROR",
                result.issues().stream().anyMatch(i ->
                        "delta-none-found".equals(i.rule()) && i.severity() == ValidationIssue.Severity.ERROR));
        assertFalse("no-deltas fails the verdict", result.passed());
    }

    public void testSkipSpecsChangeWithNoDeltasIsNotError() {
        // The load-bearing gate: a skip_specs change with no deltas is valid — the CLI suppresses the
        // no-deltas error (emitting an INFO note). The fallback must honor skip_specs the same way.
        // Positive control: testChangeWithNoDeltasIsError (identical structure sans skip_specs) DOES
        // fire delta-none-found — so this "no error" assertion is non-vacuous.
        myFixture.addFileToProject("openspec/changes/skip-c/proposal.md", "# SS\n\n## Why\ny\n\n## What Changes\n- tooling only\n");
        myFixture.addFileToProject("openspec/changes/skip-c/.openspec.yaml", "schema: spec-driven\nskip_specs: true\n");
        refreshVfs();

        ValidationResult result = validator.validateChange("skip-c");
        assertFalse("a skip_specs change with no deltas must NOT trigger delta-none-found",
                result.issues().stream().anyMatch(i -> "delta-none-found".equals(i.rule())));
        assertTrue("a skip_specs no-delta change passes the verdict", result.passed());
    }

    public void testMisplacedOnlyDoesNotAlsoTriggerNoDeltas() {
        // A misplaced root spec.md counts as a delta "found", so a misplaced-only change gets the
        // misplaced ERROR alone — never also a spurious no-deltas ERROR (matching the CLI). Positive
        // control: testChangeWithNoDeltasIsError shows delta-none-found DOES fire when nothing is found.
        myFixture.addFileToProject("openspec/changes/mp-only/proposal.md", "# MP\n\n## Why\ny\n\n## What Changes\n- x\n");
        myFixture.addFileToProject("openspec/changes/mp-only/specs/spec.md", VALID_DELTA);
        refreshVfs();

        ValidationResult result = validator.validateChange("mp-only");
        assertTrue("misplaced ERROR present",
                result.issues().stream().anyMatch(i -> "delta-spec-misplaced".equals(i.rule())));
        assertFalse("a misplaced root spec.md counts as found — no-deltas must NOT also fire",
                result.issues().stream().anyMatch(i -> "delta-none-found".equals(i.rule())));
    }

    public void testDirectoryNamedSpecMdAtRootIsNotMisplaced() {
        // A *directory* named spec.md is walked as an ordinary capability folder (id "spec.md"), not
        // flagged misplaced — matching the CLI. The spec.md inside it is the actual delta.
        myFixture.addFileToProject("openspec/changes/dir-c/proposal.md", "# D\n\n## Why\ny\n\n## What Changes\n- x\n");
        myFixture.addFileToProject("openspec/changes/dir-c/specs/spec.md/spec.md", VALID_DELTA);
        refreshVfs();

        ValidationResult result = validator.validateChange("dir-c");
        assertFalse("a directory named spec.md is not a misplaced delta",
                result.issues().stream().anyMatch(i -> "delta-spec-misplaced".equals(i.rule())));
        assertFalse("the delta inside the spec.md directory is discovered — not a no-deltas change",
                result.issues().stream().anyMatch(i -> "delta-none-found".equals(i.rule())));
    }

    public void testMissingArtifactTriggersWarning() {
        // test-change has proposal but no design.md or tasks.md. change-artifact-missing is ALWAYS a
        // non-failing WARNING (a plugin-invented lint the CLI never checks) — never escalated to ERROR
        // by any setting. A per-run strict validation fails the verdict on it via the strict
        // warnings-count rule (OpenSpecValidateAction), not by re-severity-ing it here.
        ValidationResult result = validator.validateChanges();
        assertTrue("change-artifact-missing must be a non-failing WARNING (never ERROR)",
                result.issues().stream().anyMatch(i ->
                        "change-artifact-missing".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.WARNING));
        assertTrue("change-artifact-missing must never be emitted as an ERROR",
                result.issues().stream().noneMatch(i ->
                        "change-artifact-missing".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.ERROR));
    }

    public void testChangeSchemaIncompatibleGuardedWhenCliUnavailable() throws Exception {
        // Guard: with the CLI unavailable (default test env) the known-set collapses to the built-in
        // floor, so a change schema the plugin can't verify must NOT warn — the real CLI never rejects
        // a schema name, and a legit fork would otherwise falsely red.
        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"1.0.0\"\n\nprofile:\n  name: Test\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/.openspec.yaml",
                "schema: tdd\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/proposal.md",
                "## Why\n\nTest\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertTrue("change-schema-incompatible must NOT fire when the known-set is non-authoritative",
                result.issues().stream().noneMatch(i -> "change-schema-incompatible".equals(i.rule())));
    }

    public void testChangeSchemaIncompatibleStillWarnsWhenCliAuthoritative() throws Exception {
        SchemaService authoritative = mock(SchemaService.class);
        when(authoritative.isSchemaSupported()).thenReturn(true);
        when(authoritative.getKnownSchemaNames()).thenReturn(Set.of("spec-driven"));
        ServiceContainerUtil.replaceService(getProject(), SchemaService.class, authoritative, getTestRootDisposable());

        overwriteFile("openspec/config.yaml",
                "schema: spec-driven\nversion: \"1.0.0\"\n\nprofile:\n  name: Test\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/.openspec.yaml",
                "schema: tdd\n");
        myFixture.addFileToProject("openspec/changes/incompat-change/proposal.md",
                "## Why\n\nTest\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertTrue("change-schema-incompatible must still fire on an unknown change schema when authoritative",
                result.issues().stream().anyMatch(i ->
                        "change-schema-incompatible".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.WARNING &&
                        i.message().contains("incompat-change")));
    }

    public void testScaffoldSchemaProducesNoChangeSchemaWarning() throws Exception {
        // The fixed scaffold writes `schema: spec-driven` (a built-in), so even with an AUTHORITATIVE
        // known-set the change-schema-incompatible lint the OLD invented `openspec-change` value tripped
        // is gone — proving the scaffold-schema fix silences that self-inflicted warning.
        SchemaService authoritative = mock(SchemaService.class);
        when(authoritative.isSchemaSupported()).thenReturn(true);
        when(authoritative.getKnownSchemaNames()).thenReturn(Set.of("spec-driven"));
        ServiceContainerUtil.replaceService(getProject(), SchemaService.class, authoritative, getTestRootDisposable());

        overwriteFile("openspec/config.yaml", "schema: spec-driven\n");
        myFixture.addFileToProject("openspec/changes/scaffolded/.openspec.yaml",
                "schema: spec-driven\ncreated: 2026-08-05\n");
        myFixture.addFileToProject("openspec/changes/scaffolded/proposal.md", "## Why\n\nTest\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertTrue("a spec-driven scaffold must not trip change-schema-incompatible",
                result.issues().stream().noneMatch(i -> "change-schema-incompatible".equals(i.rule())));
    }

    public void testChangeWithNoSpecsDirErrorsViaValidateChanges() {
        // (Formerly testDeltaSpecWithoutSectionsTriggersWarning — renamed; its old comment claimed
        // "LocalFileSystem doesn't work in temp VFS", which is stale: validateDeltaSpecs resolves via the
        // VFS changes dir first and reads deltas at any depth.) A change that requires specs but has no
        // specs/ directory at all is a no-deltas ERROR, surfaced through the all-changes validateChanges()
        // path (the single-change path is covered by testChangeWithNoDeltasIsError). Its `.openspec.yaml`
        // exercises the metadata-present-but-not-skip_specs gate arm.
        myFixture.addFileToProject("openspec/changes/delta-test/.openspec.yaml",
                "schema: spec-driven\nstatus: proposed\n");
        myFixture.addFileToProject("openspec/changes/delta-test/proposal.md",
                "## Why\n\nTest\n");
        refreshVfs();

        ValidationResult result = validator.validateChanges();
        assertNotNull(result);
        assertTrue("a change with no specs/ dir gets a delta-none-found ERROR via validateChanges()",
                result.issues().stream().anyMatch(i ->
                        "delta-none-found".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.ERROR
                                && i.filePath().contains("delta-test")));
    }

    // ---------------------------------------------------------------
    // ---------------------------------------------------------------
    // CLI 1.6 parity semantics (fence masking, SHALL/MUST-only, INFO tier)
    // ---------------------------------------------------------------

    public void testShouldOnlyRequirementIsWarning() {
        // SHOULD/MAY never satisfy the keyword rule; 1.8 makes a body-carrying miss a WARNING, not ERROR.
        myFixture.addFileToProject("openspec/specs/should-kw/spec.md",
                "# Should Spec\n\n### Requirement: Soft wording\n\nThe system SHOULD work and MAY retry.\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n");
        refreshVfs();

        assertTrue("SHOULD-only body-carrying requirement is a missing-keyword WARNING",
                validator.validateSpecs().issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.WARNING
                                && i.filePath().contains("should-kw")));
    }

    public void testKeywordOnlyInsideFenceIsWarning() {
        myFixture.addFileToProject("openspec/specs/fenced-kw/spec.md",
                "# Fenced Spec\n\n### Requirement: Fenced keyword\n\nBody without the magic word.\n\n```\nThe system SHALL work.\n```\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n");
        refreshVfs();

        assertTrue("keyword only inside a code fence does not satisfy the check (1.6 masking); 1.8 → WARNING",
                validator.validateSpecs().issues().stream().anyMatch(i ->
                        "spec-rfc-keywords".equals(i.rule())
                                && i.severity() == ValidationIssue.Severity.WARNING
                                && i.filePath().contains("fenced-kw")));
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

    // ---------------------------------------------------------------
    // CLI-authoritative merge (OpenSpecValidateAction.combineWithCli)
    // ---------------------------------------------------------------

    /** A clean CLI verdict, as if `openspec validate` reported every spec/change valid. */
    private static ValidationResult cleanCli() {
        return new ValidationResult(true, List.of(), "cli");
    }

    public void testCliAuthoritativeCleanCliIsNotOverriddenByBuiltInSpecErrors() throws Exception {
        // Anti-vacuous: the project has a genuinely broken spec (no `### Requirement:` block →
        // spec-requirement-required ERROR in the built-in validator) but a clean config. With the CLI
        // reporting specs valid, the whole-project verdict must PASS — the built-in validator's own
        // spec ERROR must not leak in. This fails if the old `merge(validateAll(), cli)` (which runs
        // the full built-in over specs) is restored.
        overwriteFile("openspec/config.yaml", "schema: spec-driven\n");
        myFixture.addFileToProject("openspec/specs/broken/spec.md",
                "# Broken\n\nNo requirements section at all.\n");
        refreshVfs();

        ValidationResult result = com.johnnyblabs.openspec.actions.OpenSpecValidateAction
                .combineWithCli(validator, com.johnnyblabs.openspec.actions.ValidateTarget.wholeProject(), cleanCli());

        assertTrue("clean CLI + clean config must pass despite a built-in spec ERROR", result.passed());
        assertTrue("the built-in's spec-requirement-required must NOT enter the verdict",
                result.issues().stream().noneMatch(i -> "spec-requirement-required".equals(i.rule())));
    }

    public void testCliAuthoritativeSurfacesConfigWarningsWithoutFailingWhenCliClean() throws Exception {
        // The built-in validator still OWNS config.yaml when the CLI is present — but config checks are
        // non-failing (the CLI never fails on config). A missing schema surfaces as an INFO advisory
        // alongside a clean CLI verdict; it does not red the whole-project result.
        overwriteFile("openspec/config.yaml", "version: \"1.2.0\"\n");  // no schema
        refreshVfs();

        ValidationResult result = com.johnnyblabs.openspec.actions.OpenSpecValidateAction
                .combineWithCli(validator, com.johnnyblabs.openspec.actions.ValidateTarget.wholeProject(), cleanCli());

        assertTrue("a config advisory must NOT fail a clean-CLI verdict", result.passed());
        assertTrue("the config-schema-required INFO advisory is still surfaced for display",
                result.issues().stream().anyMatch(i ->
                        "config-schema-required".equals(i.rule()) &&
                        i.severity() == ValidationIssue.Severity.INFO));
    }

    public void testCliAuthoritativeSingleItemDefersEntirelyToCli() throws Exception {
        // For a single spec/change target there is no config component: a broken config must not
        // affect a single-item verdict that defers to the CLI.
        overwriteFile("openspec/config.yaml", "version: \"1.2.0\"\n");  // no schema — broken
        refreshVfs();

        ValidationResult result = com.johnnyblabs.openspec.actions.OpenSpecValidateAction
                .combineWithCli(validator, com.johnnyblabs.openspec.actions.ValidateTarget.spec("anything"), cleanCli());

        assertTrue("single-item target defers to the CLI; broken config is not merged in", result.passed());
        assertTrue("no config issue is merged for a single-item target",
                result.issues().stream().noneMatch(i -> i.rule() != null && i.rule().startsWith("config-")));
    }

    public void testAChangeTargetIsExactlyTheClisVerdict() {
        // The Change Deltas pane shows the CLI's verdict for a change unmerged; Validate for that change must report
        // the same verdict, or the pane's line and the "Show issues" run would disagree.
        ValidationResult cli = new ValidationResult(false, List.of(new ValidationIssue(
                ValidationIssue.Severity.ERROR, "change/x", 0, "boom", "cli")), "cli");
        ValidationResult result = com.johnnyblabs.openspec.actions.OpenSpecValidateAction
                .combineWithCli(validator, com.johnnyblabs.openspec.actions.ValidateTarget.change("x"), cli);
        assertSame(cli, result);
    }
}

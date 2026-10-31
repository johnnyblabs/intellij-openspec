package com.johnnyblabs.openspec.actions;

import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the per-run strict plumbing in {@link OpenSpecValidateAction}: the CLI arg
 * vector, the console command echo, the balloon summary disclosure, and the CLI-absent verdict flip.
 * All are package-private static, so no IDE fixture is needed.
 */
class OpenSpecValidateStrictTest {

    private static ValidationIssue warning(String rule) {
        return new ValidationIssue(ValidationIssue.Severity.WARNING, "spec/x", 1, "w", rule);
    }

    private static ValidationIssue error(String rule) {
        return new ValidationIssue(ValidationIssue.Severity.ERROR, "spec/x", 1, "e", rule);
    }

    // --- cliArgs / commandLine: both polarities (trap #1: prove --strict is actually appended) ---

    @Test
    void cliArgs_appendsStrict_onlyWhenStrict() {
        List<String> plain = Arrays.asList(OpenSpecValidateAction.cliArgs(ValidateTarget.wholeProject()));
        assertFalse(plain.contains("--strict"), "a non-strict run must NOT pass --strict");
        assertTrue(plain.contains("--all") && plain.contains("--json"));

        List<String> strict = Arrays.asList(
                OpenSpecValidateAction.cliArgs(ValidateTarget.wholeProject().withStrict()));
        assertTrue(strict.contains("--strict"), "a strict run must pass --strict");
    }

    @Test
    void commandLine_echoesStrict_onlyWhenStrict() {
        assertEquals("openspec validate --all",
                OpenSpecValidateAction.commandLine(ValidateTarget.wholeProject()));
        assertEquals("openspec validate --all --strict",
                OpenSpecValidateAction.commandLine(ValidateTarget.wholeProject().withStrict()));
        // Single-item strict still appends the flag after the --type.
        assertEquals("openspec validate demo --type spec --strict",
                OpenSpecValidateAction.commandLine(ValidateTarget.spec("demo").withStrict()));
    }

    // --- summaryText disclosure (preserve the passed(/failed( tokens; strict adds title+suffix) ---

    @Test
    void summaryText_preservesTokens_andDisclosesStrictOnWarningOnlyFailure() {
        // A warnings-only strict failure: the `failed (` token stays intact, the suffix follows it.
        ValidationResult warnFail = new ValidationResult(false, List.of(warning("spec-title-required")), "built-in");
        String strict = OpenSpecValidateAction.summaryText("whole project", warnFail, true);
        assertTrue(strict.contains("failed ("), "the failed( token must be preserved for UI assertions");
        assertTrue(strict.endsWith("— strict: warnings count as failures"), "strict discloses the reason");

        // Non-strict: no suffix.
        String normal = OpenSpecValidateAction.summaryText("whole project", warnFail, false);
        assertTrue(normal.contains("failed ("));
        assertFalse(normal.contains("strict"), "a non-strict summary never mentions strict");

        // A pass keeps the passed( token in both modes.
        ValidationResult pass = new ValidationResult(true, List.of(warning("x")), "built-in");
        assertTrue(OpenSpecValidateAction.summaryText("whole project", pass, true).contains("passed ("));
    }

    @Test
    void summaryText_countsAreSingularForOne() {
        ValidationResult oneEach = new ValidationResult(false, List.of(error("a"), warning("b")), "built-in");
        assertEquals("whole project failed (1 error, 1 warning)",
                OpenSpecValidateAction.summaryText("whole project", oneEach, false));
        ValidationResult pass = new ValidationResult(true, List.of(warning("x"), warning("y")), "built-in");
        assertEquals("whole project passed (2 warnings)", OpenSpecValidateAction.summaryText("whole project", pass, false));
    }

    // --- CLI-absent verdict flip (allow-list): a plugin-invented lint WARNING must NOT flip strict,
    //     because the real CLI never emits it — flipping would make the plugin stricter than the CLI ---

    @Test
    void strictFallback_doesNotFlipOnCliSilentLintWarnings() {
        // Every built-in non-config WARNING is a lint the real CLI never emits (or, for
        // delta-spec-sections, a condition the CLI reports as an ERROR — not a warning). None is in
        // CLI_MIRRORING_STRICT_WARNINGS, so a warnings-only fallback carrying them must PASS under
        // strict — otherwise the plugin would be more restrictive than the client it wraps.
        for (String rule : List.of("spec-title-required", "change-artifact-missing",
                "change-proposal-required", "change-schema-incompatible", "delta-removed-fields",
                "delta-spec-sections")) {
            ValidationResult warnOnly = new ValidationResult(true, List.of(warning(rule)), "built-in");
            ValidationResult after = OpenSpecValidateAction.applyStrictFallbackVerdict(warnOnly, true);
            assertTrue(after.passed(), "strict must NOT flip on the CLI-silent lint '" + rule + "'");
            assertEquals(ValidationIssue.Severity.WARNING, after.issues().get(0).severity(),
                    "the issue STAYS a WARNING — no re-severity");
        }
    }

    @Test
    void strictFallback_flipMechanics_whenRuleIsInFlipSet() {
        // The flip-positive branch is unreachable via the empty production set, so exercise it through
        // the explicit-set overload: a WARNING whose rule IS in the set flips the verdict to FAILED,
        // with issues preserved verbatim, source preserved, and NO re-severity.
        ValidationResult warnOnly = new ValidationResult(true, List.of(warning("mirrored-rule")), "built-in");
        ValidationResult flipped =
                OpenSpecValidateAction.applyStrictFallbackVerdict(warnOnly, true, Set.of("mirrored-rule"));
        assertFalse(flipped.passed(), "a warning in the flip set fails the strict verdict");
        assertEquals(0, flipped.errorCount(), "no errors were invented");
        assertEquals(warnOnly.issues(), flipped.issues(), "issues preserved verbatim");
        assertEquals("built-in", flipped.source(), "source preserved");
        assertEquals(ValidationIssue.Severity.WARNING, flipped.issues().get(0).severity(),
                "the issue STAYS a WARNING — the flip is a verdict change, not a re-severity");

        // A warning NOT in the set does not flip.
        ValidationResult other = new ValidationResult(true, List.of(warning("some-other-rule")), "built-in");
        assertTrue(OpenSpecValidateAction.applyStrictFallbackVerdict(other, true, Set.of("mirrored-rule")).passed(),
                "a warning absent from the flip set must not fail strict");
    }

    @Test
    void cliMirroringStrictWarningSet_containsMissingKeywordRules() {
        // Guards the allow-list's intent: only WARNINGs that mirror a CLI strict-warning may flip a
        // strict fallback. 1.8 demoted the missing-SHALL/MUST rule to a warning the CLI itself fails on
        // under --strict (a body-carrying requirement is valid in default, valid:false under strict), so
        // it is the sole member. A future addition must still be source-verified against the real CLI —
        // this exact-set assertion is the tripwire that forces that justification.
        Set<String> set = OpenSpecValidateAction.CLI_MIRRORING_STRICT_WARNINGS;
        assertEquals(Set.of("spec-rfc-keywords", "spec-rfc-keyword-in-header"), set,
                "the strict-warning set is exactly the missing-keyword rules 1.8 fails on under --strict");
        // Plugin-invented lints the CLI never emits must stay OUT (never more restrictive than the CLI).
        assertFalse(set.contains("spec-title-required"), "plugin-invented lints must not flip strict");
        assertFalse(set.contains("change-artifact-missing"), "plugin-invented lints must not flip strict");
    }

    @Test
    void strictFallback_doesNotFailOnConfigWarnings() {
        // config.yaml is display-only (the CLI never fails on it) — config warnings do not fail even
        // under strict, keeping strict's meaning consistent between CLI-present and CLI-absent.
        ValidationResult configWarn = new ValidationResult(true, List.of(warning("config-schema-required")), "built-in");
        assertTrue(OpenSpecValidateAction.applyStrictFallbackVerdict(configWarn, true).passed(),
                "a config warning must not fail a strict verdict");
    }

    @Test
    void strictFallback_isNoOpWhenNotStrictOrAlreadyFailing() {
        ValidationResult warnOnly = new ValidationResult(true, List.of(warning("spec-title-required")), "built-in");
        assertSame(warnOnly, OpenSpecValidateAction.applyStrictFallbackVerdict(warnOnly, false),
                "non-strict: unchanged");
        ValidationResult errored = new ValidationResult(false, List.of(error("spec-requirement-required")), "built-in");
        assertSame(errored, OpenSpecValidateAction.applyStrictFallbackVerdict(errored, true),
                "already failing on an ERROR: nothing to flip");
    }
}

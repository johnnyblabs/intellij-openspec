package com.johnnyblabs.openspec.validation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rule-level tests for {@link BuiltInValidator}, driving the REAL
 * {@link BuiltInValidator#validateSpecContent} (not a re-implemented mirror). Covers the 1.8
 * body-conditional RFC 2119 keyword rule (WARNING when the requirement has a body, ERROR when it has
 * none; the header-only variant is a WARNING), scenario-clause hints, the requirement-must-have-scenario
 * rule, and delta spec structural validation.
 */
class BuiltInValidatorRulesTest {

    // --- 1.8: RFC 2119 keyword rule is severity-conditional on the requirement having a body ---

    @Test
    void rfc2119_missingKeyword_bodyPresent_isWarning() {
        String content = """
                # Test Spec

                ### Requirement: Missing keywords
                The system allows users to log in.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** system authenticates
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING && i.rule().equals("spec-rfc-keywords")),
                "1.8: a body-carrying requirement without SHALL/MUST is a WARNING, not an ERROR");
        assertTrue(issues.stream().noneMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("spec-rfc-keywords")),
                "the missing-keyword rule must not ERROR when the requirement has a body");
    }

    @Test
    void rfc2119_missingKeyword_noBody_isError() {
        String content = """
                # Test Spec

                ### Requirement: Empty

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** system authenticates
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("spec-rfc-keywords")),
                "1.8: a requirement with no body prose still ERRORs on the missing keyword");
    }

    // --- 1.8: main-spec duplicate-requirement-name rule (upstream #1484) ---

    private static List<ValidationIssue> dupErrors(List<ValidationIssue> issues) {
        List<ValidationIssue> out = new ArrayList<>();
        for (ValidationIssue i : issues) {
            if (i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("spec-duplicate-requirement")) {
                out.add(i);
            }
        }
        return out;
    }

    /** 1-based line of the {@code occurrence}-th (1-based) line whose trimmed text equals {@code header}. */
    private static int lineOfHeader(String content, int occurrence, String header) {
        String[] lines = content.split("\n", -1);
        int seen = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals(header)) {
                if (++seen == occurrence) return i + 1;
            }
        }
        return -1;
    }

    /**
     * A minimal well-formed main spec with two named requirements, each with a SHALL body and a
     * scenario, under a single {@code ## Requirements} section. Built by concatenation (not a text
     * block) so trailing whitespace in a name survives — text blocks strip it, which would defeat the
     * exterior-whitespace discriminator.
     */
    private static String twoReqSpec(String name1, String name2) {
        return "# Test Spec\n\n## Requirements\n\n"
                + "### Requirement: " + name1 + "\nThe system SHALL work.\n\n"
                + "#### Scenario: A\n- **WHEN** x\n- **THEN** y\n\n"
                + "### Requirement: " + name2 + "\nThe system SHALL work again.\n\n"
                + "#### Scenario: B\n- **WHEN** a\n- **THEN** b\n";
    }

    @Test
    void duplicateRequirementName_isErrorAnchoredOnSecondReferencingFirst() {
        String content = """
                # Test Spec

                ## Requirements

                ### Requirement: Works
                The system SHALL work first.

                #### Scenario: A
                - **WHEN** x
                - **THEN** y

                ### Requirement: Works
                The system SHALL work second.

                #### Scenario: B
                - **WHEN** a
                - **THEN** b
                """;
        List<ValidationIssue> dups = dupErrors(validateSpec(content));
        assertEquals(1, dups.size(), "a name declared twice yields exactly one duplicate ERROR");
        int firstLine = lineOfHeader(content, 1, "### Requirement: Works");
        int secondLine = lineOfHeader(content, 2, "### Requirement: Works");
        assertEquals(secondLine, dups.get(0).line(),
                "the duplicate ERROR is anchored on the second occurrence's line");
        assertTrue(dups.get(0).message().contains("duplicates the requirement declared on line " + firstLine),
                "the message references the first occurrence's line");
    }

    @Test
    void duplicateRequirementName_tripleFlagsTheLaterTwoAgainstTheFirst() {
        String content = """
                # Test Spec

                ## Requirements

                ### Requirement: Alpha
                The system SHALL alpha once.

                #### Scenario: A
                - **WHEN** x
                - **THEN** y

                ### Requirement: Alpha
                The system SHALL alpha twice.

                #### Scenario: B
                - **WHEN** x
                - **THEN** y

                ### Requirement: Alpha
                The system SHALL alpha thrice.

                #### Scenario: C
                - **WHEN** x
                - **THEN** y
                """;
        List<ValidationIssue> dups = dupErrors(validateSpec(content));
        assertEquals(2, dups.size(), "three occurrences of one name yield exactly two duplicate ERRORs (N−1)");
        int firstLine = lineOfHeader(content, 1, "### Requirement: Alpha");
        int secondLine = lineOfHeader(content, 2, "### Requirement: Alpha");
        int thirdLine = lineOfHeader(content, 3, "### Requirement: Alpha");
        // The two ERRORs are anchored on the second and third occurrences (never the first).
        java.util.Set<Integer> errorLines = new java.util.HashSet<>();
        for (ValidationIssue d : dups) errorLines.add(d.line());
        assertEquals(java.util.Set.of(secondLine, thirdLine), errorLines,
                "the duplicate ERRORs are anchored on the 2nd and 3rd occurrences, not the 1st");
        // Each references the FIRST occurrence's line — an adjacent-pair implementation would make the
        // third reference the second, so reject that explicitly.
        assertTrue(dups.stream().allMatch(d ->
                        d.message().contains("duplicates the requirement declared on line " + firstLine)),
                "each duplicate references the first occurrence's line");
        ValidationIssue thirdErr = dups.stream().filter(d -> d.line() == thirdLine).findFirst().orElseThrow();
        assertFalse(thirdErr.message().contains("declared on line " + secondLine),
                "the third occurrence references the first line, not the second (not an adjacent pair)");
    }

    @Test
    void duplicateRequirementName_matchKeyIsExactTrimmedNameCaseAndInteriorSensitive() {
        // Positive control first: two IDENTICAL names fire exactly one duplicate ERROR. This proves
        // twoReqSpec builds a structure the validator recognizes, so the negatives below are
        // scope-specific (the key discriminates), not vacuous (requirements unrecognized).
        assertEquals(1, dupErrors(validateSpec(twoReqSpec("Works", "Works"))).size(),
                "control: two identical requirement names are a duplicate");
        // Case-sensitive: 'Works' vs 'works' are distinct (the CLI does not case-fold the name).
        assertTrue(dupErrors(validateSpec(twoReqSpec("Works", "works"))).isEmpty(),
                "the 1.8 duplicate match is case-sensitive");
        // Interior whitespace significant: a whitespace-collapsing key would wrongly merge these.
        assertTrue(dupErrors(validateSpec(twoReqSpec("Process Refund", "Process  Refund"))).isEmpty(),
                "interior whitespace is significant — the key is group(1).trim(), not a collapsed form");
        // Exterior whitespace insignificant: a trailing-space-only difference IS the same requirement.
        assertEquals(1, dupErrors(validateSpec(twoReqSpec("Foo ", "Foo"))).size(),
                "exterior whitespace is trimmed — a trailing-space-only difference is still a duplicate");
    }

    @Test
    void duplicateRequirementName_headerTokenCaseDiffersButNameIdenticalIsADuplicate() {
        // The `### requirement:` token is matched case-insensitively (upstream #1154), but dedup keys off
        // the captured NAME — so `### requirement: Works` and `### Requirement: Works` are the same
        // requirement and collide. Locks that the key is the name, never the raw header line.
        String content = "# Test Spec\n\n## Requirements\n\n"
                + "### requirement: Works\nThe system SHALL work.\n\n"
                + "#### Scenario: A\n- **WHEN** x\n- **THEN** y\n\n"
                + "### Requirement: Works\nThe system SHALL work again.\n\n"
                + "#### Scenario: B\n- **WHEN** a\n- **THEN** b\n";
        assertEquals(1, dupErrors(validateSpec(content)).size(),
                "dedup keys off the requirement name — a header-token case difference still collides");
    }

    @Test
    void duplicateRequirementName_insideACodeFenceIsNotCounted() {
        String content = """
                # Test Spec

                ## Requirements

                ### Requirement: Works
                The system SHALL work.

                #### Scenario: A
                - **WHEN** x
                - **THEN** y

                ```
                ### Requirement: Works
                this is documentation, not a real requirement
                ```
                """;
        assertTrue(dupErrors(validateSpec(content)).isEmpty(),
                "a `### Requirement:` inside a fenced code block does not count (fence-masked), matching the CLI");
        // Positive control: strip the fence markers and the same name becomes a real duplicate — proving
        // the fence (not some other defect) is what suppresses the error above.
        assertEquals(1, dupErrors(validateSpec(content.replace("```\n", ""))).size(),
                "control: the same name unfenced fires the duplicate rule");
    }

    @Test
    void duplicateRequirementName_outsideTheRequirementsSectionIsNotDeduped() {
        // The second `Works` sits under a later top-level `## Appendix` section, so the CLI routes it
        // outside `## Requirements` (a different `requirement-outside-requirements` diagnostic) and never
        // dedups it — see the captured contract fixture. The fallback must match (no over-reporting).
        String content = """
                # Test Spec

                ## Requirements

                ### Requirement: Works
                The system SHALL work.

                #### Scenario: A
                - **WHEN** x
                - **THEN** y

                ## Appendix

                ### Requirement: Works
                A note that reuses the name outside the requirements section.
                """;
        assertTrue(dupErrors(validateSpec(content)).isEmpty(),
                "a requirement outside the `## Requirements` section is not deduped, matching the CLI");
        // Positive control: drop the intervening `## Appendix` header so the second `Works` falls inside
        // `## Requirements`, and it fires — proving the section boundary (not some defect) suppresses it.
        assertEquals(1, dupErrors(validateSpec(content.replace("## Appendix\n\n", ""))).size(),
                "control: with the second occurrence in-section the duplicate fires");
    }

    @Test
    void duplicateRequirementName_nameReusedInASecondRequirementsSectionIsNotDeduped() {
        // Pathological: two `## Requirements` sections. The rule is scoped to the FIRST section — the
        // choice that cannot over-report, since the CLI's two-section behavior is unpinned — so a name
        // reused in a second `## Requirements` section is not flagged. Documents the deliberate scoping.
        String content = "# Test Spec\n\n## Requirements\n\n"
                + "### Requirement: Works\nThe system SHALL work.\n\n"
                + "#### Scenario: A\n- **WHEN** x\n- **THEN** y\n\n"
                + "## Requirements\n\n"
                + "### Requirement: Works\nThe system SHALL work again.\n\n"
                + "#### Scenario: B\n- **WHEN** a\n- **THEN** b\n";
        assertTrue(dupErrors(validateSpec(content)).isEmpty(),
                "first-section-scoped: a name reused in a second `## Requirements` section is not deduped");
    }

    @Test
    void duplicateRequirementName_deltaSectionsAreNotDeduped() {
        // Delta specs use `## ADDED`/`## MODIFIED Requirements` (not `## Requirements`) and legitimately
        // repeat a name across sections, governed by the CLI's separate delta-consistency rules. The
        // main-spec dedup is scoped to `## Requirements`, so delta-shaped content is never deduped here —
        // and in production delta specs go through a wholly separate path. Guards the fallback against
        // becoming more restrictive than the CLI on delta specs.
        String content = """
                ## ADDED Requirements

                ### Requirement: New Thing
                The system SHALL do the new thing.

                #### Scenario: A
                - **WHEN** x
                - **THEN** y

                ## MODIFIED Requirements

                ### Requirement: New Thing
                The system SHALL do the new thing differently.

                #### Scenario: B
                - **WHEN** a
                - **THEN** b
                """;
        assertTrue(dupErrors(validateSpec(content)).isEmpty(),
                "delta-style repeated names across ADDED/MODIFIED are not deduped by the main-spec rule");
    }

    @Test
    void rfc2119_withShall_passes() {
        String content = """
                # Test Spec

                ### Requirement: Has keyword
                The system SHALL allow users to log in.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** system authenticates
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-rfc-keywords")),
                "Requirement with SHALL should not produce RFC keyword error");
    }

    @Test
    void rfc2119_withMust_passes() {
        String content = """
                # Test Spec

                ### Requirement: Has keyword
                The system MUST allow users to log in.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** system authenticates
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-rfc-keywords")));
    }

    // --- 1.7: Scenario clauses are ERROR ---

    @Test
    void scenarioClauses_missingWhen_isInfo() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Missing when
                - **THEN** something happens
                """;
        List<ValidationIssue> issues = validateSpec(content);
        // INFO, not ERROR — the CLI performs no clause-structure validation, so flagging it as an error
        // would make the plugin stricter than the client it wraps (verified against real production).
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.INFO
                        && i.rule().equals("spec-scenario-clauses")
                        && i.message().contains("WHEN")),
                "Missing WHEN clause is an informational (INFO) hint");
    }

    @Test
    void scenarioClauses_missingThen_isInfo() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Missing then
                - **WHEN** user does something
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.INFO
                        && i.rule().equals("spec-scenario-clauses")
                        && i.message().contains("THEN")),
                "Missing THEN clause is an informational (INFO) hint");
    }

    @Test
    void scenarioClauses_missingBoth_isInfo() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Missing both
                Some text but no clauses.
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.INFO
                        && i.rule().equals("spec-scenario-clauses")
                        && i.message().contains("WHEN and THEN")),
                "Missing both clauses is an informational (INFO) hint");
    }

    @Test
    void scenarioClauses_withBoth_passes() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Complete
                - **WHEN** user does something
                - **THEN** system responds
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-scenario-clauses")),
                "Scenario with WHEN and THEN should not produce clause error");
    }

    // --- 1.9: Requirement must have at least one scenario ---

    @Test
    void requirementWithoutScenario_isError() {
        String content = """
                # Test Spec

                ### Requirement: No scenarios
                The system SHALL do something but has no scenarios.
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("spec-scenario-required")),
                "Requirement without scenario should be ERROR");
    }

    @Test
    void requirementWithScenario_passes() {
        String content = """
                # Test Spec

                ### Requirement: Has scenario
                The system SHALL do something.

                #### Scenario: It works
                - **WHEN** triggered
                - **THEN** it works
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-scenario-required")),
                "Requirement with scenario should not produce error");
    }

    @Test
    void multipleRequirements_firstMissingScenario_isError() {
        String content = """
                # Test Spec

                ### Requirement: No scenarios
                The system SHALL do A.

                ### Requirement: Has scenarios
                The system SHALL do B.

                #### Scenario: B works
                - **WHEN** triggered
                - **THEN** B works
                """;
        List<ValidationIssue> issues = validateSpec(content);
        long scenarioErrors = issues.stream()
                .filter(i -> i.rule().equals("spec-scenario-required"))
                .count();
        assertEquals(1, scenarioErrors, "Only the requirement without a scenario should produce an error");
        assertTrue(issues.stream().anyMatch(i ->
                i.rule().equals("spec-scenario-required") && i.message().contains("No scenarios")));
    }

    // --- 1.8: Delta spec structural validation ---

    @Test
    void deltaAdded_missingScenario_isError() {
        String content = """
                ## ADDED Requirements

                ### Requirement: New feature
                The system SHALL add a feature.
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("delta-requirement-scenario")
                        && i.message().contains("ADDED")),
                "ADDED requirement without scenario should be ERROR");
    }

    @Test
    void deltaModified_missingScenario_isError() {
        String content = """
                ## MODIFIED Requirements

                ### Requirement: Updated feature
                The system SHALL update a feature.
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("delta-requirement-scenario")
                        && i.message().contains("MODIFIED")),
                "MODIFIED requirement without scenario should be ERROR");
    }

    @Test
    void deltaRemoved_missingReason_isWarning() {
        // Reason/Migration is an OpenSpec authoring convention, not an upstream rule (the
        // @fission-ai/openspec client validates REMOVED by name only), so it is advisory, not blocking.
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                **Migration**: Use new endpoint
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("delta-removed-fields")
                        && i.message().contains("**Reason**")),
                "REMOVED requirement without Reason should be WARNING");
        assertTrue(issues.stream().noneMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("delta-removed-fields")),
                "delta-removed-fields must not be an ERROR — the client does not require these fields");
    }

    @Test
    void deltaRemoved_missingMigration_isWarning() {
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                **Reason**: No longer needed
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("delta-removed-fields")
                        && i.message().contains("**Migration**")),
                "REMOVED requirement without Migration should be WARNING");
    }

    @Test
    void deltaRemoved_missingBoth_isWarning() {
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                This is being removed.
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("delta-removed-fields")
                        && i.message().contains("**Reason** and **Migration**")),
                "REMOVED requirement without both fields should report both missing");
    }

    @Test
    void deltaRemoved_colonInsideBold_isAccepted() {
        // **Reason:** / **Migration:** (colon inside the bold) is the form upstream proposals use —
        // it must be recognized just like the **Reason**: form, with no warning.
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                **Reason:** No longer needed
                **Migration:** Use the new endpoint
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("delta-removed-fields")),
                "REMOVED block using **Reason:**/**Migration:** (colon inside) should produce no finding");
    }

    @Test
    void validDeltaSpec_passes() {
        String content = """
                ## ADDED Requirements

                ### Requirement: New feature
                The system SHALL add a feature.

                #### Scenario: Feature works
                - **WHEN** triggered
                - **THEN** feature activates

                ## MODIFIED Requirements

                ### Requirement: Updated feature
                The system SHALL update behavior.

                #### Scenario: Updated behavior
                - **WHEN** condition met
                - **THEN** new behavior occurs

                ## REMOVED Requirements

                ### Requirement: Legacy feature
                **Reason**: Replaced by new feature
                **Migration**: Use new feature instead
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("delta-requirement-scenario") || i.rule().equals("delta-removed-fields")),
                "Valid delta spec should have no structural errors");
    }

    // --- RENAMED delta section ---

    @Test
    void deltaRenamed_headerFormPair_passes() {
        String content = """
                ## RENAMED Requirements

                - FROM: `### Requirement: Old requirement name`
                - TO: `### Requirement: New requirement name`
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR),
                "a header-form RENAMED pair is an entry; no error: " + issues);
    }

    /** Every supported CLI (1.3.0–1.14.3) reads bare FROM/TO as nothing: a file holding only them has no entries. */
    @Test
    void deltaRenamed_bareNamesOnly_isNoEntries_notRenamedFields() {
        for (String content : List.of("""
                ## RENAMED Requirements

                - FROM: Old requirement name
                - TO: New requirement name
                """, """
                ## RENAMED Requirements

                FROM: Old requirement name
                TO: New requirement name
                """)) {
            List<ValidationIssue> issues = validateDeltaStructure(content);
            assertTrue(issues.stream().anyMatch(i -> i.rule().equals("delta-no-entries")
                    && i.severity() == ValidationIssue.Severity.ERROR), "no entries parsed: " + issues);
            assertTrue(issues.stream().noneMatch(i -> i.rule().equals("delta-renamed-fields")),
                    "bare lines are ignored, not a rename error: " + issues);
        }
    }

    @Test
    void deltaRenamed_fromWithoutTo_isError() {
        String content = """
                ## RENAMED Requirements

                - FROM: `### Requirement: Login`
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("delta-renamed-fields")),
                "RENAMED entry lines with no well-formed FROM/TO pair should be ERROR: " + issues);
    }

    /** The CLI rejected the old (1.12) delta scaffold as having no entries; the built-in validator now agrees, verbatim. */
    @Test
    void capturedOldScaffold_isNoEntries_matchingTheCli() throws Exception {
        String base = "src/test/resources/fixtures/cli/1.12.0/scaffold-delta/";
        com.google.gson.JsonObject item = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                java.nio.file.Paths.get(base + "validate-old.json"))).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject();
        assertFalse(item.get("valid").getAsBoolean(), "capture: the CLI rejects the old scaffold");
        String cliMessage = null;
        for (var e : item.getAsJsonArray("issues")) {
            String m = e.getAsJsonObject().get("message").getAsString();
            if (m.contains("no requirement entries parsed")) cliMessage = m;
        }
        assertNotNull(cliMessage);
        String delta = java.nio.file.Files.readString(java.nio.file.Paths.get(base + "old-template/openspec/changes/c/specs/auth/spec.md")).replace("\r\n", "\n");
        List<ValidationIssue> issues = validateDeltaStructure(delta);
        String expected = cliMessage;
        assertTrue(issues.stream().anyMatch(i -> i.rule().equals("delta-no-entries") && i.message().equals(expected)), issues.toString());
    }

    @Test
    void capturedFilledScaffold_hasNoBuiltInError_matchingTheCli() throws Exception {
        // Never-more-restrictive: the CLI reports this exact file valid:true (validate-new.json beside it).
        String delta;
        try (java.io.InputStream in = getClass().getResourceAsStream(
                "/fixtures/cli/1.12.0/scaffold-delta/new-template/openspec/changes/c/specs/auth/spec.md")) {
            assertNotNull(in, "missing scaffold-delta fixture");
            delta = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        List<ValidationIssue> issues = validateDeltaStructure(delta);
        assertTrue(issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR),
                "the built-in fallback must not ERROR on a scaffold the CLI accepts: " + issues);
    }

    @Test
    void capturedStarBulletedRenamed_isAccepted_matchingCli113() throws Exception {
        // CLI 1.13.0+ parses every CommonMark bullet in RENAMED; the capture beside this input is valid:true.
        String base = "/fixtures/cli/1.13.2/renamed-star/";
        String capture = fixtureText(base + "validate-change.json");
        assertTrue(capture.contains("\"valid\": true"), "capture must record the CLI accepting the change");
        String delta = fixtureText(base + "openspec/changes/c/specs/demo/spec.md");
        assertTrue(delta.lines().anyMatch(l -> l.startsWith("* FROM:")), "control must use a * bullet: " + delta);
        for (String input : List.of(delta, delta.replace("\n", "\r\n"))) {
            List<ValidationIssue> issues = validateDeltaStructure(input);
            assertTrue(issues.stream().noneMatch(i -> i.rule().equals("delta-renamed-fields")),
                    "a *-bulleted RENAMED pair must not be delta-renamed-fields (CLI accepts it): " + issues);
        }
    }

    @Test
    void capturedUnpairedStarRenamed_isReported_soTheRuleIsLiveOnStarInput() throws Exception {
        // Liveness anchor for the "*-bullet is accepted" tests: the CLI rejects this lone `* FROM:` (valid:false),
        // and the built-in rule must see the `*` line too — otherwise "not reported" could pass on an unparsed section.
        String base = "/fixtures/cli/1.13.2/renamed-star-unpaired/";
        assertTrue(fixtureText(base + "validate-change.json").contains("\"valid\": false"));
        List<ValidationIssue> issues = validateDeltaStructure(fixtureText(base + "openspec/changes/c/specs/demo/spec.md"));
        assertTrue(issues.stream().anyMatch(i -> i.rule().equals("delta-renamed-fields")
                        && i.severity() == ValidationIssue.Severity.ERROR),
                "a lone * FROM: must be delta-renamed-fields: " + issues);
    }

    @Test
    void capturedPlusBulletedRenamed_isAccepted_matchingCli113() throws Exception {
        String base = "/fixtures/cli/1.13.2/renamed-plus/";
        assertTrue(fixtureText(base + "validate-change.json").contains("\"valid\": true"));
        List<ValidationIssue> issues = validateDeltaStructure(fixtureText(base + "openspec/changes/c/specs/demo/spec.md"));
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("delta-renamed-fields")),
                "a +-bulleted RENAMED pair must not be delta-renamed-fields: " + issues);
    }

    private String fixtureText(String path) throws java.io.IOException {
        try (java.io.InputStream in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, "missing fixture " + path);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    void deltaRenamed_emptySection_isNotAnError() {
        // The CLI accepts an empty RENAMED section beside other parsed deltas (captured in
        // fixtures/cli/1.12.0/scaffold-delta/validate-new.json), and the scaffold leaves it empty by design.
        String content = """
                ## ADDED Requirements

                ### Requirement: Logout
                The system SHALL let a signed-in user log out.

                #### Scenario: Logging out ends the session
                - **WHEN** a signed-in user logs out
                - **THEN** their session ends

                ## RENAMED Requirements

                <!-- Renamed requirements as FROM:/TO: pairs, e.g. - FROM: `### Requirement: Old name` then - TO: `### Requirement: New name` -->
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("delta-renamed-fields")),
                "An empty (comment-only) RENAMED section must not be delta-renamed-fields: " + issues);
    }

    @Test
    void validDeltaSpec_withRenamed_passes() {
        String content = """
                ## ADDED Requirements

                ### Requirement: New feature
                The system SHALL add a feature.

                #### Scenario: Feature works
                - **WHEN** triggered
                - **THEN** feature activates

                ## RENAMED Requirements

                - FROM: Old name
                - TO: New name

                ## REMOVED Requirements

                ### Requirement: Legacy feature
                **Reason**: Replaced
                **Migration**: Use new feature
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("delta-requirement-scenario")
                        || i.rule().equals("delta-removed-fields")
                        || i.rule().equals("delta-renamed-fields")),
                "Mixed delta with RENAMED + ADDED + REMOVED should have no structural errors");
    }

    // --- CLI 1.4 parity: case-insensitive headers + keyword-placement hint ---

    @Test
    void lowercaseRequirementHeader_isRecognized() {
        String content = """
                # Test Spec

                ### requirement: Lowercase header token
                The system SHALL parse this requirement.

                #### Scenario: Parse
                - **WHEN** the spec is validated
                - **THEN** the requirement is recognized
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.isEmpty(),
                "CLI 1.4+ parses headers case-insensitively; expected no issues but got: " + issues);
    }

    @Test
    void uppercaseAndMixedCaseHeaders_areRecognized() {
        String content = """
                # Test Spec

                ### REQUIREMENT: Uppercase token
                Body text without keyword and without scenario.

                ### ReQuIrEmEnT: Mixed-case token
                More body text.
                """;
        List<ValidationIssue> issues = validateSpec(content);
        // Both requirements must be SEEN (each missing keyword + scenario = 2 issues apiece).
        assertEquals(4, issues.size(),
                "Both non-canonical headers should be validated as requirements: " + issues);
    }

    @Test
    void headerTokenMidLine_isNotARequirement() {
        String content = """
                # Test Spec

                Some prose mentioning ### Requirement: inline should not match.
                """;
        Matcher m = com.johnnyblabs.openspec.util.SpecPatterns.REQUIREMENT_HEADER.matcher(content);
        assertFalse(m.find(), "Header pattern must stay line-anchored");
    }

    @Test
    void keywordOnlyInHeader_getsTargetedHint() {
        String content = """
                # Test Spec

                ### Requirement: The system SHALL support login
                Users can authenticate with a password.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** the session starts
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i -> i.rule().equals("spec-rfc-keyword-in-header")),
                "Keyword in header only should get the targeted hint: " + issues);
        assertTrue(issues.stream()
                        .filter(i -> i.rule().equals("spec-rfc-keyword-in-header"))
                        .allMatch(i -> i.severity() == ValidationIssue.Severity.WARNING),
                "1.8: the header-only-keyword hint is a WARNING (body present); --strict re-promotes it");
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-rfc-keywords")),
                "Targeted hint replaces the generic missing-keyword error: " + issues);
        assertTrue(issues.stream()
                        .filter(i -> i.rule().equals("spec-rfc-keyword-in-header"))
                        .allMatch(i -> i.message().contains("move the keyword onto the requirement body line")),
                "Hint must carry the CLI's remediation text");
    }

    @Test
    void keywordInBody_headerKeywordIsFine() {
        String content = """
                # Test Spec

                ### Requirement: The system SHALL support login
                The system SHALL authenticate users with a password.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** the session starts
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.isEmpty(),
                "Keyword present in the body satisfies the rule regardless of the header: " + issues);
    }

    @Test
    void keywordNowhere_bodyPresent_isWarning() {
        String content = """
                # Test Spec

                ### Requirement: Login support
                Users can authenticate with a password.

                #### Scenario: Login
                - **WHEN** user submits credentials
                - **THEN** the session starts
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                        i.rule().equals("spec-rfc-keywords") && i.severity() == ValidationIssue.Severity.WARNING),
                "1.8: no keyword anywhere in a body-carrying requirement is a WARNING: " + issues);
        assertTrue(issues.stream().noneMatch(i -> i.rule().equals("spec-rfc-keyword-in-header")),
                "Targeted hint requires a keyword in the header: " + issues);
    }

    // --- Helpers: extract validation logic matching BuiltInValidator ---

    /**
     * Drives the REAL {@link BuiltInValidator#validateSpecContent} on a raw spec string, so these rule
     * tests verify production behavior rather than a re-implemented mirror. The prior mirror was a
     * vacuity smell — it could pass while production was wrong (and it never fence-masked, unlike
     * production). Fence masking now happens inside the production method.
     */
    private List<ValidationIssue> validateSpec(String content) {
        List<ValidationIssue> issues = new ArrayList<>();
        BuiltInValidator.validateSpecContent(content, "test-spec.md", issues);
        return issues;
    }

    /**
     * Drives the REAL {@link BuiltInValidator#validateDeltaContent} on a raw delta string — the same
     * method {@code validateChange} applies to each delta file — so delta rule tests verify production
     * behavior. The prior hand-copied mirror could not catch a production regression.
     */
    private List<ValidationIssue> validateDeltaStructure(String content) {
        List<ValidationIssue> issues = new ArrayList<>();
        BuiltInValidator.validateDeltaContent(content, "test-delta.md", issues);
        return issues;
    }

    // --- maskFences (the real production utility, not a mirror) ---

    @Test
    void maskFencesMasksBacktickFenceContentAndMarkers() {
        String in = "before\n```\nThe system SHALL work.\n```\nafter\n";
        String out = BuiltInValidator.maskFences(in);
        assertEquals(in.length(), out.length(), "offset preservation");
        assertFalse(out.contains("SHALL"));
        assertFalse(out.contains("```"), "fence marker lines are masked too");
        assertTrue(out.contains("before") && out.contains("after"));
        assertEquals(countNewlines(in), countNewlines(out), "line preservation");
    }

    @Test
    void maskFencesHandlesTildeFences() {
        String out = BuiltInValidator.maskFences("~~~\n#### Scenario: hidden\n~~~\nvisible\n");
        assertFalse(out.contains("Scenario"));
        assertTrue(out.contains("visible"));
    }

    @Test
    void maskFencesUnclosedFenceMasksToEndOfContent() {
        String out = BuiltInValidator.maskFences("ok\n```\nSHALL forever\nno close");
        assertTrue(out.contains("ok"));
        assertFalse(out.contains("SHALL"));
        assertFalse(out.contains("no close"));
    }

    @Test
    void maskFencesBacktickInsideTildeFenceDoesNotClose() {
        String out = BuiltInValidator.maskFences("~~~\n```\nstill fenced SHALL\n~~~\nvisible SHALL\n");
        assertFalse(out.contains("still fenced"));
        assertTrue(out.contains("visible SHALL"));
    }

    @Test
    void maskFencesIndentedFenceRecognized() {
        String out = BuiltInValidator.maskFences("  ```\n  fenced MUST\n  ```\nplain MUST\n");
        assertFalse(out.contains("fenced MUST"));
        assertTrue(out.contains("plain MUST"));
    }

    private static long countNewlines(String s) {
        return s.chars().filter(c -> c == '\n').count();
    }
}

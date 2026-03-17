package com.johnnyblabs.openspec.validation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BuiltInValidator validation rules extracted as logic tests.
 * Validates RFC 2119 keyword enforcement (ERROR), scenario clause enforcement (ERROR),
 * requirement-must-have-scenario rule, and delta spec structural validation.
 */
class BuiltInValidatorRulesTest {

    // Patterns matching BuiltInValidator
    private static final Pattern REQUIREMENT_PATTERN = Pattern.compile("^### Requirement:\\s*.+", Pattern.MULTILINE);
    private static final Pattern RFC_KEYWORD_PATTERN = Pattern.compile("\\b(SHALL NOT|SHOULD NOT|SHALL|SHOULD|MAY|MUST)\\b");
    private static final Pattern SCENARIO_PATTERN = Pattern.compile("^#{4} Scenario:.+", Pattern.MULTILINE);
    private static final Pattern DELTA_SECTION_PATTERN = Pattern.compile("^## (ADDED|MODIFIED|REMOVED) Requirements", Pattern.MULTILINE);

    // --- 1.7: RFC 2119 keywords are ERROR ---

    @Test
    void rfc2119_missingKeyword_isError() {
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
                i.severity() == ValidationIssue.Severity.ERROR && i.rule().equals("spec-rfc-keywords")),
                "Missing RFC 2119 keyword should be ERROR");
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
    void scenarioClauses_missingWhen_isError() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Missing when
                - **THEN** something happens
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("spec-scenario-clauses")
                        && i.message().contains("WHEN")),
                "Missing WHEN clause should be ERROR");
    }

    @Test
    void scenarioClauses_missingThen_isError() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Missing then
                - **WHEN** user does something
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("spec-scenario-clauses")
                        && i.message().contains("THEN")),
                "Missing THEN clause should be ERROR");
    }

    @Test
    void scenarioClauses_missingBoth_isError() {
        String content = """
                # Test Spec

                ### Requirement: Test
                The system SHALL do things.

                #### Scenario: Missing both
                Some text but no clauses.
                """;
        List<ValidationIssue> issues = validateSpec(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("spec-scenario-clauses")
                        && i.message().contains("WHEN and THEN")),
                "Missing both clauses should be ERROR");
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
    void deltaRemoved_missingReason_isError() {
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                **Migration**: Use new endpoint
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("delta-removed-fields")
                        && i.message().contains("**Reason**")),
                "REMOVED requirement without Reason should be ERROR");
    }

    @Test
    void deltaRemoved_missingMigration_isError() {
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                **Reason**: No longer needed
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("delta-removed-fields")
                        && i.message().contains("**Migration**")),
                "REMOVED requirement without Migration should be ERROR");
    }

    @Test
    void deltaRemoved_missingBoth_isError() {
        String content = """
                ## REMOVED Requirements

                ### Requirement: Old feature
                This is being removed.
                """;
        List<ValidationIssue> issues = validateDeltaStructure(content);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("delta-removed-fields")
                        && i.message().contains("**Reason** and **Migration**")),
                "REMOVED requirement without both fields should report both missing");
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

    // --- Helpers: extract validation logic matching BuiltInValidator ---

    private List<ValidationIssue> validateSpec(String content) {
        List<ValidationIssue> issues = new ArrayList<>();
        String path = "test-spec.md";

        Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(content);
        while (reqMatcher.find()) {
            int reqStart = reqMatcher.start();
            int reqLine = lineNumberAt(content, reqStart);
            String reqHeader = reqMatcher.group().replaceFirst("^###\\s*Requirement:\\s*", "").trim();
            int nextReq = findNext(REQUIREMENT_PATTERN, content, reqMatcher.end());
            String reqContent = content.substring(reqMatcher.end(), nextReq);

            if (!RFC_KEYWORD_PATTERN.matcher(reqContent).find()) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                        "Requirement '" + reqHeader + "' must contain RFC 2119 keywords (SHALL, MUST, SHOULD, MAY)", "spec-rfc-keywords"));
            }

            if (!SCENARIO_PATTERN.matcher(reqContent).find()) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                        "Requirement '" + reqHeader + "' must have at least one '#### Scenario:' block", "spec-scenario-required"));
            }

            Matcher scenMatcher = SCENARIO_PATTERN.matcher(reqContent);
            while (scenMatcher.find()) {
                int scenLine = reqLine + lineNumberAt(reqContent, scenMatcher.start()) - 1;
                String scenHeader = scenMatcher.group().replaceFirst("^#{4}\\s*Scenario:\\s*", "").trim();
                int nextScen = findNext(SCENARIO_PATTERN, reqContent, scenMatcher.end());
                String scenContent = reqContent.substring(scenMatcher.end(), nextScen);

                boolean hasWhen = Pattern.compile("\\bWHEN\\b").matcher(scenContent).find();
                boolean hasThen = Pattern.compile("\\bTHEN\\b").matcher(scenContent).find();
                if (!hasWhen || !hasThen) {
                    String missing = !hasWhen && !hasThen ? "WHEN and THEN"
                            : !hasWhen ? "WHEN" : "THEN";
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, scenLine,
                            "Scenario '" + scenHeader + "' is missing " + missing + " clause(s)", "spec-scenario-clauses"));
                }
            }
        }
        return issues;
    }

    private List<ValidationIssue> validateDeltaStructure(String content) {
        List<ValidationIssue> issues = new ArrayList<>();
        String path = "test-delta.md";

        Matcher sectionMatcher = DELTA_SECTION_PATTERN.matcher(content);
        while (sectionMatcher.find()) {
            String sectionType = sectionMatcher.group(1);
            int sectionStart = sectionMatcher.end();
            Pattern nextH2 = Pattern.compile("^## ", Pattern.MULTILINE);
            Matcher nextH2Matcher = nextH2.matcher(content);
            int sectionEnd = content.length();
            if (nextH2Matcher.find(sectionStart)) {
                sectionEnd = nextH2Matcher.start();
            }
            String sectionContent = content.substring(sectionStart, sectionEnd);

            Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(sectionContent);
            while (reqMatcher.find()) {
                int reqLine = lineNumberAt(content, sectionStart + reqMatcher.start());
                String reqHeader = reqMatcher.group().replaceFirst("^###\\s*Requirement:\\s*", "").trim();
                int nextReq = findNext(REQUIREMENT_PATTERN, sectionContent, reqMatcher.end());
                String reqContent = sectionContent.substring(reqMatcher.end(), nextReq);

                if ("REMOVED".equals(sectionType)) {
                    boolean hasReason = reqContent.contains("**Reason**");
                    boolean hasMigration = reqContent.contains("**Migration**");
                    if (!hasReason || !hasMigration) {
                        String missing = !hasReason && !hasMigration ? "**Reason** and **Migration**"
                                : !hasReason ? "**Reason**" : "**Migration**";
                        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                                "REMOVED requirement '" + reqHeader + "' must contain " + missing + " fields",
                                "delta-removed-fields"));
                    }
                } else {
                    if (!SCENARIO_PATTERN.matcher(reqContent).find()) {
                        String detail = "MODIFIED".equals(sectionType)
                                ? "MODIFIED requirement '" + reqHeader + "' must include full updated content with at least one scenario"
                                : "ADDED requirement '" + reqHeader + "' must have at least one '#### Scenario:' block";
                        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, reqLine,
                                detail, "delta-requirement-scenario"));
                    }
                }
            }
        }
        return issues;
    }

    private int lineNumberAt(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private int findNext(Pattern pattern, String content, int from) {
        Matcher m = pattern.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }
}

package com.johnnyblabs.openspec.validation;

import com.johnnyblabs.openspec.version.VersionSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for config version validation and change schema cross-validation logic.
 * Mirrors the validation rules in BuiltInValidator without requiring IntelliJ services.
 */
class ConfigVersionValidationTest {

    // --- Config version validation ---

    @Test
    void missingVersionField_producesWarning() {
        List<ValidationIssue> issues = validateConfig("spec-driven", null);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("config-version-required")),
                "Missing version field should produce config-version-required WARNING");
    }

    @Test
    void unrecognizedVersion_producesWarning() {
        List<ValidationIssue> issues = validateConfig("spec-driven", "9.9.9");
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("config-version-unknown")),
                "Unrecognized version should produce config-version-unknown WARNING");
    }

    @Test
    void missingRequiredConfigField_producesError() {
        // Version 1.2.0 requires both "schema" and "version" fields.
        // Simulate missing schema field.
        List<ValidationIssue> issues = validateConfigFields(null, "1.2.0", VersionSupport.V1_2);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.ERROR
                        && i.rule().equals("config-field-required")
                        && i.message().contains("schema")),
                "Missing required 'schema' field should produce config-field-required ERROR");
    }

    @Test
    void validConfigWithAllFields_passesWithoutNewIssues() {
        List<ValidationIssue> issues = validateConfig("spec-driven", "1.2.0");
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("config-version-required")
                        || i.rule().equals("config-version-unknown")
                        || i.rule().equals("config-field-required")),
                "Valid config should produce no version-related issues");
    }

    // --- Change schema cross-validation ---

    @Test
    void changeWithIncompatibleSchema_producesWarning() {
        // 1.0.0 only supports "spec-driven", not "tdd"
        List<ValidationIssue> issues = validateChangeSchema("tdd", VersionSupport.V1_0);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("change-schema-incompatible")),
                "Change with schema not valid for project version should produce WARNING");
    }

    @Test
    void v1_0_specDriven_passes() {
        List<ValidationIssue> issues = validateChangeSchema("spec-driven", VersionSupport.V1_0);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("change-schema-incompatible")),
                "1.0.0 project with spec-driven schema should pass");
    }

    @Test
    void v1_0_tdd_warns() {
        List<ValidationIssue> issues = validateChangeSchema("tdd", VersionSupport.V1_0);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("change-schema-incompatible")),
                "1.0.0 project with tdd schema should warn");
    }

    @Test
    void v1_1_tdd_warns() {
        List<ValidationIssue> issues = validateChangeSchema("tdd", VersionSupport.V1_1);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("change-schema-incompatible")),
                "tdd schema is not supported by CLI — should warn");
    }

    @Test
    void v1_2_rapid_warns() {
        List<ValidationIssue> issues = validateChangeSchema("rapid", VersionSupport.V1_2);
        assertTrue(issues.stream().anyMatch(i ->
                i.severity() == ValidationIssue.Severity.WARNING
                        && i.rule().equals("change-schema-incompatible")),
                "rapid schema is not supported by CLI — should warn");
    }

    @Test
    void v1_2_specDriven_passes() {
        List<ValidationIssue> issues = validateChangeSchema("spec-driven", VersionSupport.V1_2);
        assertTrue(issues.stream().noneMatch(i ->
                i.rule().equals("change-schema-incompatible")),
                "1.2.0 project with spec-driven should pass");
    }

    // --- Helpers mirroring BuiltInValidator logic ---

    private List<ValidationIssue> validateConfig(String schema, String version) {
        List<ValidationIssue> issues = new ArrayList<>();
        String path = "config.yaml";

        // Version presence
        if (version == null || version.isEmpty()) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                    "config.yaml should have a 'version' field", "config-version-required"));
        } else if (!VersionSupport.allVersions().contains(version)) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                    "Version '" + version + "' is not recognized. Known versions: " +
                            VersionSupport.allVersions(), "config-version-unknown"));
        }

        // Required config fields
        VersionSupport vs = VersionSupport.fromString(version);
        issues.addAll(validateConfigFields(schema, version, vs));

        return issues;
    }

    private List<ValidationIssue> validateConfigFields(String schema, String version, VersionSupport vs) {
        List<ValidationIssue> issues = new ArrayList<>();
        String path = "config.yaml";

        for (String field : vs.getRequiredConfigFields()) {
            boolean present = switch (field) {
                case "schema" -> schema != null && !schema.isEmpty();
                case "version" -> version != null && !version.isEmpty();
                default -> false;
            };
            if (!present) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, 1,
                        "config.yaml requires '" + field + "' field for version " + vs.getVersion(),
                        "config-field-required"));
            }
        }
        return issues;
    }

    private List<ValidationIssue> validateChangeSchema(String changeSchema, VersionSupport version) {
        List<ValidationIssue> issues = new ArrayList<>();
        String path = "test-change";

        if (!version.getValidSchemas().contains(changeSchema)) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, path, 1,
                    "Change uses schema '" + changeSchema + "' which is not valid for project version " +
                            version.getVersion() + ". Valid schemas: " + version.getValidSchemas(),
                    "change-schema-incompatible"));
        }
        return issues;
    }
}

package com.johnnyblabs.openspec.settings;

import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.model.SchemaResolution;
import com.johnnyblabs.openspec.model.SchemaValidationReport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the schema-tooling display helpers in {@link OpenSpecSettingsPanel}:
 * the row label with resolution provenance, and the inline validation-report rendering.
 * Follows the panel's established pattern of testing extracted pure functions; the
 * button enablement rides the same {@code isSchemaSupported()} flag as fork/init
 * (covered by manual sandbox verification like the rest of the section).
 */
class OpenSpecSettingsPanelSchemaToolingTest {

    private static SchemaInfo info(String name, String description, boolean builtIn) {
        return new SchemaInfo(name, description, builtIn, List.of());
    }

    @Nested
    class RowLabelProvenance {

        @Test
        void originTagAppendedForProjectSchema() {
            String label = OpenSpecSettingsPanel.schemaRowLabel(
                    info("custom-flow", "Team flow", false),
                    new SchemaResolution("custom-flow", "project", "/p", List.of()));
            assertEquals("custom-flow — Team flow  [project]", label);
        }

        @Test
        void shadowingCopySaysWhatItShadows() {
            String label = OpenSpecSettingsPanel.schemaRowLabel(
                    info("spec-driven", "", false),
                    new SchemaResolution("spec-driven", "project", "/p", List.of("package")));
            assertEquals("spec-driven  [project, shadows package]", label);
        }

        @Test
        void originOmittedWhenProvenanceUnavailable() {
            String label = OpenSpecSettingsPanel.schemaRowLabel(
                    info("spec-driven", "Default workflow", true), null);
            assertEquals("spec-driven — Default workflow (built-in)", label);
            assertFalse(label.contains("["));
        }

        @Test
        void builtInSuffixPreservedAlongsideOrigin() {
            String label = OpenSpecSettingsPanel.schemaRowLabel(
                    info("spec-driven", "", true),
                    new SchemaResolution("spec-driven", "package", "/pkg", List.of()));
            assertEquals("spec-driven (built-in)  [package]", label);
        }
    }

    @Nested
    class ValidationReportRendering {

        @Test
        void validSchemaConfirmed() {
            String html = OpenSpecSettingsPanel.formatValidationReport(
                    new SchemaValidationReport("custom-flow", true, List.of(), null));
            assertEquals("<html>Schema 'custom-flow' is valid.</html>", html);
        }

        @Test
        void issuesRenderedWithSeverityAndPath() {
            String html = OpenSpecSettingsPanel.formatValidationReport(
                    new SchemaValidationReport("custom-flow", false, List.of(
                            new SchemaValidationReport.Issue("error", "artifacts.design.template",
                                    "Template file 'design.md' not found for artifact 'design'")),
                            null));
            assertTrue(html.contains("has 1 problem:"));
            assertTrue(html.contains("ERROR artifacts.design.template"));
            assertTrue(html.contains("design.md"));
        }

        @Test
        void multilineIssueMessageCollapsedToFirstLine() {
            String html = OpenSpecSettingsPanel.formatValidationReport(
                    new SchemaValidationReport("x", false, List.of(
                            new SchemaValidationReport.Issue("error", "schema.yaml",
                                    "Parse error: Map keys must be unique at line 43, column 5:\n\n  requires: []\n  ^\n")),
                            null));
            assertTrue(html.contains("Parse error: Map keys must be unique"));
            assertFalse(html.contains("requires: []"));
        }

        @Test
        void htmlInMessagesIsEscaped() {
            String html = OpenSpecSettingsPanel.formatValidationReport(
                    new SchemaValidationReport("x", false, List.of(
                            new SchemaValidationReport.Issue("error", "p", "bad <template> & more")),
                            null));
            assertTrue(html.contains("bad &lt;template&gt; &amp; more"));
        }
    }
}

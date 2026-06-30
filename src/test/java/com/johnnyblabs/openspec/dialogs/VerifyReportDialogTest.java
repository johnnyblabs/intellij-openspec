package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.johnnyblabs.openspec.model.VerificationFinding;
import com.johnnyblabs.openspec.model.VerificationFinding.Dimension;
import com.johnnyblabs.openspec.model.VerificationFinding.Severity;
import com.johnnyblabs.openspec.model.VerificationReport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the pure rendering helpers of {@link VerifyReportDialog}. The dialog itself is a
 * {@link com.intellij.openapi.ui.DialogWrapper} and needs a running Application to construct,
 * so the logic-bearing pieces (summary/severity icon + color selection, finding HTML) are
 * extracted as static methods and exercised directly — same approach as the other dialog tests.
 */
class VerifyReportDialogTest {

    private static VerificationFinding finding(Severity severity, String description, String filePath) {
        return new VerificationFinding(severity, Dimension.COHERENCE, description, filePath, -1);
    }

    @Nested
    class FormatFinding {

        @Test
        void wraps_description_in_html() {
            String html = VerifyReportDialog.formatFinding(
                    new VerificationFinding(Severity.WARNING, Dimension.CORRECTNESS, "something is off"));
            assertEquals("<html>something is off</html>", html);
        }

        @Test
        void appends_basename_of_file_path_when_present() {
            String html = VerifyReportDialog.formatFinding(
                    finding(Severity.WARNING, "missing artifact", "/abs/openspec/changes/x/tasks.md"));
            // Only the basename is shown, italicised in parentheses — not the full path.
            assertEquals("<html>missing artifact <i>(tasks.md)</i></html>", html);
            assertFalse(html.contains("/abs/openspec/changes"), "full path must not leak into the label");
        }

        @Test
        void file_path_with_no_slash_is_used_verbatim() {
            String html = VerifyReportDialog.formatFinding(
                    finding(Severity.WARNING, "issue", "tasks.md"));
            assertEquals("<html>issue <i>(tasks.md)</i></html>", html);
        }

        @Test
        void no_file_suffix_when_path_is_null() {
            // The convenience constructor leaves filePath null.
            String html = VerifyReportDialog.formatFinding(
                    new VerificationFinding(Severity.SUGGESTION, Dimension.COHERENCE, "design has TBD markers"));
            assertEquals("<html>design has TBD markers</html>", html);
            assertFalse(html.contains("<i>"), "no file fragment expected when there is no path");
        }
    }

    @Nested
    class SeverityIcon {

        @Test
        void maps_each_severity_to_its_icon() {
            assertSame(AllIcons.General.Error, VerifyReportDialog.severityIcon(Severity.CRITICAL));
            assertSame(AllIcons.General.Warning, VerifyReportDialog.severityIcon(Severity.WARNING));
            assertSame(AllIcons.General.Information, VerifyReportDialog.severityIcon(Severity.SUGGESTION));
        }

        @Test
        void every_severity_resolves_to_a_distinct_non_null_icon() {
            // Guards the switch against a future severity being added without a branch
            // (an unmapped value would fail to compile) and against two severities colliding.
            assertNotNull(VerifyReportDialog.severityIcon(Severity.CRITICAL));
            assertNotNull(VerifyReportDialog.severityIcon(Severity.WARNING));
            assertNotNull(VerifyReportDialog.severityIcon(Severity.SUGGESTION));
            assertNotSame(VerifyReportDialog.severityIcon(Severity.CRITICAL),
                    VerifyReportDialog.severityIcon(Severity.WARNING));
            assertNotSame(VerifyReportDialog.severityIcon(Severity.WARNING),
                    VerifyReportDialog.severityIcon(Severity.SUGGESTION));
        }
    }

    @Nested
    class SeverityColor {

        @Test
        void critical_is_red_and_warning_is_orange() {
            assertSame(JBColor.RED, VerifyReportDialog.severityColor(Severity.CRITICAL));
            assertSame(JBColor.ORANGE, VerifyReportDialog.severityColor(Severity.WARNING));
        }

        @Test
        void suggestion_uses_default_foreground_not_an_alarm_color() {
            assertNotNull(VerifyReportDialog.severityColor(Severity.SUGGESTION));
            assertNotSame(JBColor.RED, VerifyReportDialog.severityColor(Severity.SUGGESTION));
            assertNotSame(JBColor.ORANGE, VerifyReportDialog.severityColor(Severity.SUGGESTION));
        }
    }

    @Nested
    class SummaryIcon {

        @Test
        void clean_report_shows_ok_icon() {
            VerificationReport report = new VerificationReport("test");
            assertTrue(report.isClean());
            assertSame(AllIcons.General.InspectionsOK, VerifyReportDialog.summaryIcon(report));
        }

        @Test
        void critical_report_shows_error_icon() {
            VerificationReport report = new VerificationReport("test");
            report.addFinding(new VerificationFinding(Severity.CRITICAL, Dimension.COMPLETENESS, "incomplete tasks"));
            assertSame(AllIcons.General.Error, VerifyReportDialog.summaryIcon(report));
        }

        @Test
        void non_critical_findings_show_warning_icon() {
            // Findings present but none critical → header warns rather than errors.
            VerificationReport report = new VerificationReport("test");
            report.addFinding(new VerificationFinding(Severity.WARNING, Dimension.CORRECTNESS, "gap"));
            report.addFinding(new VerificationFinding(Severity.SUGGESTION, Dimension.COHERENCE, "tbd"));
            assertFalse(report.isClean());
            assertFalse(report.hasCritical());
            assertSame(AllIcons.General.Warning, VerifyReportDialog.summaryIcon(report));
        }
    }
}

package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.toolwindow.SpecPreviewRenderer.ChangeShowKind;
import com.johnnyblabs.openspec.toolwindow.SpecPreviewRenderer.ChangeShowOutcome;
import com.johnnyblabs.openspec.util.CliJson;
import com.johnnyblabs.openspec.util.CliRunner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for what the Browse preview shows when {@code openspec show <change> --type change --json} doesn't
 * answer with deltas, against real runs captured by {@code scripts/capture-error-fixtures.py}: 1.15.0 answers with an
 * error envelope; 1.3.1 has no JSON and puts {@code ✖ Error: …} on stderr after a harmless flags warning.
 */
class ChangeShowOutcomeContractTest {

    private static String fixture(String path) {
        try (InputStream is = ChangeShowOutcomeContractTest.class.getResourceAsStream("/fixtures/cli/" + path)) {
            assertNotNull(is, "missing fixture " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    /** The captured run {@code <version>/errors/<name>} as the CLI result the preview would get. */
    private static CliRunner.CliResult run(String version, String name) {
        String base = version + "/errors/" + name;
        return new CliRunner.CliResult(Integer.parseInt(fixture(base + ".exit").strip()),
                fixture(base + ".stdout"), fixture(base + ".stderr"));
    }

    @Nested
    class CapturedRejections {

        @Test
        void aMissingChangeOn1150IsRejectedWithTheClisMessage() {
            ChangeShowOutcome outcome = SpecPreviewRenderer.changeShowOutcome("nosuch", run("1.15.0", "show-change-missing"));

            assertEquals(ChangeShowKind.REJECTED, outcome.kind());
            assertTrue(outcome.fragment().contains(SpecPreviewRenderer.CLI_REJECTED_MARKER), outcome.fragment());
            assertTrue(outcome.fragment().contains("Change &quot;nosuch&quot; not found at /fixture/proj/openspec/changes/nosuch/proposal.md"),
                    outcome.fragment());
            assertFalse(outcome.fragment().contains(SpecPreviewRenderer.CLI_UNAVAILABLE_MARKER));
            assertFalse(outcome.fragment().contains("Install"), "the CLI is there; don't tell the user to install it");
        }

        @Test
        void aChangeWithNoProposalYetOn1150IsRejectedWithTheClisNextStep() {
            ChangeShowOutcome outcome = SpecPreviewRenderer.changeShowOutcome("noproposal",
                    run("1.15.0", "show-change-noproposal"));

            assertEquals(ChangeShowKind.REJECTED, outcome.kind());
            assertTrue(outcome.fragment().contains("has no proposal.md yet"), outcome.fragment());
            assertTrue(outcome.fragment().contains("openspec status --change noproposal"), outcome.fragment());
        }

        @Test
        void aCrlfStderrReasonIsCleanedTheSameWay() {
            CliRunner.CliResult lf = run("1.3.1", "show-change-missing");
            CliRunner.CliResult crlf = new CliRunner.CliResult(lf.exitCode(), lf.stdout(), lf.stderr().replace("\n", "\r\n"));

            assertEquals(SpecPreviewRenderer.changeShowOutcome("nosuch", lf).fragment(),
                    SpecPreviewRenderer.changeShowOutcome("nosuch", crlf).fragment());
        }

        @Test
        void a131StderrReasonDropsTheFlagsWarningAndTheErrorPrefix() {
            ChangeShowOutcome outcome = SpecPreviewRenderer.changeShowOutcome("nosuch", run("1.3.1", "show-change-missing"));

            assertEquals(ChangeShowKind.REJECTED, outcome.kind());
            String fragment = outcome.fragment();
            assertTrue(fragment.contains("The OpenSpec CLI said: Change &quot;nosuch&quot; not found at"), fragment);
            assertFalse(fragment.contains("Ignoring flags"), "the warning is printed on success too; it's never the reason");
            assertFalse(fragment.contains("✖") || fragment.contains("Error:"), fragment);
        }

        @Test
        void aRejectionIsNeverCached() {
            assertFalse(SpecPreviewRenderer.changeShowOutcome("nosuch", run("1.15.0", "show-change-missing")).cacheable());
        }
    }

    @Nested
    class OtherOutcomes {

        @Test
        void a131SuccessWithTheFlagsWarningOnStderrIsDeltasAndCacheable() {
            CliRunner.CliResult ok = run("1.3.1", "show-change-good");
            assertTrue(ok.stderr().contains("Ignoring flags"), "the capture carries the warning a success still prints");

            ChangeShowOutcome outcome = SpecPreviewRenderer.changeShowOutcome("good", ok);

            assertEquals(ChangeShowKind.DELTAS, outcome.kind());
            assertTrue(outcome.cacheable());
            assertFalse(outcome.fragment().contains(SpecPreviewRenderer.CLI_REJECTED_MARKER));
        }

        @Test
        void aCapturedShowWithDeltasRendersThem() {
            ChangeShowOutcome outcome = SpecPreviewRenderer.changeShowOutcome("c",
                    new CliRunner.CliResult(0, fixture("1.14.3/additive-shapes/show-change.json"), ""));

            assertEquals(ChangeShowKind.DELTAS, outcome.kind());
            assertTrue(outcome.fragment().contains("demo"), outcome.fragment());
        }

        @Test
        void aCliThatCannotStartIsUnavailable() {
            CliRunner.CliException cannotStart =
                    new CliRunner.CliException("Failed to run OpenSpec CLI: not found", new IOException("not found"));

            ChangeShowOutcome outcome = SpecPreviewRenderer.changeShowOutcome("add-auth", cannotStart);

            assertEquals(ChangeShowKind.UNAVAILABLE, outcome.kind());
            assertTrue(outcome.fragment().contains(SpecPreviewRenderer.CLI_UNAVAILABLE_MARKER));
            assertFalse(outcome.cacheable());
        }

        @Test
        void aTimeoutIsRejectedNotUnavailable() {
            CliRunner.CliException timeout = CliRunner.CliException.timeout("OpenSpec CLI command timed out after 30s");

            ChangeShowOutcome outcome = SpecPreviewRenderer.changeShowOutcome("add-auth", timeout);

            assertEquals(ChangeShowKind.REJECTED, outcome.kind());
            assertTrue(outcome.fragment().contains("timed out after 30s"), outcome.fragment());
            assertFalse(outcome.fragment().contains(SpecPreviewRenderer.CLI_UNAVAILABLE_MARKER));
        }

        @Test
        void anAnswerThePluginCannotParseIsRejected() {
            // A JSON array where the change object belongs: CliJson reads it as Ok, the model parser can't.
            ChangeShowOutcome outcome = SpecPreviewRenderer.changeShowOutcome("c", new CliRunner.CliResult(0, "[1, 2]", ""));

            assertEquals(ChangeShowKind.REJECTED, outcome.kind());
            assertTrue(outcome.fragment().contains("couldn't read the OpenSpec CLI's answer"), outcome.fragment());
        }
    }

    @Nested
    class Placeholder {

        @Test
        void startsWithTheHeaderAndEscapesTheReason() {
            String fragment = SpecPreviewRenderer.cliRejectedPlaceholder("add-auth", "bad <tag> & \"quotes\"");

            assertTrue(fragment.startsWith("<div class=\"" + SpecPreviewRenderer.HEADER_MARKER + "\"><h1>Change Deltas — add-auth</h1>"),
                    fragment);
            assertTrue(fragment.contains(SpecPreviewRenderer.PURPOSE_LINE));
            assertTrue(fragment.contains("bad &lt;tag&gt; &amp; &quot;quotes&quot;"), fragment);
            assertFalse(fragment.contains("<tag>"), fragment);
            assertFalse(fragment.contains(SpecPreviewRenderer.CLI_UNAVAILABLE_MARKER), fragment);
        }

        @Test
        void theOpenLinkGoesAfterTheHeader() {
            String fragment = SpecPreviewRenderer.withOpenLink(
                    SpecPreviewRenderer.cliRejectedPlaceholder("add-auth", "reason"), "proposal.md");

            int header = fragment.indexOf(SpecPreviewRenderer.PURPOSE_LINE);
            int link = fragment.indexOf("Open proposal.md in editor");
            int marker = fragment.indexOf(SpecPreviewRenderer.CLI_REJECTED_MARKER);
            assertTrue(header >= 0 && link > header && marker > link, fragment);
        }

        @Test
        void anEnvelopeReasonKeepsTheClisFix() {
            CliJson.Failed failed = new CliJson.Failed(CliJson.Kind.ENVELOPE, "Change \"x\" not found.",
                    "Restore the directory.", "show_error", 1, null);

            assertEquals("Change \"x\" not found. Restore the directory.", SpecPreviewRenderer.cliReason(failed));
        }

        @Test
        void aStderrReasonWithNothingLeftFallsBackToTheMessage() {
            String onlyWarning = "Warning: Ignoring flags not applicable to change: scenarios";
            CliJson.Failed failed = new CliJson.Failed(CliJson.Kind.UNREADABLE, onlyWarning, null, null, 1, null);

            assertEquals(onlyWarning, SpecPreviewRenderer.cliReason(failed));
        }
    }
}

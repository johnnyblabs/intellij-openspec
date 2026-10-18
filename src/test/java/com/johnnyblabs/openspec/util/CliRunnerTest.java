package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CliRunner internals that don't require an IntelliJ Project context.
 */
class CliRunnerTest {

    @Nested
    class CommandLineTest {

        @Test
        void everyRunAsksForPlainOutput() {
            var cmd = CliRunner.buildCommandLine("/usr/local/bin/openspec", "/work", "status", "--json");
            var env = cmd.getEnvironment();
            assertEquals("1", env.get("NO_COLOR"));
            assertEquals("0", env.get("FORCE_COLOR"));
            assertEquals("1", env.get("OPENSPEC_NO_UPDATE_CHECK"));
            assertEquals("0", env.get("OPENSPEC_TELEMETRY"));
            assertEquals("/usr/local/bin/openspec", cmd.getExePath());
            assertEquals(java.util.List.of("status", "--json"), cmd.getParametersList().getList());
            // Compare Files, not strings: Windows reports this directory as \work.
            assertEquals(new java.io.File("/work"), cmd.getWorkDirectory());
        }

        @Test
        void runBuildsThroughTheHelper() throws Exception {
            // Guard against a second, environment-less command line creeping back into run().
            String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/johnnyblabs/openspec/util/CliRunner.java"));
            assertEquals(1, source.split("new GeneralCommandLine", -1).length - 1);
            assertTrue(source.contains("buildCommandLine(resolveCliPath(project)"));
            assertTrue(source.contains("return capturedResult(process.exitValue()"), "run() strips colour on capture");
        }

        @Test
        void capturedOutputHasNoColourCodes() {
            String ansi = CliFixtures.text("1.14.3/errors/update-legacy-pending-ansi.stdout");
            var r = CliRunner.capturedResult(1, ansi, "\u001B[31m✖\u001B[39m Error: boom");
            assertEquals(CliFixtures.text("1.14.3/errors/update-legacy-pending-plain.stdout"), r.stdout());
            assertEquals("✖ Error: boom", r.stderr());
            assertEquals(1, r.exitCode());
        }
    }

    @Nested
    class StripAnsiTest {

        @Test
        void colouredCaptureEqualsThePlainOneOnceStripped() {
            String ansi = CliFixtures.text("1.14.3/errors/update-legacy-pending-ansi.stdout");
            String plain = CliFixtures.text("1.14.3/errors/update-legacy-pending-plain.stdout");
            assertTrue(ansi.contains("\u001B["), "capture precondition: the coloured run has escape codes");
            assertEquals(plain, CliText.stripAnsi(ansi));
        }

        @Test
        void plainTextAndNullAreUnchanged() {
            assertEquals("plain", CliText.stripAnsi("plain"));
            assertNull(CliText.stripAnsi(null));
        }
    }

    @Nested
    class CliResultTest {

        @Test
        void successWhenExitCodeZero() {
            var result = new CliRunner.CliResult(0, "output", "");
            assertTrue(result.isSuccess());
        }

        @Test
        void failureWhenExitCodeNonZero() {
            var result = new CliRunner.CliResult(1, "", "error");
            assertFalse(result.isSuccess());
        }

        @Test
        void failureWhenExitCodeNegative() {
            var result = new CliRunner.CliResult(-1, "", "");
            assertFalse(result.isSuccess());
        }

        @Test
        void preservesStdoutAndStderr() {
            var result = new CliRunner.CliResult(0, "hello\nworld", "warn: something");
            assertEquals("hello\nworld", result.stdout());
            assertEquals("warn: something", result.stderr());
        }

        @Test
        void handlesEmptyOutput() {
            var result = new CliRunner.CliResult(0, "", "");
            assertTrue(result.isSuccess());
            assertEquals("", result.stdout());
            assertEquals("", result.stderr());
        }
    }

    @Nested
    class CliExceptionTest {

        @Test
        void messageOnly() {
            var ex = new CliRunner.CliException("command failed");
            assertEquals("command failed", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        void messageWithCause() {
            var cause = new RuntimeException("io error");
            var ex = new CliRunner.CliException("command failed", cause);
            assertEquals("command failed", ex.getMessage());
            assertSame(cause, ex.getCause());
        }
    }
}

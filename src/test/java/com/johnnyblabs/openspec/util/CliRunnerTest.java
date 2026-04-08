package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CliRunner internals that don't require an IntelliJ Project context.
 */
class CliRunnerTest {

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

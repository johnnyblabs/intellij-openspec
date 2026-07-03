package com.johnnyblabs.openspec.coordination;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.CliDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Windows-only integration test for the {@code .cmd} shim invocation path. On the GitHub Windows
 * matrix leg this exercises {@link CliDetectionService}'s real {@code .cmd}/{@code .bat}/{@code .exe}
 * suffix fallback end-to-end from a directory whose name contains spaces, then invokes a
 * store-listing command through the resolved shim and asserts the output round-trips through the
 * store parser.
 *
 * <p>The shim is self-contained (a crafted {@code openspec.cmd} that emits the captured 1.5.0
 * {@code store list --json} fixture), so the test does not depend on a globally installed CLI. The
 * workflow still provisions a real CLI on the Windows leg (task 1.4); this test is the authoritative,
 * deterministic check of the invocation mechanics. It is skipped on macOS/Linux (including the local
 * build) via {@code @EnabledOnOs(WINDOWS)}.
 */
class WindowsCmdShimInvocationTest {

    private static String fixture(String name) {
        String path = "/fixtures/cli/1.5.0/" + name;
        try (InputStream is = WindowsCmdShimInvocationTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void cmdShimFromSpacedPathDetectsAndRoundTrips(@TempDir Path tmp) throws Exception {
        // A bin directory whose name contains spaces — the case that breaks naive PATH handling.
        Path binDir = Files.createDirectories(tmp.resolve("Program Files").resolve("open spec bin"));
        // The shim's payload: the real captured store list JSON.
        Files.writeString(binDir.resolve("payload.json"), fixture("store-list.json"));
        // openspec.cmd: `--version` prints the version banner; anything else emits the payload.
        Path cmd = binDir.resolve("openspec.cmd");
        Files.writeString(cmd, String.join("\r\n",
                "@echo off",
                "if \"%~1\"==\"--version\" (",
                "  echo openspec 1.5.0",
                ") else (",
                "  type \"%~dp0payload.json\"",
                ")",
                ""));

        // 6.1 + 6.2: real CliDetectionService suffix resolution — pass the bare name (no .cmd); the
        // Windows suffix fallback must find openspec.cmd from the spaced path and detect 1.5.0.
        CliDetectionService svc = new CliDetectionService((Project) null);
        svc.setIsWindowsForTest(true);
        String bareName = binDir.resolve("openspec").toString();
        assertTrue(svc.tryPath(bareName),
                "the .cmd suffix fallback must resolve the shim from a spaced path");
        assertTrue(svc.getDetectedPath().endsWith("openspec.cmd"),
                "detection must land on the .cmd shim: " + svc.getDetectedPath());
        assertEquals("1.5.0", svc.getDetectedVersion());

        // Invoke a store command through the resolved shim and assert exit code 0.
        GeneralCommandLine cli = new GeneralCommandLine(svc.getDetectedPath(), "store", "list", "--json");
        cli.setCharset(StandardCharsets.UTF_8);
        Process process = cli.createProcess();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "shim invocation must not hang");
        assertEquals(0, process.exitValue(), "the .cmd shim invocation must exit 0");

        // 6.2: the invocation output round-trips through the real store parser.
        List<StoreEntry> stores = CoordinationService.parseStores(stdout);
        assertEquals(2, stores.size(), "shim output must parse to the captured store model");
        assertEquals("gitstore", stores.get(0).id());
        assertEquals("/fixture/gitstore", stores.get(0).root());
    }
}

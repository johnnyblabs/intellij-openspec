package com.johnnyblabs.openspec.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A CLI that starts but doesn't finish in time is reported as {@link CliRunner.CliException#timedOut()}, so callers
 * can tell it from a CLI that can't run at all (the Browse preview must not say "install the CLI" for it). Runs a real
 * process that sleeps past a one-second timeout, so it's macOS and Linux only.
 */
public class CliRunnerTimeoutTest extends BasePlatformTestCase {

    private int savedTimeout;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        savedTimeout = OpenSpecSettings.getInstance(getProject()).getCliTimeoutSeconds();
        // The CLI runs in the project's base dir, which a light project doesn't create on disk.
        Files.createDirectories(Path.of(getProject().getBasePath()));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecSettings.getInstance(getProject()).setCliTimeoutSeconds(savedTimeout);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    private void cliIs(String body) throws Exception {
        File script = new File(FileUtil.createTempDirectory("cli-timeout", null, true), "openspec");
        Files.writeString(script.toPath(), "#!/bin/sh\n" + body + "\n");
        assertTrue(script.setExecutable(true));
        CliDetectionService detection = getProject().getService(CliDetectionService.class);
        CliDetectionTestSupport.fakeAvailable(detection, "1.15.0");
        assertTrue(detection.tryPath(script.getPath()));
    }

    private CliRunner.CliException runExpectingFailure() {
        try {
            CliRunner.run(getProject(), "show", "x", "--json");
        } catch (CliRunner.CliException e) {
            return e;
        }
        fail("the run was expected to fail");
        return null;
    }

    public void testAHungCliIsReportedAsTimedOut() throws Exception {
        if (SystemInfo.isWindows) return;
        cliIs("sleep 10");
        OpenSpecSettings.getInstance(getProject()).setCliTimeoutSeconds(1);

        CliRunner.CliException error = runExpectingFailure();

        assertTrue(error.getMessage(), error.timedOut());
        assertTrue(error.getMessage(), error.getMessage().contains("timed out"));
    }

    public void testACliThatCannotStartIsNotReportedAsTimedOut() throws Exception {
        if (SystemInfo.isWindows) return;
        cliIs("exit 0");
        CliDetectionService detection = getProject().getService(CliDetectionService.class);
        File missing = new File(FileUtil.createTempDirectory("cli-missing", null, true), "openspec");
        // The version probe is faked, so detection accepts a path that doesn't exist; starting it then fails.
        assertTrue(detection.tryPath(missing.getPath()));

        CliRunner.CliException error = runExpectingFailure();

        assertFalse(error.getMessage(), error.timedOut());
    }
}

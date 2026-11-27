package com.johnnyblabs.openspec.scaffolding;

import com.johnnyblabs.openspec.CliFixtureSuite;

import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The warning for a failed CLI init, built from what the real CLI printed: 2.1.0 with no Git on PATH (reason on
 * stdout, tool on stderr), and 2.1.1 refusing a machine-wide init in the home folder (reason on stderr only).
 */
@CliFixtureSuite
class FailedInitNoticeContractTest {

    static CliRunner.CliResult captured(String version, String name) throws IOException {
        Path base = FixtureVersions.CLI.resolve(version).resolve("errors");
        return new CliRunner.CliResult(Integer.parseInt(Files.readString(base.resolve(name + ".exit")).strip()),
                Files.readString(base.resolve(name + ".stdout")), Files.readString(base.resolve(name + ".stderr")));
    }

    static String reasonLine(CliRunner.CliResult r) {
        return r.stdout().lines().filter(l -> l.startsWith("Failed:")).findFirst().orElseThrow();
    }

    @Test
    void theNoGitFailureCarriesTheReasonFromStdoutAndTheGitHint() throws IOException {
        CliRunner.CliResult noGit = captured("2.1.0", "init-no-git");
        assertNotEquals(0, noGit.exitCode());

        String text = ScaffoldingService.cliErrorText(noGit);
        assertTrue(text.startsWith(noGit.stderr().strip()), "the CLI's stderr comes first, unchanged");
        assertTrue(text.contains(reasonLine(noGit)), "the reason line from the stdout summary is kept");
        assertFalse(text.contains("Getting started"), "the rest of the summary is left out");

        String notice = ScaffoldingService.failedInitNotice(text, "2.1.0", true);
        assertTrue(notice.contains("Git is needed to install the OpenSpec skills"), notice);
        assertTrue(notice.endsWith(ScaffoldingService.GIT_NETWORK_HINT), "2.1.0 adds the Git and network sentence");
        assertTrue(notice.startsWith("The OpenSpec CLI set up openspec/ but couldn't finish."), notice);
    }

    @Test
    void aStderrOnlyFailureOn211IsShownAsWrittenWithoutTheHint() throws IOException {
        CliRunner.CliResult home = captured("2.1.1", "init-home-global");
        assertEquals(home.stderr().strip(), ScaffoldingService.cliErrorText(home));

        String notice = ScaffoldingService.failedInitNotice(ScaffoldingService.cliErrorText(home), "2.1.1", false);
        assertTrue(notice.contains("This is your home folder, where the global skills live."), notice);
        assertFalse(notice.contains(ScaffoldingService.GIT_NETWORK_HINT), "only 2.1.0 gets the hint");
        assertTrue(notice.contains("the plugin's built-in setup ran instead"), notice);
    }

    @Test
    void theHintIsForExactly210() {
        for (String other : new String[] {"2.0.0", "2.1.1", "2.2.0", "1.17.0"}) {
            assertFalse(ScaffoldingService.failedInitNotice("x", other, false).contains(ScaffoldingService.GIT_NETWORK_HINT), other);
        }
        assertFalse(ScaffoldingService.failedInitNotice("x", null, false).contains(ScaffoldingService.GIT_NETWORK_HINT));
    }

    @Test
    void anEmptyStderrFallsBackToStdout() {
        CliRunner.CliResult r = new CliRunner.CliResult(1, "  only stdout  \n", "");
        assertEquals("only stdout", ScaffoldingService.cliErrorText(r));
        assertEquals("openspec init exited with code 3", ScaffoldingService.cliErrorText(new CliRunner.CliResult(3, "", "")));
    }

    @Test
    void theCliTextIsEscapedForTheNotification() {
        assertTrue(ScaffoldingService.failedInitNotice("a <b> & c", "2.1.1", false).contains("a &lt;b&gt; &amp; c"));
    }
}

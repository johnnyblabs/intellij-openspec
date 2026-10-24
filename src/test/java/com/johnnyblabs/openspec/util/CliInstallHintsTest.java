package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The install hint against upstream's own words: every command must appear verbatim in the captured README Quick Start
 * ({@code fixtures/upstream/readme-quick-start-1.15.0.md}), so an upstream change shows up as a failing fixture
 * re-capture rather than a stale hint.
 */
class CliInstallHintsTest {

    private static String quickStart() {
        String path = "/fixtures/upstream/readme-quick-start-1.15.0.md";
        try (InputStream is = CliInstallHintsTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void everyCommandIsUpstreamsQuickStartWordForWord() {
        String readme = quickStart();
        assertTrue(readme.contains("\n" + CliInstallHints.standaloneCommand(false) + "\n"), "the macOS/Linux installer line");
        assertTrue(readme.contains("\n" + CliInstallHints.standaloneCommand(true) + "\n"), "the Windows installer line");
        assertTrue(readme.contains("`" + CliInstallHints.NPM + "`"), "the npm command");
        assertTrue(readme.contains("`" + CliInstallHints.BREW + "`"), "the Homebrew command");
        assertTrue(readme.contains(CliInstallHints.INSTALL_DOCS_URL), "the installation page the README links to");
    }

    @Test
    void unixHintLeadsWithTheStandaloneInstallerThenNpmThenBrew() {
        String html = CliInstallHints.html(false);
        int curl = html.indexOf("curl -fsSL https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.sh | sh");
        int npm = html.indexOf(CliInstallHints.NPM);
        int brew = html.indexOf(CliInstallHints.BREW);
        assertTrue(curl >= 0, html);
        assertTrue(curl < npm && npm < brew, "standalone, then npm, then Homebrew: " + html);
        assertFalse(html.contains("irm "), "no PowerShell command outside Windows");
    }

    @Test
    void windowsHintUsesPowerShellAndOffersNoHomebrewOrCurl() {
        String html = CliInstallHints.html(true);
        int irm = html.indexOf("irm https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.ps1 | iex");
        int npm = html.indexOf(CliInstallHints.NPM);
        assertTrue(irm >= 0 && irm < npm, "standalone, then npm: " + html);
        assertFalse(html.contains("brew"), html);
        assertFalse(html.contains("curl"), html);
    }

    @Test
    void htmlCarriesNoLinkBecauseLabelsCantFollowOne() {
        assertFalse(CliInstallHints.html(false).contains("<a "));
        assertFalse(CliInstallHints.html(true).contains("<a "));
    }

    @Test
    void notificationTextNamesTheRoutesButNoCommand() {
        for (boolean windows : new boolean[]{false, true}) {
            String text = CliInstallHints.notificationText(windows);
            assertTrue(text.contains("no Node.js"), text);
            assertTrue(text.contains("npm"), text);
            assertFalse(text.contains("curl") || text.contains("irm ") || text.contains("install -g") || text.contains("brew install"),
                    "a balloon names no command: " + text);
        }
        assertTrue(CliInstallHints.notificationText(false).contains("Homebrew"));
        assertFalse(CliInstallHints.notificationText(true).contains("Homebrew"));
    }

    @Test
    void standaloneLocationsMatchTheInstallersDefaults() {
        assertEquals("~/.local/bin/openspec", CliInstallHints.standaloneLocation(false));
        assertEquals("%LOCALAPPDATA%\\Programs\\openspec\\openspec.exe", CliInstallHints.standaloneLocation(true));
    }
}

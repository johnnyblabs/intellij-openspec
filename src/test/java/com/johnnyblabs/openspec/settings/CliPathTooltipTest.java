package com.johnnyblabs.openspec.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliPathTooltipTest {

    @Test
    void unixTooltipListsTheStandaloneLocationBesideTheExistingExamples() {
        String tip = OpenSpecSettingsPanel.cliPathTooltip(false);
        assertTrue(tip.contains("~/.local/bin/openspec"), tip);
        assertTrue(tip.contains("/opt/homebrew/bin/openspec") && tip.contains("/usr/local/bin/openspec"), tip);
        assertFalse(tip.contains("%LOCALAPPDATA%"), tip);
    }

    @Test
    void windowsTooltipListsTheStandaloneExeBesideTheNpmShim() {
        String tip = OpenSpecSettingsPanel.cliPathTooltip(true);
        assertTrue(tip.contains("%LOCALAPPDATA%\\Programs\\openspec\\openspec.exe"), tip);
        assertTrue(tip.contains("%APPDATA%\\npm\\openspec.cmd"), tip);
        assertFalse(tip.contains("~/.local/bin"), tip);
    }
}

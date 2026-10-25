package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The setup wizard and Manage AI Tools show the shared hint for the host OS; the wizard keeps its Windows path note.
 * A plain unit test on purpose: it must not load a dialog class, which would start the platform's UI scaling outside a
 * platform test and break {@code UIUtil} for every later test in the JVM (seen on the Windows CI leg).
 */
class InstallHintSurfacesTest {

    @Test
    void wizardCliStepShowsTheSharedHintPerOs() {
        assertTrue(CliInstallHints.wizardHintBody(false).contains(CliInstallHints.html(false)));
        String windows = CliInstallHints.wizardHintBody(true);
        assertTrue(windows.contains(CliInstallHints.html(true)));
        assertTrue(windows.contains("%LOCALAPPDATA%\\Programs\\openspec\\openspec.exe"), "the standalone path to set");
        assertTrue(windows.contains("%APPDATA%\\npm\\openspec.cmd"), "the npm path to set");
        assertFalse(CliInstallHints.wizardHintBody(false).contains(".cmd"), "the Windows note stays on Windows");
    }

    @Test
    void manageAiToolsFooterShowsTheSharedHintPerOs() {
        assertTrue(CliInstallHints.manageToolsHintBody(false).contains(CliInstallHints.html(false)));
        assertTrue(CliInstallHints.manageToolsHintBody(true).contains(CliInstallHints.html(true)));
    }
}

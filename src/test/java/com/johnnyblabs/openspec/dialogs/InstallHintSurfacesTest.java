package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.util.CliInstallHints;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The setup wizard and Manage AI Tools show the shared hint for the host OS; the wizard keeps its Windows path note. */
class InstallHintSurfacesTest {

    @Test
    void wizardCliStepShowsTheSharedHintPerOs() {
        assertTrue(SetupWizardDialog.cliHintBody(false).contains(CliInstallHints.html(false)));
        String windows = SetupWizardDialog.cliHintBody(true);
        assertTrue(windows.contains(CliInstallHints.html(true)));
        assertTrue(windows.contains("%LOCALAPPDATA%\\Programs\\openspec\\openspec.exe"), "the standalone path to set");
        assertTrue(windows.contains("%APPDATA%\\npm\\openspec.cmd"), "the npm path to set");
        assertFalse(SetupWizardDialog.cliHintBody(false).contains(".cmd"), "the Windows note stays on Windows");
    }

    @Test
    void manageAiToolsFooterShowsTheSharedHintPerOs() {
        assertTrue(ManageAiToolsDialog.cliHintBody(false).contains(CliInstallHints.html(false)));
        assertTrue(ManageAiToolsDialog.cliHintBody(true).contains(CliInstallHints.html(true)));
    }
}

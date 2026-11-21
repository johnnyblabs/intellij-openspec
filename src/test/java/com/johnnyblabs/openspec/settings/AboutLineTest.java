package com.johnnyblabs.openspec.settings;

import com.johnnyblabs.openspec.util.PluginVersions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Settings About line names this plugin, the CLI it targets, and the CLI detected on this machine. */
class AboutLineTest {

    @Test
    void aboutNamesThePluginTheTargetAndTheDetectedCli() {
        String text = OpenSpecSettingsPanel.aboutText("1.16.1");

        assertTrue(text.startsWith("Plugin "), text);
        assertTrue(text.contains("targets OpenSpec CLI " + PluginVersions.targetCliVersion()), text);
        assertTrue(text.endsWith("detected CLI 1.16.1"), text);
    }

    @Test
    void aboutSaysNotDetectedWhenNoCliIsFound() {
        assertTrue(OpenSpecSettingsPanel.aboutText(null).endsWith("detected CLI not detected"));
        assertTrue(OpenSpecSettingsPanel.aboutText("  ").endsWith("detected CLI not detected"));
    }
}

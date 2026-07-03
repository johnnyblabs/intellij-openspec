package com.johnnyblabs.openspec.settings;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The version-override combo targets the config-format version axis, which the plugin
 * deliberately pins at a single baseline (see {@code VersionSupport} / config.yaml
 * {@code version: 1.2.0}). Offering CLI-looking versions (1.3.0/1.4.0) there was misleading —
 * they were silently ignored. These tests lock the presets to values the axis actually models.
 * The panel can't be instantiated without a Project + Swing env, so we assert on the extracted
 * {@code VERSION_OVERRIDE_PRESETS} constant (same package = direct access).
 */
class OpenSpecSettingsPanelVersionOverrideTest {

    @Test
    void presets_do_not_offer_cli_looking_versions() {
        List<String> presets = Arrays.asList(OpenSpecSettingsPanel.VERSION_OVERRIDE_PRESETS);
        assertFalse(presets.contains("1.3.0"), "1.3.0 is a CLI-version-looking value the config-format axis does not model");
        assertFalse(presets.contains("1.4.0"), "1.4.0 is a CLI-version-looking value the config-format axis does not model");
    }

    @Test
    void presets_offer_empty_default_and_modeled_baseline() {
        List<String> presets = Arrays.asList(OpenSpecSettingsPanel.VERSION_OVERRIDE_PRESETS);
        assertTrue(presets.contains(""), "empty preset means 'use the config default'");
        assertTrue(presets.contains("1.2.0"), "1.2.0 is the modeled config-format baseline");
    }
}

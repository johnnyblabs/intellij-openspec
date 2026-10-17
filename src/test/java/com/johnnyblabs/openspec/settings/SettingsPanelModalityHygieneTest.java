package com.johnnyblabs.openspec.settings;

import com.johnnyblabs.openspec.ModalityCallScanner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every {@code invokeLater} in {@link OpenSpecSettingsPanel} must pass the Settings dialog's modality. The panel always
 * lives in the modal Settings dialog; a plain {@code invokeLater} from a background thread is NON_MODAL, so the platform
 * holds it until the dialog closes and the update lands on a panel nobody can see (the empty schema list). The headless
 * dialog test proves the behaviour for the schema list; this scan keeps every other site from regressing. The scanner
 * itself is locked by {@code ModalityCallScannerTest}.
 */
class SettingsPanelModalityHygieneTest {

    @Test
    void everyInvokeLaterInTheSettingsPanelPassesAModality() throws IOException {
        List<ModalityCallScanner.Call> calls =
                ModalityCallScanner.calls(ModalityCallScanner.mainSource("settings/OpenSpecSettingsPanel.java"));
        // Anti-vacuity: the panel has many background-result sites; the scan must actually find them.
        assertTrue(calls.size() >= 9, "expected the settings panel's invokeLater calls, found " + calls.size());

        List<String> missing = calls.stream().filter(c -> !c.hasSecondArgument())
                .map(c -> c.method() + " at line " + c.line()).toList();
        assertTrue(missing.isEmpty(),
                "invokeLater without the dialog's modality (capture uiModality() on the EDT and pass it): " + missing);
    }
}

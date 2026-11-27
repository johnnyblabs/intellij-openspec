package com.johnnyblabs.openspec.actions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Continue acts on the change it was launched for, and asks only without one. */
class ContinueLaunchedForTest {

    private static final List<String> ACTIVE = List.of("add-auth", "fix-cache");

    @Test
    void aChangeNodeOrThePanelNamesTheChange() {
        assertEquals("fix-cache", OpenSpecContinueAction.launchedFor("fix-cache", ACTIVE));
    }

    /** The action itself consults the launched-for change before asking. */
    @Test
    void theActionUsesTheLaunchedForChangeBeforeAsking() throws java.io.IOException {
        String code = java.nio.file.Files.readString(
                com.johnnyblabs.openspec.ModalityCallScanner.mainSource("actions/OpenSpecContinueAction.java"));
        int named = code.indexOf("launchedFor(e.getData(OpenSpecDataKeys.CHANGE_NAME), names)");
        int ask = code.indexOf("ChangePicker.pick(names");
        org.junit.jupiter.api.Assertions.assertTrue(named > 0 && ask > named,
                "actionPerformed must use the context's change before asking which one");
    }

    @Test
    void withoutAChangeOrForOneThatIsNotActiveItAsks() {
        assertNull(OpenSpecContinueAction.launchedFor(null, ACTIVE));
        assertNull(OpenSpecContinueAction.launchedFor("archived-one", ACTIVE));
    }
}

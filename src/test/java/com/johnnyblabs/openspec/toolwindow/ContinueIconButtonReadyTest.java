package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.johnnyblabs.openspec.actions.OpenSpecDataKeys;

import javax.swing.*;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** With a captured status whose design and specs are ready, the Continue button is enabled and runs Continue. */
public class ContinueIconButtonReadyTest extends ContinueIconButtonTestBase {

    @Override
    String statusFixture() {
        return "1.7.0/status-with-context.json";
    }

    public void testAReadyArtifactEnablesContinue_whichLaunchesFromTheButtonNamingTheChange() {
        workflowPanel.setProfileStateForTest(true, Set.of("continue"));
        JButton button = workflowPanel.continueIconButtonForTest();

        assertTrue("design and specs are ready", button.isEnabled());
        assertEquals("Continue: " + CHANGE, button.getToolTipText());
        assertEquals(CHANGE, ((DataProvider) button).getData(OpenSpecDataKeys.CHANGE_NAME.getName()));
        List<String> actions = new ArrayList<>();
        List<Component> sources = new ArrayList<>();
        workflowPanel.setLauncherForTest((action, source) -> {
            actions.add(ActionManager.getInstance().getId(action));
            sources.add(source);
        });
        button.doClick();
        assertEquals(List.of("OpenSpec.Continue"), actions);
        assertSame("launched from the button, whose data names the change", button, sources.get(0));
    }
}

package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.components.JBLabel;
import com.johnnyblabs.openspec.actions.OpenSpecDataKeys;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The workflow panel's Continue button, after Revise: hidden unless the profile includes {@code continue}, enabled while
 * the change has a ready artifact, and launched from the button whose data names the change. The "No changes yet" card
 * points a custom profile with workflows beyond core at the OpenSpec menu.
 */
public class ContinueIconButtonTest extends ContinueIconButtonTestBase {

    /** Every artifact done. */
    @Override
    String statusFixture() {
        return "1.7.0/status-complete.json";
    }

    /** With no CLI the profile is core, which has no continue: no Continue button, and no hint. */
    public void testTheCoreProfileShowsNoContinueButton() {
        assertFalse(workflowPanel.continueIconButtonForTest().isVisible());
    }

    /** With the profile's continue, the button sits after Revise; every artifact done, it is disabled and says so. */
    public void testAProfileWithContinueShowsItAfterRevise_disabledWhenEveryArtifactIsDone() {
        workflowPanel.setProfileStateForTest(true, Set.of("continue"));
        JButton button = workflowPanel.continueIconButtonForTest();

        assertTrue(button.isVisible());
        List<Component> buttons = List.of(button.getParent().getComponents());
        assertEquals("Continue sits right after Revise",
                buttons.indexOf(workflowPanel.reviseIconButtonForTest()) + 1, buttons.indexOf(button));
        assertFalse("every captured artifact is done", button.isEnabled());
        assertEquals("Continue (all artifacts done)", button.getToolTipText());
    }

    public void testContinueIsEnabledOnlyWithAReadyArtifact() {
        assertTrue(WorkflowActionPanel.continueEnabled(CHANGE, "design"));
        assertFalse(WorkflowActionPanel.continueEnabled(CHANGE, null));
        assertFalse(WorkflowActionPanel.continueEnabled(null, "design"));
        assertEquals("Continue: " + CHANGE, WorkflowActionPanel.continueTooltip(CHANGE, "design", false));
        assertEquals("Continue (all artifacts done)", WorkflowActionPanel.continueTooltip(CHANGE, null, true));
        assertEquals("Continue (no artifact is ready)", WorkflowActionPanel.continueTooltip(CHANGE, null, false));
    }

    public void testTheNoChangesCardHintsAtMoreWorkflowsOnlyForThem() {
        assertFalse("the installed card's hint is hidden on core", workflowPanel.moreWorkflowsLabelForTest().isVisible());
        JPanel card = workflowPanel.noChangesCardForTest();
        JBLabel hint = null;
        for (Component c : card.getComponents()) {
            if (c instanceof JBLabel l && WorkflowActionPanel.MORE_WORKFLOWS_HINT.equals(l.getText())) hint = l;
        }
        assertNotNull("the card has the hint line", hint);
        assertFalse("core: no hint", hint.isVisible());

        Set<String> extras = new LinkedHashSet<>(List.of("continue", "verify"));
        WorkflowActionPanel.applyMoreWorkflowsHint(hint, extras);
        assertTrue(hint.isVisible());
        assertEquals("Also in your profile: continue, verify", hint.getToolTipText());

        WorkflowActionPanel.applyMoreWorkflowsHint(hint, Set.of());
        assertFalse("only core workflows: no hint", hint.isVisible());
    }
}

package com.johnnyblabs.openspec.toolwindow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the wizard-skip and content-selection logic in OpenSpecToolWindowFactory.
 *
 * The factory decides what to show based on two inputs:
 * - Whether the project is initialized (openspec/ exists)
 * - Whether setupCompleted has been persisted
 *
 * Key rule: any initialized project (NOT_INITIALIZED excluded) gets the tree view.
 * The wizard only launches for truly uninitialized projects.
 */
class ToolWindowFactoryWizardTest {

    enum State { NOT_INITIALIZED, NO_AI_CONFIGURED, NO_CHANGES, READY }

    record ContentDecision(boolean showTree, boolean showGettingStarted,
                           boolean launchWizard, boolean autoCompleteSetup) {}

    /**
     * Mirrors the decision logic from OpenSpecToolWindowFactory.createToolWindowContent().
     */
    private static ContentDecision decide(State state, boolean setupCompleted) {
        boolean showTree = state != State.NOT_INITIALIZED;
        boolean showGettingStarted = state == State.NOT_INITIALIZED;
        boolean launchWizard = false;
        boolean autoCompleteSetup = false;

        if (!setupCompleted) {
            if (state == State.NOT_INITIALIZED) {
                launchWizard = true;
            } else {
                autoCompleteSetup = true;
            }
        }

        return new ContentDecision(showTree, showGettingStarted, launchWizard, autoCompleteSetup);
    }

    // --- Tree view shown for all initialized states ---

    @Test
    void readyState_showsTree() {
        ContentDecision d = decide(State.READY, true);
        assertTrue(d.showTree());
        assertFalse(d.showGettingStarted());
    }

    @Test
    void noChanges_showsTree() {
        ContentDecision d = decide(State.NO_CHANGES, true);
        assertTrue(d.showTree(), "Initialized project with no changes should still show tree for spec browsing");
        assertFalse(d.showGettingStarted());
    }

    @Test
    void noAiConfigured_showsTree() {
        ContentDecision d = decide(State.NO_AI_CONFIGURED, true);
        assertTrue(d.showTree(), "Initialized project without AI should still show tree for spec browsing");
        assertFalse(d.showGettingStarted());
    }

    @Test
    void notInitialized_showsGettingStarted() {
        ContentDecision d = decide(State.NOT_INITIALIZED, false);
        assertFalse(d.showTree());
        assertTrue(d.showGettingStarted());
    }

    // --- Wizard launch logic ---

    @Test
    void notInitialized_setupNotCompleted_launchesWizard() {
        ContentDecision d = decide(State.NOT_INITIALIZED, false);
        assertTrue(d.launchWizard(), "Wizard should launch for uninitialized projects");
    }

    @Test
    void notInitialized_setupAlreadyCompleted_skipsWizard() {
        ContentDecision d = decide(State.NOT_INITIALIZED, true);
        assertFalse(d.launchWizard(), "Wizard should not re-launch after setup was completed");
    }

    // --- Auto-complete setup for initialized projects ---

    @Test
    void ready_setupNotCompleted_autoCompletes() {
        ContentDecision d = decide(State.READY, false);
        assertFalse(d.launchWizard());
        assertTrue(d.autoCompleteSetup(), "setupCompleted should auto-set for initialized project with changes");
    }

    @Test
    void noChanges_setupNotCompleted_autoCompletes() {
        ContentDecision d = decide(State.NO_CHANGES, false);
        assertFalse(d.launchWizard());
        assertTrue(d.autoCompleteSetup(), "setupCompleted should auto-set for initialized project without changes");
    }

    @Test
    void noAiConfigured_setupNotCompleted_autoCompletes() {
        ContentDecision d = decide(State.NO_AI_CONFIGURED, false);
        assertFalse(d.launchWizard());
        assertTrue(d.autoCompleteSetup(), "setupCompleted should auto-set for initialized project without AI");
    }

    @Test
    void ready_setupAlreadyCompleted_noAutoComplete() {
        ContentDecision d = decide(State.READY, true);
        assertFalse(d.launchWizard());
        assertFalse(d.autoCompleteSetup(), "No need to auto-complete when already completed");
    }
}

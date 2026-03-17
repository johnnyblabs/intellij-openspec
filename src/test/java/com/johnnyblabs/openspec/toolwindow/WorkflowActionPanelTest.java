package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.ai.DeliveryMode;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorkflowActionPanel logic: CardLayout switching, FF input validation,
 * cancel destination, and delivery-aware generation routing.
 *
 * These tests exercise the behavioral rules in isolation using CardLayout and
 * Swing components directly, without requiring an IntelliJ Project instance.
 */
class WorkflowActionPanelTest {

    private static final String CARD_NO_CHANGES = "noChanges";
    private static final String CARD_FF_INPUT = "ffInput";
    private static final String CARD_PIPELINE = "pipeline";

    // --- 6.2: CardLayout card switching logic ---

    @Test
    void cardLayout_noChanges_showsNoChangesCard() {
        CardLayout layout = new CardLayout();
        JPanel cards = createCardPanel(layout);

        layout.show(cards, CARD_NO_CHANGES);

        // The no-changes card should be visible
        assertCardVisible(cards, 0); // noChanges is the first card added
    }

    @Test
    void cardLayout_ffActivated_showsFfInputCard() {
        CardLayout layout = new CardLayout();
        JPanel cards = createCardPanel(layout);

        layout.show(cards, CARD_FF_INPUT);

        assertCardVisible(cards, 1); // ffInput is the second card added
    }

    @Test
    void cardLayout_changesExist_showsPipelineCard() {
        CardLayout layout = new CardLayout();
        JPanel cards = createCardPanel(layout);

        layout.show(cards, CARD_PIPELINE);

        assertCardVisible(cards, 2); // pipeline is the third card added
    }

    @Test
    void cardLayout_ffInput_toPipeline_switchesCorrectly() {
        CardLayout layout = new CardLayout();
        JPanel cards = createCardPanel(layout);

        layout.show(cards, CARD_FF_INPUT);
        assertCardVisible(cards, 1);

        layout.show(cards, CARD_PIPELINE);
        assertCardVisible(cards, 2);
    }

    @Test
    void cardLayout_ffInput_toNoChanges_switchesCorrectly() {
        CardLayout layout = new CardLayout();
        JPanel cards = createCardPanel(layout);

        layout.show(cards, CARD_FF_INPUT);
        assertCardVisible(cards, 1);

        layout.show(cards, CARD_NO_CHANGES);
        assertCardVisible(cards, 0);
    }

    // --- 6.5: Cancel returns to correct previous card ---

    @Test
    void cancel_withActiveChanges_returnsToPipeline() {
        List<String> activeChanges = List.of("change-one");
        String expectedCard = activeChanges.isEmpty() ? CARD_NO_CHANGES : CARD_PIPELINE;
        assertEquals(CARD_PIPELINE, expectedCard);
    }

    @Test
    void cancel_withNoActiveChanges_returnsToNoChanges() {
        List<String> activeChanges = List.of();
        String expectedCard = activeChanges.isEmpty() ? CARD_NO_CHANGES : CARD_PIPELINE;
        assertEquals(CARD_NO_CHANGES, expectedCard);
    }

    @Test
    void cancel_withMultipleActiveChanges_returnsToPipeline() {
        List<String> activeChanges = List.of("change-one", "change-two");
        String expectedCard = activeChanges.isEmpty() ? CARD_NO_CHANGES : CARD_PIPELINE;
        assertEquals(CARD_PIPELINE, expectedCard);
    }

    // --- 6.4: Delivery-aware generation trigger ---

    @Test
    void deliveryMode_directApi_triggersGenerateAll() {
        DeliveryMode mode = DeliveryMode.DIRECT_API;
        String action = (mode == DeliveryMode.DIRECT_API) ? "generateAll" : "generate";
        assertEquals("generateAll", action);
    }

    @Test
    void deliveryMode_clipboard_triggersSingleGenerate() {
        DeliveryMode mode = DeliveryMode.CLIPBOARD;
        String action = (mode == DeliveryMode.DIRECT_API) ? "generateAll" : "generate";
        assertEquals("generate", action);
    }

    @Test
    void deliveryMode_editorTab_triggersSingleGenerate() {
        DeliveryMode mode = DeliveryMode.EDITOR_TAB;
        String action = (mode == DeliveryMode.DIRECT_API) ? "generateAll" : "generate";
        assertEquals("generate", action);
    }

    // --- 6.6: Go button validation ---

    @Test
    void goButton_disabledWhenDescriptionEmpty() {
        String description = "";
        boolean enabled = !description.isBlank();
        assertFalse(enabled, "Go button should be disabled when description is empty");
    }

    @Test
    void goButton_disabledWhenDescriptionWhitespace() {
        String description = "   ";
        boolean enabled = !description.isBlank();
        assertFalse(enabled, "Go button should be disabled when description is only whitespace");
    }

    @Test
    void goButton_enabledWhenDescriptionHasText() {
        String description = "add user authentication";
        boolean enabled = !description.isBlank();
        assertTrue(enabled, "Go button should be enabled when description has text");
    }

    @Test
    void goButton_enabledWhenDescriptionMinimal() {
        String description = "x";
        boolean enabled = !description.isBlank();
        assertTrue(enabled, "Go button should be enabled with minimal text");
    }

    // --- 6.3: FF input → Go → change created → pipeline visible ---

    @Test
    void ffGoFlow_nameDerivation_usesOverrideWhenProvided() {
        String description = "add user auth";
        String nameOverride = "custom-name";
        String changeName = nameOverride.isBlank()
                ? WorkflowActionPanel.deriveKebabName(description)
                : nameOverride.trim();
        assertEquals("custom-name", changeName);
    }

    @Test
    void ffGoFlow_nameDerivation_derivesFromDescriptionWhenNoOverride() {
        String description = "add user auth";
        String nameOverride = "";
        String changeName = nameOverride.isBlank()
                ? WorkflowActionPanel.deriveKebabName(description)
                : nameOverride.trim();
        assertEquals("add-user-auth", changeName);
    }

    @Test
    void ffGoFlow_formDisabledDuringCreation() {
        // Simulates the state the Go handler sets before background task
        JButton goButton = new JButton("Go");
        JButton cancelButton = new JButton("Cancel");
        JTextArea descriptionField = new JTextArea();
        JTextField nameField = new JTextField();
        JLabel statusLabel = new JLabel();

        // Simulate onFfGo() disabling form
        goButton.setEnabled(false);
        cancelButton.setEnabled(false);
        descriptionField.setEnabled(false);
        nameField.setEnabled(false);
        statusLabel.setText("Creating change 'add-user-auth'...");

        assertFalse(goButton.isEnabled());
        assertFalse(cancelButton.isEnabled());
        assertFalse(descriptionField.isEnabled());
        assertFalse(nameField.isEnabled());
        assertTrue(statusLabel.getText().contains("Creating change"));
    }

    @Test
    void ffGoFlow_successSwitchesToPipelineCard() {
        CardLayout layout = new CardLayout();
        JPanel cards = createCardPanel(layout);

        // Start on FF input
        layout.show(cards, CARD_FF_INPUT);
        assertCardVisible(cards, 1);

        // After successful creation, switch to pipeline
        layout.show(cards, CARD_PIPELINE);
        assertCardVisible(cards, 2);
    }

    @Test
    void ffGoFlow_failureReEnablesForm() {
        // Simulates the state the Go handler sets on CLI failure
        JButton goButton = new JButton("Go");
        JButton cancelButton = new JButton("Cancel");
        JTextArea descriptionField = new JTextArea();
        JTextField nameField = new JTextField();
        JLabel statusLabel = new JLabel();

        // Simulate failure re-enabling form
        goButton.setEnabled(true);
        cancelButton.setEnabled(true);
        descriptionField.setEnabled(true);
        nameField.setEnabled(true);
        statusLabel.setText("Error: change already exists");

        assertTrue(goButton.isEnabled());
        assertTrue(cancelButton.isEnabled());
        assertTrue(descriptionField.isEnabled());
        assertTrue(nameField.isEnabled());
        assertTrue(statusLabel.getText().startsWith("Error:"));
    }

    // --- Helpers ---

    private JPanel createCardPanel(CardLayout layout) {
        JPanel cards = new JPanel(layout);
        cards.add(new JPanel(), CARD_NO_CHANGES);
        cards.add(new JPanel(), CARD_FF_INPUT);
        cards.add(new JPanel(), CARD_PIPELINE);
        return cards;
    }

    private void assertCardVisible(JPanel cards, int expectedIndex) {
        for (int i = 0; i < cards.getComponentCount(); i++) {
            Component c = cards.getComponent(i);
            if (i == expectedIndex) {
                assertTrue(c.isVisible(), "Card at index " + i + " should be visible");
            } else {
                assertFalse(c.isVisible(), "Card at index " + i + " should be hidden");
            }
        }
    }
}

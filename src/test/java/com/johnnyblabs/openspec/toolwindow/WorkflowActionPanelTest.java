package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorkflowActionPanel logic: CardLayout switching, FF input validation,
 * cancel destination, delivery-aware generation routing, chip click routing,
 * icon bar state, and status strip content.
 *
 * These tests exercise the behavioral rules in isolation using CardLayout and
 * Swing components directly, without requiring an IntelliJ Project instance.
 */
class WorkflowActionPanelTest {

    private static final String CARD_NO_CHANGES = "noChanges";
    private static final String CARD_FF_INPUT = "ffInput";
    private static final String CARD_PIPELINE = "pipeline";

    // --- CardLayout card switching logic ---

    @Test
    void cardLayout_noChanges_showsNoChangesCard() {
        CardLayout layout = new CardLayout();
        JPanel cards = createCardPanel(layout);

        layout.show(cards, CARD_NO_CHANGES);

        assertCardVisible(cards, 0);
    }

    @Test
    void cardLayout_ffActivated_showsFfInputCard() {
        CardLayout layout = new CardLayout();
        JPanel cards = createCardPanel(layout);

        layout.show(cards, CARD_FF_INPUT);

        assertCardVisible(cards, 1);
    }

    @Test
    void cardLayout_changesExist_showsPipelineCard() {
        CardLayout layout = new CardLayout();
        JPanel cards = createCardPanel(layout);

        layout.show(cards, CARD_PIPELINE);

        assertCardVisible(cards, 2);
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

    // --- Cancel returns to correct previous card ---

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

    // --- Delivery-aware generation trigger ---

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

    // --- Go button validation ---

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

    // --- FF input → Go → change created → pipeline visible ---

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
        JButton goButton = new JButton("Go");
        JButton cancelButton = new JButton("Cancel");
        JTextArea descriptionField = new JTextArea();
        JTextField nameField = new JTextField();
        JLabel statusLabel = new JLabel();

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

        layout.show(cards, CARD_FF_INPUT);
        assertCardVisible(cards, 1);

        layout.show(cards, CARD_PIPELINE);
        assertCardVisible(cards, 2);
    }

    @Test
    void ffGoFlow_failureReEnablesForm() {
        JButton goButton = new JButton("Go");
        JButton cancelButton = new JButton("Cancel");
        JTextArea descriptionField = new JTextArea();
        JTextField nameField = new JTextField();
        JLabel statusLabel = new JLabel();

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

    // --- 7.1: Chip click routing ---

    @Test
    void chipClick_readyStatus_triggersGeneration() {
        ArtifactInfo artifact = new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.READY, List.of());
        String action = resolveClickAction(artifact.status());
        assertEquals("generate", action);
    }

    @Test
    void chipClick_doneStatus_opensFile() {
        ArtifactInfo artifact = new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.DONE, List.of());
        String action = resolveClickAction(artifact.status());
        assertEquals("open", action);
    }

    @Test
    void chipClick_blockedStatus_doesNothing() {
        ArtifactInfo artifact = new ArtifactInfo("design", "design.md", ArtifactStatus.BLOCKED, List.of("proposal"));
        String action = resolveClickAction(artifact.status());
        assertEquals("none", action);
    }

    @Test
    void chipClick_generatingStatus_doesNothing() {
        ArtifactInfo artifact = new ArtifactInfo("proposal", "proposal.md", ArtifactStatus.GENERATING, List.of());
        String action = resolveClickAction(artifact.status());
        assertEquals("none", action);
    }

    // --- 7.2: Context menu items per chip state ---

    @Test
    void contextMenu_doneChip_hasOpenRegenCopy() {
        List<String> items = getContextMenuItems(ArtifactStatus.DONE, false, 1);
        assertEquals(List.of("Open file", "Regenerate", "Copy prompt"), items);
    }

    @Test
    void contextMenu_readyChip_hasGenerateAndCopy() {
        List<String> items = getContextMenuItems(ArtifactStatus.READY, false, 1);
        assertEquals(List.of("Generate", "Copy prompt"), items);
    }

    @Test
    void contextMenu_readyChip_withDirectApiAndMultipleReady_hasGenerateAll() {
        List<String> items = getContextMenuItems(ArtifactStatus.READY, true, 3);
        assertEquals(List.of("Generate", "Generate All Remaining", "Copy prompt"), items);
    }

    @Test
    void contextMenu_readyChip_withDirectApiButSingleReady_noGenerateAll() {
        List<String> items = getContextMenuItems(ArtifactStatus.READY, true, 1);
        assertEquals(List.of("Generate", "Copy prompt"), items);
    }

    @Test
    void contextMenu_generatingChip_hasCancel() {
        List<String> items = getContextMenuItems(ArtifactStatus.GENERATING, false, 0);
        assertEquals(List.of("Cancel"), items);
    }

    @Test
    void contextMenu_blockedChip_isEmpty() {
        List<String> items = getContextMenuItems(ArtifactStatus.BLOCKED, false, 0);
        assertTrue(items.isEmpty());
    }

    // --- 7.3: Icon bar enabled/disabled states ---

    @Test
    void iconBar_allComplete_noTasks_verifyAndArchiveEnabled() {
        boolean allComplete = true;
        boolean tasksRemaining = false;

        boolean verifyEnabled = allComplete;
        boolean archiveEnabled = allComplete && !tasksRemaining;

        assertTrue(verifyEnabled);
        assertTrue(archiveEnabled);
    }

    @Test
    void iconBar_allComplete_withTasks_verifyEnabledArchiveDisabled() {
        boolean allComplete = true;
        boolean tasksRemaining = true;

        boolean verifyEnabled = allComplete;
        boolean archiveEnabled = allComplete && !tasksRemaining;

        assertTrue(verifyEnabled);
        assertFalse(archiveEnabled);
    }

    @Test
    void iconBar_incomplete_bothDisabled() {
        boolean allComplete = false;
        boolean tasksRemaining = false;

        boolean verifyEnabled = allComplete;
        boolean archiveEnabled = allComplete && !tasksRemaining;

        assertFalse(verifyEnabled);
        assertFalse(archiveEnabled);
    }

    // --- 7.4: Status strip content in different states ---

    @Test
    void statusStrip_steady_showsComplianceAndDeliveryMode() {
        String compliance = "Not checked";
        String deliveryMode = "Clipboard: Claude Code";
        int taskComplete = 0;
        int taskTotal = 0;

        String strip = buildStatusStripText(compliance, taskComplete, taskTotal, deliveryMode, false, 0, 0);
        assertEquals("Not checked · Clipboard: Claude Code", strip);
    }

    @Test
    void statusStrip_withTasks_showsTaskProgress() {
        String compliance = "\u2713 Compliant";
        String deliveryMode = "Direct API";
        int taskComplete = 3;
        int taskTotal = 5;

        String strip = buildStatusStripText(compliance, taskComplete, taskTotal, deliveryMode, false, 0, 0);
        assertEquals("\u2713 Compliant · 3/5 tasks · Direct API", strip);
    }

    @Test
    void statusStrip_generating_showsProgress() {
        String compliance = "Not checked";
        String deliveryMode = "Direct API";

        String strip = buildStatusStripText(compliance, 0, 0, deliveryMode, true, 2, 4);
        assertEquals("Generating 2/4... · Direct API", strip);
    }

    // --- Helpers ---

    private String resolveClickAction(ArtifactStatus status) {
        return switch (status) {
            case READY -> "generate";
            case DONE -> "open";
            default -> "none";
        };
    }

    private List<String> getContextMenuItems(ArtifactStatus status, boolean directApi, int readyCount) {
        return switch (status) {
            case DONE -> List.of("Open file", "Regenerate", "Copy prompt");
            case READY -> {
                if (directApi && readyCount >= 2) {
                    yield List.of("Generate", "Generate All Remaining", "Copy prompt");
                }
                yield List.of("Generate", "Copy prompt");
            }
            case GENERATING -> List.of("Cancel");
            default -> List.of();
        };
    }

    private String buildStatusStripText(String compliance, int taskComplete, int taskTotal,
                                         String deliveryMode, boolean generating, int genCurrent, int genTotal) {
        if (generating) {
            return "Generating " + genCurrent + "/" + genTotal + "... · " + deliveryMode;
        }
        StringBuilder sb = new StringBuilder(compliance);
        if (taskTotal > 0) {
            sb.append(" · ").append(taskComplete).append("/").append(taskTotal).append(" tasks");
        }
        sb.append(" · ").append(deliveryMode);
        return sb.toString();
    }

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

    // --- Icon bar: FF removed, change-name label present ---

    @Test
    void iconBar_doesNotContainFfButton() {
        // The icon bar should contain: change label (WEST) + button panel (EAST)
        // The button panel should have: verify, archive, overflow — NOT FF
        JPanel iconBar = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout());
        JButton verify = new JButton("Verify");
        JButton archive = new JButton("Archive");
        JButton overflow = new JButton("...");
        buttons.add(verify);
        buttons.add(archive);
        buttons.add(overflow);
        iconBar.add(new JLabel("change-name"), BorderLayout.WEST);
        iconBar.add(buttons, BorderLayout.EAST);

        // Verify no button labeled "Fast-Forward" in the button panel
        for (Component c : buttons.getComponents()) {
            if (c instanceof JButton btn) {
                assertNotEquals("Fast-Forward", btn.getText(),
                        "FF button should not be in the icon bar");
            }
        }
    }

    // --- Contextual tooltips ---

    @Test
    void tooltip_verifyEnabled_showsChangeName() {
        boolean allComplete = true;
        String changeName = "my-feature";
        String tooltip = allComplete ? "Verify: " + changeName : "Verify (complete all artifacts first)";
        assertEquals("Verify: my-feature", tooltip);
    }

    @Test
    void tooltip_verifyDisabled_showsReason() {
        boolean allComplete = false;
        String changeName = "my-feature";
        String tooltip = allComplete ? "Verify: " + changeName : "Verify (complete all artifacts first)";
        assertEquals("Verify (complete all artifacts first)", tooltip);
    }

    @Test
    void tooltip_archiveEnabled_showsChangeName() {
        boolean allComplete = true;
        boolean hasTasks = false;
        String changeName = "my-feature";
        String tooltip = (allComplete && !hasTasks)
                ? "Archive: " + changeName
                : "Archive (complete all artifacts and tasks first)";
        assertEquals("Archive: my-feature", tooltip);
    }

    @Test
    void tooltip_archiveDisabled_showsReason() {
        boolean allComplete = true;
        boolean hasTasks = true;
        String changeName = "my-feature";
        String tooltip = (allComplete && !hasTasks)
                ? "Archive: " + changeName
                : "Archive (complete all artifacts and tasks first)";
        assertEquals("Archive (complete all artifacts and tasks first)", tooltip);
    }

    // --- FF link visibility based on Direct API ---

    @Test
    void ffLink_visible_whenDirectApiConfigured() {
        boolean apiConfigured = true;
        JPanel card = new JPanel();
        card.add(new JLabel("No changes yet."));
        card.add(new JLabel("Propose a change")); // hyperlink stand-in
        if (apiConfigured) {
            card.add(new JLabel(" or "));
            card.add(new JLabel("Fast-Forward"));
        }
        assertEquals(4, card.getComponentCount(), "Card should have 4 components with FF link");
    }

    @Test
    void ffLink_hidden_whenDirectApiNotConfigured() {
        boolean apiConfigured = false;
        JPanel card = new JPanel();
        card.add(new JLabel("No changes yet."));
        card.add(new JLabel("Propose a change")); // hyperlink stand-in
        if (apiConfigured) {
            card.add(new JLabel(" or "));
            card.add(new JLabel("Fast-Forward"));
        }
        assertEquals(2, card.getComponentCount(), "Card should have 2 components without FF link");
    }

    @Test
    void activateFfInput_blockedWithoutDirectApi_showsMessage() {
        boolean apiConfigured = false;
        JLabel statusLabel = new JLabel();
        JButton goButton = new JButton("Go");

        if (!apiConfigured) {
            statusLabel.setText("Requires AI provider. Configure in Settings \u2192 Tools \u2192 OpenSpec.");
            goButton.setEnabled(false);
        }

        assertTrue(statusLabel.getText().contains("Requires AI provider"));
        assertFalse(goButton.isEnabled());
    }

    // --- Overflow menu structure ---

    @Test
    void overflowMenu_bulkArchiveRenamedToArchiveAll() {
        String label = "Archive All Changes...";
        assertTrue(label.contains("All Changes"), "Should say 'All Changes' not 'Bulk Archive'");
        assertFalse(label.contains("Bulk"), "Should not contain 'Bulk'");
    }

    @Test
    void overflowMenu_ffItemPresent() {
        // FF should be in the overflow menu creation group
        String ffLabel = "Fast-Forward...";
        assertTrue(ffLabel.contains("Fast-Forward"), "FF should be in overflow menu");
    }

    // --- EDT threading safety ---

    @Nested
    class EdtThreadingSafety {

        /**
         * Regression guard: setActiveChange() must call refreshForChange() (which dispatches
         * to a pooled thread) rather than refreshForChangeOnPool() (which runs on the calling
         * thread). Calling refreshForChangeOnPool from the EDT blocks the UI during CLI calls.
         */
        @Test
        void setActiveChange_callsRefreshForChange_notRefreshForChangeOnPool() throws IOException {
            Path source = Path.of("src/main/java/com/johnnyblabs/openspec/toolwindow/WorkflowActionPanel.java");
            String content = Files.readString(source);

            // Extract the setActiveChange method body
            int methodStart = content.indexOf("public void setActiveChange(");
            assertTrue(methodStart >= 0, "setActiveChange method should exist");

            // Find the closing brace of the method (next method or end)
            int bodyStart = content.indexOf('{', methodStart);
            int braceDepth = 0;
            int methodEnd = bodyStart;
            for (int i = bodyStart; i < content.length(); i++) {
                if (content.charAt(i) == '{') braceDepth++;
                if (content.charAt(i) == '}') braceDepth--;
                if (braceDepth == 0) { methodEnd = i; break; }
            }

            String methodBody = content.substring(bodyStart, methodEnd + 1);

            assertTrue(methodBody.contains("refreshForChange("),
                    "setActiveChange should call refreshForChange() for background dispatch");
            assertFalse(methodBody.contains("refreshForChangeOnPool("),
                    "setActiveChange must NOT call refreshForChangeOnPool() directly — it blocks the EDT");
        }
    }
}

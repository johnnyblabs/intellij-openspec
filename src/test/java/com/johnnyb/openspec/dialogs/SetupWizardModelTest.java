package com.johnnyb.openspec.dialogs;

import com.johnnyb.openspec.ai.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SetupWizardModelTest {

    private SetupWizardModel model;

    @BeforeEach
    void setUp() {
        model = new SetupWizardModel();
    }

    @Nested
    class Defaults {

        @Test
        void cliPathDefaultsToEmpty() {
            assertEquals("", model.getCliPath());
        }

        @Test
        void cliFoundDefaultsToFalse() {
            assertFalse(model.isCliFound());
        }

        @Test
        void cliVersionDefaultsToEmpty() {
            assertEquals("", model.getCliVersion());
        }

        @Test
        void detectedToolsDefaultsToEmptyList() {
            assertNotNull(model.getDetectedTools());
            assertTrue(model.getDetectedTools().isEmpty());
        }

        @Test
        void selectedToolDefaultsToEmpty() {
            assertEquals("", model.getSelectedTool());
        }

        @Test
        void deliveryMethodDefaultsToEmpty() {
            assertEquals("", model.getDeliveryMethod());
        }

        @Test
        void aiProviderDefaultsToNone() {
            assertEquals(AiProvider.NONE, model.getAiProvider());
        }

        @Test
        void aiModelDefaultsToEmpty() {
            assertEquals("", model.getAiModel());
        }

        @Test
        void apiKeyDefaultsToEmpty() {
            assertEquals("", model.getApiKey());
        }

        @Test
        void projectInitializedDefaultsToFalse() {
            assertFalse(model.isProjectInitialized());
        }
    }

    @Nested
    class StateTransitions {

        @Test
        void cliDetection() {
            model.setCliPath("/usr/local/bin/openspec");
            model.setCliFound(true);
            model.setCliVersion("1.2.0");

            assertEquals("/usr/local/bin/openspec", model.getCliPath());
            assertTrue(model.isCliFound());
            assertEquals("1.2.0", model.getCliVersion());
        }

        @Test
        void toolSelection() {
            model.setDetectedTools(List.of("Claude Code", "GitHub Copilot"));
            model.setSelectedTool("Claude Code");
            model.setDeliveryMethod("clipboard");

            assertEquals(2, model.getDetectedTools().size());
            assertEquals("Claude Code", model.getSelectedTool());
            assertEquals("clipboard", model.getDeliveryMethod());
        }

        @Test
        void directApiConfiguration() {
            model.setAiProvider(AiProvider.CLAUDE);
            model.setAiModel("claude-sonnet-4-20250514");
            model.setApiKey("sk-ant-test-key");

            assertEquals(AiProvider.CLAUDE, model.getAiProvider());
            assertEquals("claude-sonnet-4-20250514", model.getAiModel());
            assertEquals("sk-ant-test-key", model.getApiKey());
        }

        @Test
        void projectInitialization() {
            assertFalse(model.isProjectInitialized());
            model.setProjectInitialized(true);
            assertTrue(model.isProjectInitialized());
        }
    }
}

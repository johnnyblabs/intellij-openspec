package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The Claude-only Workspace ID field: shown only for Claude, validated before Apply writes anything, and
 * round-tripped through reset()/apply(). Headless — the panel is built for real, no CLI runs.
 */
public class OpenSpecSettingsPanelWorkspaceIdTest extends BasePlatformTestCase {

    private OpenSpecSettings.State saved;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionService detection = new CliDetectionService(getProject());
        CliDetectionTestSupport.fakeAvailable(detection, "1.14.3");
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
        OpenSpecSettings.State s = settings().getState();
        saved = new OpenSpecSettings.State();
        saved.aiProvider = s.aiProvider;
        saved.aiModel = s.aiModel;
        saved.aiWorkspaceId = s.aiWorkspaceId;
        saved.cliTimeoutSeconds = s.cliTimeoutSeconds;
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecSettings.State s = settings().getState();
            s.aiProvider = saved.aiProvider;
            s.aiModel = saved.aiModel;
            s.aiWorkspaceId = saved.aiWorkspaceId;
            s.cliTimeoutSeconds = saved.cliTimeoutSeconds;
        } finally {
            super.tearDown();
        }
    }

    private OpenSpecSettings settings() {
        return OpenSpecSettings.getInstance(getProject());
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, out);
        }
    }

    public void testWorkspaceRowIsVisibleOnlyForClaude() {
        OpenSpecSettingsPanel panel = new OpenSpecSettingsPanel(getProject());
        List<Component> all = new ArrayList<>();
        collect(panel.getPanel(), all);
        assertTrue("the panel renders the Workspace ID label with the documented text",
                all.stream().anyMatch(c -> c instanceof JLabel l
                        && (OpenSpecSettingsPanel.WORKSPACE_ID_LABEL + ":").equals(l.getText())));

        panel.setAiProvider("CLAUDE");
        assertTrue("shown for Claude", panel.isWorkspaceIdRowVisible());
        for (String other : List.of("OPENAI", "GEMINI", "NONE")) {
            panel.setAiProvider(other);
            assertFalse("hidden for " + other, panel.isWorkspaceIdRowVisible());
        }
        panel.setAiProvider("CLAUDE");
        assertTrue("shown again after switching back to Claude", panel.isWorkspaceIdRowVisible());
    }

    public void testApplyRejectsAnInvalidIdAndWritesNothing() {
        settings().setAiProvider("NONE");
        settings().setCliTimeoutSeconds(30);
        settings().setAiWorkspaceId("");
        OpenSpecConfigurable configurable = new OpenSpecConfigurable(getProject());
        configurable.createComponent();
        OpenSpecSettingsPanel panel = panelOf(configurable);
        panel.setAiProvider("CLAUDE");
        panel.setAiWorkspaceId("bogus");
        panel.setCliTimeout(99);

        try {
            configurable.apply();
            fail("an invalid Workspace ID must be rejected on Apply");
        } catch (ConfigurationException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("wrkspc_"));
        }
        assertEquals("a rejected Apply must not write other settings", 30, settings().getCliTimeoutSeconds());
        assertEquals("NONE", settings().getAiProvider());
        assertEquals("", settings().getAiWorkspaceId());
        configurable.disposeUIResources();
    }

    public void testValidIdRoundTripsThroughApplyAndReset() throws Exception {
        settings().setAiProvider("CLAUDE");
        settings().setAiWorkspaceId("");
        OpenSpecConfigurable configurable = new OpenSpecConfigurable(getProject());
        configurable.createComponent();
        OpenSpecSettingsPanel panel = panelOf(configurable);
        assertFalse("nothing edited yet", configurable.isModified());

        panel.setAiWorkspaceId("wrkspc_example");
        assertTrue("typing a Workspace ID enables Apply", configurable.isModified());
        configurable.apply();
        assertEquals("wrkspc_example", settings().getAiWorkspaceId());
        assertFalse(configurable.isModified());

        panel.setAiWorkspaceId("wrkspc_other");
        configurable.reset();
        assertEquals("reset restores the saved value", "wrkspc_example", panel.getAiWorkspaceId());
        configurable.disposeUIResources();
    }

    private static OpenSpecSettingsPanel panelOf(OpenSpecConfigurable configurable) {
        try {
            var f = OpenSpecConfigurable.class.getDeclaredField("panel");
            f.setAccessible(true);
            return (OpenSpecSettingsPanel) f.get(configurable);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public void testValidator() {
        assertNull(OpenSpecSettingsPanel.validateWorkspaceId(""));
        assertNull(OpenSpecSettingsPanel.validateWorkspaceId(null));
        assertNull(OpenSpecSettingsPanel.validateWorkspaceId("wrkspc_abc123"));
        assertNull("surrounding whitespace is trimmed", OpenSpecSettingsPanel.validateWorkspaceId("  wrkspc_abc  "));
        for (String bad : List.of("abc", "WRKSPC_abc", "wrkspc_", "wrkspc_a b", "sk-ant-x")) {
            assertNotNull("rejects " + bad, OpenSpecSettingsPanel.validateWorkspaceId(bad));
        }
    }

    public void testTestFailureMessage() {
        assertEquals("Check your API key",
                OpenSpecSettingsPanel.testFailureMessage(new AiApiException("x", 401, "Claude", null)));
        assertEquals("Check your API key",
                OpenSpecSettingsPanel.testFailureMessage(new AiApiException("x", 403, "Claude", null)));
        // Any other API error shows its own (for workspace errors, the mapped actionable) message.
        assertEquals("mapped message",
                OpenSpecSettingsPanel.testFailureMessage(new AiApiException("mapped message", 400, "Claude", null)));
        assertEquals("Connection failed", OpenSpecSettingsPanel.testFailureMessage(new RuntimeException()));
        assertSame(AiProvider.CLAUDE, AiProvider.fromString("CLAUDE"));
    }
}

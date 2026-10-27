package com.johnnyblabs.openspec.settings;

import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.ai.ProviderRequestSettings;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import java.util.List;

/**
 * The OpenAI-only Organization and Project ID fields: shown only for OpenAI, warned about but never blocked on an
 * unexpected prefix, sent by Test as typed, and round-tripped through reset()/apply(). Headless, no CLI runs.
 */
public class OpenSpecSettingsPanelOpenAiIdsTest extends BasePlatformTestCase {

    private OpenSpecSettings.State saved;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionService detection = new CliDetectionService(getProject());
        CliDetectionTestSupport.fakeAvailable(detection, "1.15.0");
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
        OpenSpecSettings.State s = settings().getState();
        saved = new OpenSpecSettings.State();
        saved.aiProvider = s.aiProvider;
        saved.aiModel = s.aiModel;
        saved.aiWorkspaceId = s.aiWorkspaceId;
        saved.aiOpenAiOrganization = s.aiOpenAiOrganization;
        saved.aiOpenAiProject = s.aiOpenAiProject;
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecSettings.State s = settings().getState();
            s.aiProvider = saved.aiProvider;
            s.aiModel = saved.aiModel;
            s.aiWorkspaceId = saved.aiWorkspaceId;
            s.aiOpenAiOrganization = saved.aiOpenAiOrganization;
            s.aiOpenAiProject = saved.aiOpenAiProject;
        } finally {
            super.tearDown();
        }
    }

    private OpenSpecSettings settings() {
        return OpenSpecSettings.getInstance(getProject());
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

    public void testRowsAreVisibleOnlyForOpenAi() {
        OpenSpecSettingsPanel panel = new OpenSpecSettingsPanel(getProject());
        panel.setAiProvider("OPENAI");
        assertTrue("shown for OpenAI", panel.isOpenAiScopeRowsVisible());
        for (String other : List.of("CLAUDE", "GEMINI", "NONE")) {
            panel.setAiProvider(other);
            assertTrue("hidden for " + other, panel.isOpenAiScopeRowsHidden());
        }
        panel.setAiProvider("OPENAI");
        assertTrue("shown again after switching back", panel.isOpenAiScopeRowsVisible());
    }

    public void testAnUnexpectedPrefixWarnsOnlyWhileShown() {
        OpenSpecSettingsPanel panel = new OpenSpecSettingsPanel(getProject());
        panel.setAiProvider("OPENAI");
        assertEquals(List.of(), panel.visibleOpenAiWarningsForTest());

        panel.setAiOpenAiOrganization("acme");
        panel.setAiOpenAiProject("proj_ok");
        assertEquals(List.of(OpenSpecSettingsPanel.validateOpenAiOrganization("acme")), panel.visibleOpenAiWarningsForTest());

        panel.setAiOpenAiProject("project-1");
        assertEquals(2, panel.visibleOpenAiWarningsForTest().size());

        panel.setAiProvider("CLAUDE");
        assertEquals("no OpenAI warning on another provider", List.of(), panel.visibleOpenAiWarningsForTest());

        panel.setAiProvider("OPENAI");
        panel.setAiOpenAiOrganization("org-abc");
        panel.setAiOpenAiProject("");
        assertEquals("cleared once the prefix is right", List.of(), panel.visibleOpenAiWarningsForTest());
    }

    public void testValidators() {
        for (String fine : java.util.Arrays.asList("", null, "org-abc", "  org-abc  ")) {
            assertNull("accepts " + fine, OpenSpecSettingsPanel.validateOpenAiOrganization(fine));
        }
        for (String fine : java.util.Arrays.asList("", null, "proj_abc", " proj_abc ")) {
            assertNull("accepts " + fine, OpenSpecSettingsPanel.validateOpenAiProject(fine));
        }
        for (String odd : List.of("abc", "ORG-abc", "org_abc")) {
            assertTrue(odd, OpenSpecSettingsPanel.validateOpenAiOrganization(odd).contains("\"org-\""));
        }
        for (String odd : List.of("abc", "proj-abc", "PROJ_abc")) {
            assertTrue(odd, OpenSpecSettingsPanel.validateOpenAiProject(odd).contains("\"proj_\""));
        }
    }

    public void testAnUnexpectedPrefixIsStillAppliedAndSavedAsEntered() throws Exception {
        settings().setAiProvider("OPENAI");
        settings().setAiOpenAiOrganization("");
        settings().setAiOpenAiProject("");
        OpenSpecConfigurable configurable = new OpenSpecConfigurable(getProject());
        configurable.createComponent();
        try {
            OpenSpecSettingsPanel panel = panelOf(configurable);
            panel.setAiOpenAiOrganization(" acme ");
            panel.setAiOpenAiProject("project-1");

            configurable.apply();

            assertEquals("acme", settings().getAiOpenAiOrganization());
            assertEquals("project-1", settings().getAiOpenAiProject());
        } finally {
            configurable.disposeUIResources();
        }
    }

    public void testEachFieldRoundTripsThroughIsModifiedApplyAndReset() throws Exception {
        settings().setAiProvider("OPENAI");
        settings().setAiOpenAiOrganization("");
        settings().setAiOpenAiProject("");
        OpenSpecConfigurable configurable = new OpenSpecConfigurable(getProject());
        configurable.createComponent();
        try {
            OpenSpecSettingsPanel panel = panelOf(configurable);
            assertFalse("nothing edited yet", configurable.isModified());

            panel.setAiOpenAiOrganization("org-abc");
            assertTrue("the organization enables Apply", configurable.isModified());
            configurable.apply();
            assertFalse(configurable.isModified());

            panel.setAiOpenAiProject("proj_xyz");
            assertTrue("the project enables Apply", configurable.isModified());
            configurable.apply();
            assertEquals("org-abc", settings().getAiOpenAiOrganization());
            assertEquals("proj_xyz", settings().getAiOpenAiProject());

            panel.setAiOpenAiOrganization("org-other");
            panel.setAiOpenAiProject("proj_other");
            configurable.reset();
            assertEquals("org-abc", panel.getAiOpenAiOrganization());
            assertEquals("proj_xyz", panel.getAiOpenAiProject());
        } finally {
            configurable.disposeUIResources();
        }
    }

    public void testTestSendsTheTypedValuesNotTheSavedOnes() {
        settings().setAiOpenAiOrganization("org-saved");
        settings().setAiOpenAiProject("proj_saved");
        OpenSpecSettingsPanel panel = new OpenSpecSettingsPanel(getProject());
        panel.setAiWorkspaceId("wrkspc_typed");
        panel.setAiOpenAiOrganization("org-typed");
        panel.setAiOpenAiProject(" proj_typed ");

        assertEquals(ProviderRequestSettings.openAi("org-typed", "proj_typed"), panel.formRequestSettings(AiProvider.OPENAI));
        assertEquals(ProviderRequestSettings.claudeWorkspace("wrkspc_typed"), panel.formRequestSettings(AiProvider.CLAUDE));
        assertEquals(ProviderRequestSettings.NONE, panel.formRequestSettings(AiProvider.GEMINI));
    }

    public void testHelpTextSaysWhenTheIdsAreNeededAndClaimsNothingAboutOtherKeys() {
        OpenSpecSettingsPanel panel = new OpenSpecSettingsPanel(getProject());
        String help = panel.openAiHelpTextForTest();
        assertTrue(help, help.contains("legacy user API keys"));
        assertTrue(help, help.contains("more than one organization"));
        assertTrue(help, help.contains("Settings → Organization → General"));
        assertTrue(help, help.contains("Settings → Project → General"));
        assertFalse(help, help.toLowerCase().matches("(?s).*(service.account|project key).*"));
    }

    public void testTestShowsTheIdHintOnA401() {
        assertEquals(OpenSpecSettingsPanel.OPENAI_SCOPE_TEST_FAILURE, OpenSpecSettingsPanel.testFailureMessage(
                new AiApiException("x", 401, "OpenAI", DirectApiService.OPENAI_SCOPE_SUGGESTION)));
        assertEquals("without IDs a 401 is unchanged", "Check your API key", OpenSpecSettingsPanel.testFailureMessage(
                new AiApiException("x", 401, "OpenAI", "Check your API key in Settings → Tools → OpenSpec")));
    }
}

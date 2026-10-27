package com.johnnyblabs.openspec.ai;

import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Provider request settings: trimmed, never null, read from the stored settings field by field. */
class ProviderRequestSettingsTest {

    @Test
    void nullBecomesBlankAndValuesAreTrimmed() {
        var settings = new ProviderRequestSettings(null, " org-abc ", "\tproj_xyz\n");
        assertEquals("", settings.claudeWorkspaceId());
        assertEquals("org-abc", settings.openAiOrganization());
        assertEquals("proj_xyz", settings.openAiProject());
    }

    @Test
    void noneIsAllBlank() {
        assertEquals(new ProviderRequestSettings("", "", ""), ProviderRequestSettings.NONE);
        assertFalse(ProviderRequestSettings.NONE.hasOpenAiScope());
    }

    @Test
    void fromMapsEachStoredField() {
        OpenSpecSettings stored = mock(OpenSpecSettings.class);
        when(stored.getAiWorkspaceId()).thenReturn("wrkspc_saved");
        when(stored.getAiOpenAiOrganization()).thenReturn("org-saved");
        when(stored.getAiOpenAiProject()).thenReturn("proj_saved");

        assertEquals(new ProviderRequestSettings("wrkspc_saved", "org-saved", "proj_saved"),
                ProviderRequestSettings.from(stored));
    }

    @Test
    void hasOpenAiScopeWhenEitherIsSet() {
        assertTrue(ProviderRequestSettings.openAi("org-abc", "").hasOpenAiScope());
        assertTrue(ProviderRequestSettings.openAi(null, "proj_xyz").hasOpenAiScope());
        assertFalse(ProviderRequestSettings.openAi("  ", null).hasOpenAiScope());
        assertFalse(ProviderRequestSettings.claudeWorkspace("wrkspc_x").hasOpenAiScope(),
                "a Claude workspace is not an OpenAI scope");
    }
}

package com.johnnyblabs.openspec.settings;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The OpenAI Organization and Project IDs persist as plain settings next to the Workspace ID and default to empty,
 * so an existing {@code openspec.xml} without them loads unchanged.
 */
class OpenSpecSettingsOpenAiIdsTest {

    @Test
    void defaultToEmpty() {
        OpenSpecSettings settings = new OpenSpecSettings();
        assertEquals("", settings.getAiOpenAiOrganization());
        assertEquals("", settings.getAiOpenAiProject());
    }

    @Test
    void roundTripThroughPersistedState() {
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setAiProvider("OPENAI");
        settings.setAiOpenAiOrganization("org-abc");
        settings.setAiOpenAiProject("proj_xyz");

        Element xml = XmlSerializer.serialize(settings.getState());
        OpenSpecSettings fresh = new OpenSpecSettings();
        fresh.loadState(XmlSerializer.deserialize(xml, OpenSpecSettings.State.class));

        assertEquals("org-abc", fresh.getAiOpenAiOrganization());
        assertEquals("proj_xyz", fresh.getAiOpenAiProject());
        assertEquals("OPENAI", fresh.getAiProvider());
    }

    @Test
    void legacyStateWithoutTheOptionsLoadsAsEmpty() {
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setAiProvider("OPENAI");
        settings.setAiOpenAiOrganization("org-abc");
        settings.setAiOpenAiProject("proj_xyz");
        Element xml = XmlSerializer.serialize(settings.getState());
        // Both options must be there to remove, or this test couldn't fail.
        assertTrue(xml.getChildren().removeIf(option -> "aiOpenAiOrganization".equals(option.getAttributeValue("name"))));
        assertTrue(xml.getChildren().removeIf(option -> "aiOpenAiProject".equals(option.getAttributeValue("name"))));

        OpenSpecSettings fresh = new OpenSpecSettings();
        fresh.loadState(XmlSerializer.deserialize(xml, OpenSpecSettings.State.class));

        assertEquals("", fresh.getAiOpenAiOrganization());
        assertEquals("", fresh.getAiOpenAiProject());
        assertEquals("OPENAI", fresh.getAiProvider());
    }

    @Test
    void settersTrimAndNullClears() {
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setAiOpenAiOrganization("  org-abc ");
        settings.setAiOpenAiProject(" proj_xyz\t");
        assertEquals("org-abc", settings.getAiOpenAiOrganization());
        assertEquals("proj_xyz", settings.getAiOpenAiProject());
        settings.setAiOpenAiOrganization(null);
        settings.setAiOpenAiProject(null);
        assertEquals("", settings.getAiOpenAiOrganization());
        assertEquals("", settings.getAiOpenAiProject());
    }
}

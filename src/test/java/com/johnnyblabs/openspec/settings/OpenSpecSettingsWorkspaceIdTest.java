package com.johnnyblabs.openspec.settings;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Claude Workspace ID persists with the other Direct API settings (provider, model) and defaults to
 * empty, so an existing {@code openspec.xml} without the option loads unchanged.
 */
class OpenSpecSettingsWorkspaceIdTest {

    @Test
    void defaultsToEmpty() {
        assertEquals("", new OpenSpecSettings().getAiWorkspaceId());
    }

    @Test
    void roundTripsThroughPersistedState() {
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setAiProvider("CLAUDE");
        settings.setAiModel("claude-sonnet-5");
        settings.setAiWorkspaceId("wrkspc_example");

        // Serialize the state the way the platform persists it, then load it into a fresh instance.
        Element xml = XmlSerializer.serialize(settings.getState());
        OpenSpecSettings.State reloaded = XmlSerializer.deserialize(xml, OpenSpecSettings.State.class);
        OpenSpecSettings fresh = new OpenSpecSettings();
        fresh.loadState(reloaded);

        assertEquals("wrkspc_example", fresh.getAiWorkspaceId());
        assertEquals("CLAUDE", fresh.getAiProvider());
        assertEquals("claude-sonnet-5", fresh.getAiModel());
    }

    @Test
    void legacyStateWithoutTheOptionLoadsAsEmpty() {
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setAiProvider("CLAUDE");
        Element xml = XmlSerializer.serialize(settings.getState());
        xml.getChildren().removeIf(option -> "aiWorkspaceId".equals(option.getAttributeValue("name")));

        OpenSpecSettings fresh = new OpenSpecSettings();
        fresh.loadState(XmlSerializer.deserialize(xml, OpenSpecSettings.State.class));

        assertEquals("", fresh.getAiWorkspaceId());
        assertEquals("CLAUDE", fresh.getAiProvider());
    }

    @Test
    void setterTrimsAndNullClears() {
        OpenSpecSettings settings = new OpenSpecSettings();
        settings.setAiWorkspaceId("  wrkspc_example  ");
        assertEquals("wrkspc_example", settings.getAiWorkspaceId());
        settings.setAiWorkspaceId(null);
        assertEquals("", settings.getAiWorkspaceId());
    }
}

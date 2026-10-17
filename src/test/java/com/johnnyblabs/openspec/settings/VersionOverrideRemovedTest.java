package com.johnnyblabs.openspec.settings;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.version.VersionSupport;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The Version override control is gone: it only ever offered the one config format the plugin models, so it did
 * nothing. A value stored by an earlier version no longer applies; the effective version comes from config.yaml.
 */
public class VersionOverrideRemovedTest extends BasePlatformTestCase {

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, out);
        }
    }

    public void testSettingsShowNoVersionOverrideControl() {
        OpenSpecSettingsPanel panel = new OpenSpecSettingsPanel(getProject());
        {
            List<Component> all = new ArrayList<>();
            collect(panel.getPanel(), all);
            assertTrue("sanity: the panel rendered its CLI section",
                    all.stream().anyMatch(c -> c instanceof JLabel l && "CLI path:".equals(l.getText())));
            assertFalse("no Version override label",
                    all.stream().anyMatch(c -> c instanceof JLabel l && l.getText() != null && l.getText().contains("Version override")));
            assertFalse("no combo offering the 1.2.0 config format",
                    all.stream().anyMatch(c -> c instanceof JComboBox<?> box && box.isEditable() && containsItem(box, "1.2.0")));
            assertTrue("sanity: the scan saw the panel's labels", all.stream().filter(c -> c instanceof JLabel).count() > 5);
        }
    }

    private static boolean containsItem(JComboBox<?> box, String item) {
        for (int i = 0; i < box.getItemCount(); i++) if (item.equals(String.valueOf(box.getItemAt(i)))) return true;
        return false;
    }

    private OpenSpecSettings settings() {
        return OpenSpecSettings.getInstance(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            settings().getState().versionOverride = "";
        } finally {
            super.tearDown();
        }
    }

    private String effective(String configYaml) {
        if (configYaml != null) myFixture.addFileToProject("openspec/config.yaml", configYaml);
        com.johnnyblabs.openspec.services.ConfigService config = getProject().getService(com.johnnyblabs.openspec.services.ConfigService.class);
        config.reload();
        return settings().getEffectiveVersion(getProject());
    }

    public void testWithNoConfigVersionTheDefaultFormatApplies() {
        String v = effective("schema: spec-driven\n");
        assertNull(v);
        assertSame(VersionSupport.V1_2, VersionSupport.fromString(v));
    }

    public void testALegacyConfigVersionIsStillRead() {
        assertEquals("1.1.0", effective("schema: spec-driven\nversion: 1.1.0\n"));
    }

    public void testAStoredOverrideFromAnEarlierVersionIsIgnored() {
        settings().getState().versionOverride = "9.9.0";
        assertEquals("the config's version, not the invisible stored override", "1.1.0",
                effective("schema: spec-driven\nversion: 1.1.0\n"));
    }

    public void testAStoredOverrideIsIgnoredWithoutAConfigToo() {
        settings().getState().versionOverride = "9.9.0";
        assertNull(effective(null));
    }
}

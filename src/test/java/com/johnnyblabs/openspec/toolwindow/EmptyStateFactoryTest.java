package com.johnnyblabs.openspec.toolwindow;

import com.intellij.ui.scale.JBUIScale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class EmptyStateFactoryTest {

    @BeforeAll
    static void precomputeUiScale() {
        // The factory uses JBUI.insets/scale, whose lazy system-scale init on the
        // Windows CI runner logs "Must be precomputed" (an error the test logger
        // converts into failures) when nothing precomputed the scale — the IDE does
        // this at startup; plain JUnit tests must pre-seed it themselves.
        JBUIScale.setSystemScaleFactor(1f);
        JBUIScale.setUserScaleFactor(1f);
    }

    @Test
    void createsPanelWithAllComponents() {
        Icon icon = UIManager.getIcon("OptionPane.informationIcon");
        JButton button = new JButton("Action");

        JPanel panel = EmptyStateFactory.createPanel(icon, "Title", "Description", button);

        assertNotNull(panel);
        // icon + title + description + button = 4 components
        assertEquals(4, panel.getComponentCount());
    }

    @Test
    void createsPanelWithoutIcon() {
        JButton button = new JButton("Action");

        JPanel panel = EmptyStateFactory.createPanel(null, "Title", "Description", button);

        assertNotNull(panel);
        // title + description + button = 3 components
        assertEquals(3, panel.getComponentCount());
    }

    @Test
    void createsPanelWithoutButton() {
        Icon icon = UIManager.getIcon("OptionPane.informationIcon");

        JPanel panel = EmptyStateFactory.createPanel(icon, "Title", "Description", null);

        assertNotNull(panel);
        // icon + title + description = 3 components
        assertEquals(3, panel.getComponentCount());
    }

    @Test
    void createsPanelWithTitleAndDescriptionOnly() {
        JPanel panel = EmptyStateFactory.createPanel("Title", "Description");

        assertNotNull(panel);
        // title + description = 2 components
        assertEquals(2, panel.getComponentCount());
    }

    @Test
    void usesGridBagLayout() {
        JPanel panel = EmptyStateFactory.createPanel("Title", "Description");
        assertInstanceOf(GridBagLayout.class, panel.getLayout());
    }
}

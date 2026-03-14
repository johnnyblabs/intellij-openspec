package com.johnnyblabs.openspec.toolwindow;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class EmptyStateFactoryTest {

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

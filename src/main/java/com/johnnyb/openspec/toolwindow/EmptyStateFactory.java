package com.johnnyb.openspec.toolwindow;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class EmptyStateFactory {

    private EmptyStateFactory() {
    }

    public static JPanel createPanel(@Nullable Icon icon, String title, String description, @Nullable JButton actionButton) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = JBUI.insets(4);

        if (icon != null) {
            gbc.gridy = 0;
            panel.add(new JBLabel(icon), gbc);
        }

        gbc.gridy = icon != null ? 1 : 0;
        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        panel.add(titleLabel, gbc);

        gbc.gridy++;
        JBLabel descLabel = new JBLabel("<html><body style='width:" + JBUI.scale(280) + "px'>" + description + "</body></html>");
        descLabel.setForeground(JBColor.GRAY);
        panel.add(descLabel, gbc);

        if (actionButton != null) {
            gbc.gridy++;
            gbc.insets = JBUI.insets(8, 4, 4, 4);
            panel.add(actionButton, gbc);
        }

        return panel;
    }

    public static JPanel createPanel(String title, String description) {
        return createPanel(null, title, description, null);
    }
}

package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.services.InstallToolTicks;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Install into this project": which tools get the project's own skills. Ticked as the CLI's own picker ticks it; at
 * least one tool must stay ticked, as upstream requires. Opened from Manage AI Tools, with that dialog as its parent.
 */
public final class InstallToolListDialog extends DialogWrapper {

    static final String TITLE = "Install Skills into This Project";
    static final String EXPLANATION = "<html>The skills are installed only on this machine. Pick the tools that get "
            + "OpenSpec skills in this project, so they're committed with it.</html>";

    private final Map<AiTool, JBCheckBox> boxes = new LinkedHashMap<>();

    public InstallToolListDialog(Component parent, List<InstallToolTicks.Row> rows) {
        super(parent, false);
        setTitle(TITLE);
        setOKButtonText("Install");
        for (InstallToolTicks.Row row : rows) {
            JBCheckBox box = new JBCheckBox(row.tool().name(), row.ticked());
            box.addItemListener(e -> updateOk());
            boxes.put(row.tool(), box);
        }
        init();
        updateOk();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        boxes.values().forEach(list::add);
        JBScrollPane scroll = new JBScrollPane(list);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JBLabel explanation = new JBLabel(EXPLANATION);
        explanation.setForeground(JBColor.GRAY);
        explanation.setBorder(JBUI.Borders.emptyBottom(8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(explanation, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(JBUI.scale(420), JBUI.scale(360)));
        return panel;
    }

    private void updateOk() {
        setOKActionEnabled(!ticked().isEmpty());
    }

    /** The ticked tools, in list order. */
    public List<AiTool> ticked() {
        List<AiTool> out = new ArrayList<>();
        boxes.forEach((tool, box) -> {
            if (box.isSelected()) out.add(tool);
        });
        return out;
    }

    @VisibleForTesting
    void setTickedForTest(AiTool tool, boolean ticked) {
        boxes.get(tool).setSelected(ticked);
    }

    @VisibleForTesting
    boolean okEnabledForTest() {
        return isOKActionEnabled();
    }
}

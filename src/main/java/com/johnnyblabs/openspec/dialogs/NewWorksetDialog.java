package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.coordination.WorksetEntry;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects a workset name and an add/remove list of members (each a name + a folder) for
 * {@code openspec workset create <name> --member name=path …}. {@link #doValidate()} blocks OK on a
 * blank name, no members, or any incomplete member row; the rule lives in {@link #validateWorkset}
 * so it can be unit tested without a running IDE. No CLI calls happen here — the panel runs the
 * write off the EDT after OK.
 */
public final class NewWorksetDialog extends DialogWrapper {

    private final @Nullable Project project;
    private final JBTextField nameField = new JBTextField();
    private final JPanel rowsPanel = new JPanel(new VerticalLayout(JBUI.scale(4)));
    private final List<MemberRow> rows = new ArrayList<>();

    public NewWorksetDialog(@Nullable Project project) {
        super(project, false);
        this.project = project;
        setTitle("New Workset");
        addRow();
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));

        JPanel namePanel = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        namePanel.add(new JBLabel("Workset name:"), BorderLayout.WEST);
        nameField.setColumns(24);
        namePanel.add(nameField, BorderLayout.CENTER);
        panel.add(namePanel, BorderLayout.NORTH);

        JPanel members = new JPanel(new BorderLayout());
        members.setBorder(BorderFactory.createTitledBorder("Members"));
        members.add(rowsPanel, BorderLayout.CENTER);
        JButton addButton = new JButton("Add Member", AllIcons.General.Add);
        addButton.addActionListener(e -> {
            addRow();
            revalidateRows();
        });
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, JBUI.scale(4)));
        addRow.add(addButton);
        members.add(addRow, BorderLayout.SOUTH);
        panel.add(members, BorderLayout.CENTER);

        panel.setPreferredSize(new Dimension(JBUI.scale(460), JBUI.scale(240)));
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return nameField;
    }

    private void addRow() {
        MemberRow row = new MemberRow(project);
        row.removeButton.addActionListener(e -> {
            if (rows.size() > 1) {
                rows.remove(row);
                rowsPanel.remove(row.panel);
                revalidateRows();
            }
        });
        rows.add(row);
        rowsPanel.add(row.panel);
    }

    private void revalidateRows() {
        rowsPanel.revalidate();
        rowsPanel.repaint();
        pack();
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        String error = validateWorkset(getWorksetName(), getMembers());
        return error == null ? null : new ValidationInfo(error, nameField);
    }

    /**
     * Pure validation for the New Workset dialog. Returns an error message, or null when the input
     * is acceptable. Blocks a blank name, an empty member list, or any member row missing its name
     * or folder.
     */
    @Nullable
    public static String validateWorkset(@Nullable String name, List<WorksetEntry.Member> members) {
        if (name == null || name.isBlank()) {
            return "Enter a workset name.";
        }
        if (members == null || members.isEmpty()) {
            return "Add at least one member.";
        }
        for (WorksetEntry.Member m : members) {
            if (m.name() == null || m.name().isBlank() || m.path() == null || m.path().isBlank()) {
                return "Every member needs a name and a folder.";
            }
        }
        return null;
    }

    public String getWorksetName() {
        return nameField.getText().trim();
    }

    /** The current member rows as value objects (name, path); blank fields are preserved as blank. */
    public List<WorksetEntry.Member> getMembers() {
        List<WorksetEntry.Member> result = new ArrayList<>();
        for (MemberRow row : rows) {
            result.add(new WorksetEntry.Member(row.nameField.getText().trim(),
                    row.pathField.getText().trim()));
        }
        return result;
    }

    /** One editable member row: a name field, a folder picker, and a remove button. */
    private static final class MemberRow {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JBTextField nameField = new JBTextField();
        final TextFieldWithBrowseButton pathField = new TextFieldWithBrowseButton();
        final JButton removeButton = new JButton(AllIcons.General.Remove);

        MemberRow(@Nullable Project project) {
            pathField.addBrowseFolderListener(
                    "Member Folder",
                    "Choose a member folder",
                    project,
                    FileChooserDescriptorFactory.createSingleFolderDescriptor());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = JBUI.insets(0, 0, 0, JBUI.scale(4));
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;
            nameField.setColumns(10);
            panel.add(nameField, gbc);
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            panel.add(pathField, gbc);
            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(removeButton, gbc);
        }
    }
}

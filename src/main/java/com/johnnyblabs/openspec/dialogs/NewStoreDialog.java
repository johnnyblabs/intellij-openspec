package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Collects the store id and mandatory folder path for {@code openspec store setup <id> --path <p>}.
 * The path is required because the 1.5.0 CLI rejects {@code store setup} without {@code --path}
 * (BREAKING change vs 1.4). {@link #doValidate()} blocks OK on a blank id or a blank / non-existent
 * folder; the validation rule itself lives in {@link #validateStore} so it can be unit tested
 * without a running IDE. This dialog performs no CLI calls — the panel runs the write off the EDT
 * after OK.
 */
public final class NewStoreDialog extends DialogWrapper {

    private final JBTextField idField = new JBTextField();
    private final TextFieldWithBrowseButton pathField = new TextFieldWithBrowseButton();

    public NewStoreDialog(@Nullable Project project) {
        super(project, false);
        setTitle("New Store");
        pathField.addBrowseFolderListener(
                "Store Folder",
                "Choose the folder where the store should live",
                project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor());
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JBLabel("Store id:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        idField.setColumns(24);
        panel.add(idField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JBLabel("Folder:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(pathField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JBLabel hint = new JBLabel("<html><body style='width:" + JBUI.scale(340) + "px;color:gray'>"
                + "A folder is required — the store's <code>openspec/</code> tree is created here."
                + "</body></html>");
        panel.add(hint, gbc);

        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return idField;
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        String error = validateStore(getStoreId(), getStorePath());
        if (error == null) return null;
        boolean pathProblem = getStoreId() != null && !getStoreId().isBlank();
        return new ValidationInfo(error, pathProblem ? pathField : idField);
    }

    /**
     * Pure validation for the New Store dialog. Returns an error message, or null when the input is
     * acceptable. Blocks a blank id, a blank folder, and a folder that does not exist on disk (the
     * 1.5.0 CLI requires {@code --path} to point at a real folder).
     */
    @Nullable
    public static String validateStore(@Nullable String id, @Nullable String path) {
        if (id == null || id.isBlank()) {
            return "Enter a store id.";
        }
        if (path == null || path.isBlank()) {
            return "Choose a folder for the store (a path is required).";
        }
        if (!Files.isDirectory(Path.of(path))) {
            return "The selected folder does not exist.";
        }
        return null;
    }

    public String getStoreId() {
        return idField.getText().trim();
    }

    public String getStorePath() {
        return pathField.getText().trim();
    }

    @TestOnly
    void setInputsForTest(String id, String path) {
        idField.setText(id);
        pathField.setText(path);
    }
}

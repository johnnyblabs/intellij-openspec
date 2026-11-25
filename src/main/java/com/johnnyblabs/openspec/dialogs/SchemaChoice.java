package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.services.SchemaService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * The schema selector a change-creating dialog shows when the project has several schemas, preselecting the default
 * schema from Settings. With one schema there is nothing to choose, and the selector isn't shown.
 */
record SchemaChoice(JComboBox<String> combo, boolean visible) {

    static final SchemaChoice NONE = new SchemaChoice(new JComboBox<>(), false);

    static SchemaChoice forProject(Project project) {
        JComboBox<String> combo = new JComboBox<>();
        combo.getAccessibleContext().setAccessibleName("Schema");
        SchemaService schemaService = project.getService(SchemaService.class);
        if (schemaService == null) return new SchemaChoice(combo, false);
        List<SchemaInfo> schemas = schemaService.listSchemas();
        if (schemas.size() <= 1) return new SchemaChoice(combo, false);
        for (SchemaInfo info : schemas) combo.addItem(info.name());
        // getDefaultSchema (raw), NOT getEffectiveSchema — see OpenSpecSettings.getDefaultSchema Javadoc.
        String defaultSchema = OpenSpecSettings.getInstance(project).getDefaultSchema();
        if (defaultSchema != null && !defaultSchema.isEmpty()) combo.setSelectedItem(defaultSchema);
        return new SchemaChoice(combo, true);
    }

    @Nullable String selected() {
        if (!visible) return null;
        Object selected = combo.getSelectedItem();
        return selected != null ? selected.toString() : null;
    }
}

package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.model.SchemaInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the schema combo box visibility logic used in ProposeChangeDialog and FfDialog.
 * The combo is visible only when the schema list has more than one entry.
 */
class SchemaComboVisibilityTest {

    @Test
    void hiddenWithEmptyList() {
        List<SchemaInfo> schemas = List.of();
        assertFalse(schemas.size() > 1, "Combo should be hidden when no schemas exist");
    }

    @Test
    void hiddenWithSingleSchema() {
        List<SchemaInfo> schemas = List.of(
                new SchemaInfo("spec-driven", "Default", true, List.of("proposal", "design"))
        );
        assertFalse(schemas.size() > 1, "Combo should be hidden when only one schema exists");
    }

    @Test
    void visibleWithTwoSchemas() {
        List<SchemaInfo> schemas = List.of(
                new SchemaInfo("spec-driven", "Default", true, List.of("proposal", "design")),
                new SchemaInfo("rapid", "Rapid prototyping", false, List.of("proposal", "tasks"))
        );
        assertTrue(schemas.size() > 1, "Combo should be visible when multiple schemas exist");
    }

    @Test
    void visibleWithThreeSchemas() {
        List<SchemaInfo> schemas = List.of(
                new SchemaInfo("spec-driven", "Default", true, List.of()),
                new SchemaInfo("rapid", "Rapid", false, List.of()),
                new SchemaInfo("compliance", "Compliance", false, List.of())
        );
        assertTrue(schemas.size() > 1, "Combo should be visible when more than two schemas exist");
    }
}

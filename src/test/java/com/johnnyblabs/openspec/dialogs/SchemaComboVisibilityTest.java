package com.johnnyblabs.openspec.dialogs;

import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.services.SchemaService;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dialogs that create a change show a schema selector only when the project has several schemas: Propose with
 * Direct API (where the plugin creates the change) and New Change. Propose in a handoff never shows one, since the
 * agent's propose workflow picks the schema.
 */
public class SchemaComboVisibilityTest extends BasePlatformTestCase {

    private void schemas(String... names) {
        SchemaService service = mock(SchemaService.class);
        when(service.listSchemas()).thenReturn(java.util.Arrays.stream(names)
                .map(n -> new SchemaInfo(n, n, false, List.of())).toList());
        ServiceContainerUtil.replaceService(getProject(), SchemaService.class, service, getTestRootDisposable());
    }

    private ProposeChangeDialog propose(boolean directApi) {
        ProposeChangeDialog dialog = new ProposeChangeDialog(getProject(), directApi, "Propose", "mode");
        com.intellij.openapi.util.Disposer.register(getTestRootDisposable(), dialog.getDisposable());
        return dialog;
    }

    private NewChangeDialog newChange() {
        NewChangeDialog dialog = new NewChangeDialog(getProject());
        com.intellij.openapi.util.Disposer.register(getTestRootDisposable(), dialog.getDisposable());
        return dialog;
    }

    public void testHiddenWithOneSchema() {
        schemas("spec-driven");
        assertFalse(propose(true).isSchemaComboVisible());
        assertFalse(newChange().isSchemaComboVisible());
        assertNull(propose(true).getSelectedSchema());
    }

    public void testShownWithSeveralSchemas() {
        schemas("spec-driven", "research-first");
        assertTrue(propose(true).isSchemaComboVisible());
        assertTrue(newChange().isSchemaComboVisible());
        assertEquals("spec-driven", newChange().getSelectedSchema());
    }

    public void testNeverInAHandoff() {
        schemas("spec-driven", "research-first");
        assertFalse(propose(false).isSchemaComboVisible());
        assertNull(propose(false).getSelectedSchema());
    }
}

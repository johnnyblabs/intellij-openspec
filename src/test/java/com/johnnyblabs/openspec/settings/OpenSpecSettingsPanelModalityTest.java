package com.johnnyblabs.openspec.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.SchemaInfo;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;
import com.johnnyblabs.openspec.services.SchemaService;

import javax.swing.ListModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings panel lives in the modal Settings dialog, so its background results must be posted with the dialog's
 * modality or the platform holds them until the dialog closes (the "schema list is always empty" bug). Each test runs
 * the panel inside a simulated modal state and asserts the schema list fills <em>before</em> the modal is left. The
 * schema data is captured real {@code schemas --json} output from CLI 1.14.3; no CLI runs. Refresh reloads through
 * the same {@code loadSchemaList}; every other site is held to the same rule by {@code SettingsPanelModalityHygieneTest}.
 */
public class OpenSpecSettingsPanelModalityTest extends BasePlatformTestCase {

    private static final String SCHEMAS_FIXTURE = "/fixtures/cli/1.14.3/schema-commands/schemas.json";
    private String savedDefaultSchema;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionService detection = new CliDetectionService(getProject());
        CliDetectionTestSupport.fakeAvailable(detection, "1.14.3");
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
        SchemaService schemas = new SchemaService(getProject());
        schemas.seedSchemaListForTest(fixture());
        ServiceContainerUtil.replaceService(getProject(), SchemaService.class, schemas, getTestRootDisposable());
        savedDefaultSchema = OpenSpecSettings.getInstance(getProject()).getDefaultSchema();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecSettings.getInstance(getProject()).setDefaultSchema(savedDefaultSchema);
        } finally {
            super.tearDown();
        }
    }

    private static String fixture() {
        try (InputStream is = OpenSpecSettingsPanelModalityTest.class.getResourceAsStream(SCHEMAS_FIXTURE)) {
            assertNotNull("missing fixture " + SCHEMAS_FIXTURE, is);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Schema names in the captured output, read independently of the plugin's parser. */
    private static List<String> fixtureNames() {
        List<String> names = new ArrayList<>();
        for (JsonElement el : JsonParser.parseString(fixture()).getAsJsonArray()) {
            names.add(el.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    private static List<String> names(ListModel<SchemaInfo> model) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            names.add(model.getElementAt(i).name());
        }
        return names;
    }

    /** Pumps events until the list has the fixture's size or the timeout passes; true if it filled. */
    private static boolean filledWithin(ListModel<SchemaInfo> model, int expected) throws Exception {
        long end = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            if (model.getSize() == expected) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    public void testSchemaListFillsWhileTheSettingsDialogIsOpen() throws Exception {
        List<String> expected = fixtureNames();
        assertTrue("anti-vacuity: the capture lists several schemas", expected.size() >= 2);
        String chosenDefault = expected.get(expected.size() - 1);
        OpenSpecSettings.getInstance(getProject()).setDefaultSchema(chosenDefault);

        Object dialog = new Object();
        OpenSpecSettingsPanel panel;
        boolean filledWhileOpen;
        LaterInvocator.enterModal(dialog);
        try {
            panel = new OpenSpecSettingsPanel(getProject());
            assertEquals("nothing loads before the panel is shown", 0, panel.schemaListModelForTest().getSize());
            panel.simulateFirstShownForTest();
            filledWhileOpen = filledWithin(panel.schemaListModelForTest(), expected.size());
        } finally {
            LaterInvocator.leaveModal(dialog);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        }

        assertEquals("the load must deliver at all", expected, names(panel.schemaListModelForTest()));
        assertTrue("the schema list must fill while the Settings dialog is open, not after it closes", filledWhileOpen);
        assertEquals("the saved default schema is selected", chosenDefault,
                panel.defaultSchemaComboForTest().getSelectedItem());
    }

    public void testRefreshClearsAStaleValidationResult() throws Exception {
        OpenSpecSettingsPanel panel = new OpenSpecSettingsPanel(getProject());
        panel.simulateFirstShownForTest();
        assertTrue(filledWithin(panel.schemaListModelForTest(), fixtureNames().size()));
        var result = panel.schemaValidationResultLabelForTest();
        result.setText("Schema 'spec-driven' is valid.");
        result.setVisible(true);

        panel.clickRefreshSchemasForTest();

        assertFalse("Refresh drops the selection, so the old validation result must not stay on screen",
                result.isVisible());
        assertEquals("", result.getText());
        // Refresh cleared the service cache; drain the reload it started so it can't outlive the test.
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    public void testSelectionDependentActionsAreDisabledUntilASchemaIsSelected() throws Exception {
        OpenSpecSettingsPanel panel = new OpenSpecSettingsPanel(getProject());
        panel.simulateFirstShownForTest();
        assertTrue(filledWithin(panel.schemaListModelForTest(), fixtureNames().size()));
        panel.schemaListForTest().clearSelection();

        // Fork, Validate, Open Templates, New, Refresh
        assertArrayEquals("with nothing selected, only New and Refresh are available",
                new boolean[]{false, false, false, true, true}, panel.schemaButtonsEnabledForTest());

        panel.schemaListForTest().setSelectedIndex(0);
        assertArrayEquals("selecting a schema enables the actions that act on it",
                new boolean[]{true, true, true, true, true}, panel.schemaButtonsEnabledForTest());

        panel.schemaListForTest().clearSelection();
        assertArrayEquals(new boolean[]{false, false, false, true, true}, panel.schemaButtonsEnabledForTest());
    }

    private static void assertArrayEquals(String message, boolean[] expected, boolean[] actual) {
        assertEquals(message, java.util.Arrays.toString(expected), java.util.Arrays.toString(actual));
    }

    private static void assertArrayEquals(boolean[] expected, boolean[] actual) {
        assertArrayEquals(null, expected, actual);
    }
}

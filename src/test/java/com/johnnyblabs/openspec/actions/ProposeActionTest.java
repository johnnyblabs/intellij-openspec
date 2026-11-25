package com.johnnyblabs.openspec.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.dialogs.ProposeChangeDialog;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Propose does what OpenSpec's propose workflow does. With Direct API the plugin creates the change and generates it
 * (the dialog asks for a description, an optional name and, with several schemas, a schema). With clipboard or editor
 * tab it hands the tool's own propose workflow the description alone, and creates nothing; with no propose workflow
 * installed it says so and offers Update OpenSpec….
 */
public class ProposeActionTest extends BasePlatformTestCase {

    private static final String DESCRIPTION = "Let users reset their password by email";

    private final List<Notification> raised = new ArrayList<>();
    private final List<ProposeChangeDialog> shown = new ArrayList<>();
    private final List<List<Object>> proposed = new ArrayList<>();
    private String savedDelivery;
    private String savedPreferredTool;
    private String savedProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("openspec/config.yaml", "schema: spec-driven\n");
        WorkflowProfileService profile = mock(WorkflowProfileService.class);
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profile, getTestRootDisposable());
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });
        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        savedDelivery = settings.getPreferredDeliveryMethod();
        savedPreferredTool = settings.getPreferredTool();
        savedProvider = settings.getAiProvider();
        settings.setPreferredTool("");
        settings.setPreferredDeliveryMethod("CLIPBOARD");
        Files.createDirectories(base());
        CopyPasteManager.getInstance().setContents(new StringSelection("clipboard-sentinel"));
        OpenSpecProposeAction.setDirectApiTargetForTest((project, name, description, schema, after) ->
                proposed.add(java.util.Arrays.asList(name, description, schema)));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecProposeAction.setDialogFactoryForTest(null);
            OpenSpecProposeAction.setDirectApiTargetForTest(null);
            FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
            OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
            settings.setPreferredDeliveryMethod(savedDelivery);
            settings.setPreferredTool(savedPreferredTool);
            settings.setAiProvider(savedProvider);
            deleteUnderBase(".claude");
            deleteUnderBase(".agents");
            deleteUnderBase("openspec/changes");
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    private static AnAction action() {
        return ActionManager.getInstance().getAction("OpenSpec.Propose");
    }

    /** Replaces the dialog with one that records itself and returns as if the user typed and confirmed. */
    private void dialog(String description, String name, boolean ok) {
        OpenSpecProposeAction.setDialogFactoryForTest((project, directApi, title, modeLine) ->
                new ProposeChangeDialog(project, directApi, title, modeLine) {
                    @Override
                    public boolean showAndGet() {
                        shown.add(this);
                        fillForTest(description, name);
                        return ok;
                    }
                });
    }

    private void claudeSkill() throws Exception {
        Path skill = base().resolve(".claude/skills/openspec-propose/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "---\nname: openspec-propose\n---\nPROPOSE-WORKFLOW-BODY\n");
    }

    // --- gating ---

    public void testItIsGatedOnTheProposeWorkflowAndRunsWhileIndexing() {
        assertEquals("propose", ((OpenSpecBaseAction) action()).getWorkflowId());
        assertTrue(action() instanceof DumbAware);
    }

    public void testItsDescriptionIsUpstreams() {
        assertEquals("Propose a new change: create it and generate all artifacts in one step",
                action().getTemplatePresentation().getDescription());
    }

    // --- handoff ---

    public void testAHandoffGivesTheToolsProposeWorkflowTheDescriptionAndCreatesNothing() throws Exception {
        claudeSkill();
        dialog(DESCRIPTION, "", true);
        OpenSpecProposeAction.propose(getProject());

        assertEquals("/openspec-propose " + DESCRIPTION, clipboard());
        assertTrue(lastContent(), lastContent().startsWith("Copied /openspec-propose " + DESCRIPTION + " for Claude Code"));
        assertTrue("the plugin creates no change", proposed.isEmpty());
        assertFalse(Files.exists(base().resolve("openspec/changes")));
    }

    public void testAnEmptyDescriptionHandsOffTheInvocationAlone() throws Exception {
        claudeSkill();
        dialog("", "", true);
        OpenSpecProposeAction.propose(getProject());
        assertEquals("/openspec-propose", clipboard());
    }

    public void testTheHandoffDialogAsksOnlyForTheDescriptionAndSaysWhatHappens() throws Exception {
        claudeSkill();
        dialog("", "", false);
        OpenSpecProposeAction.propose(getProject());
        ProposeChangeDialog d = shown.get(0);
        assertFalse("no name field", d.hasNameField());
        assertFalse("no schema field", d.isSchemaComboVisible());
        assertNull("an empty description is allowed", d.validateForTest());
        assertEquals("Propose a Change with Claude Code", d.getTitle());
        assertTrue(d.getModeLine(), d.getModeLine().startsWith("Copies /openspec-propose for Claude Code"));
        assertEquals("cancelled: nothing copied", "clipboard-sentinel", clipboard());
    }

    public void testAnEditorTabHandoffSaysItOpensATab() throws Exception {
        claudeSkill();
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("EDITOR_TAB");
        dialog(DESCRIPTION, "", false);
        OpenSpecProposeAction.propose(getProject());
        assertTrue(shown.get(0).getModeLine(),
                shown.get(0).getModeLine().startsWith("Opens /openspec-propose in an editor tab for Claude Code"));
    }

    public void testWithNoProposeWorkflowItWarnsOffersUpdateAndCreatesNothing() throws Exception {
        dialog(DESCRIPTION, "", true);
        OpenSpecProposeAction.propose(getProject());

        assertTrue("no dialog was shown", shown.isEmpty());
        assertEquals(OpenSpecProposeAction.NOT_INSTALLED_NOTICE, lastContent());
        assertEquals("Update OpenSpec…", raised.get(raised.size() - 1).getActions().get(0).getTemplateText());
        assertEquals("clipboard-sentinel", clipboard());
        assertTrue(proposed.isEmpty());
    }

    // --- Direct API ---

    private void directApi() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setAiProvider("CLAUDE");
        settings.setPreferredDeliveryMethod("DIRECT_API");
    }

    public void testDirectApiProposesWithTheDialogsValues() {
        directApi();
        dialog(DESCRIPTION, "reset-password", true);
        OpenSpecProposeAction.propose(getProject());
        assertEquals(List.of(java.util.Arrays.asList("reset-password", DESCRIPTION, null)), proposed);
        assertEquals("clipboard-sentinel", clipboardOrSentinel());
    }

    public void testDirectApiDerivesTheNameWhenLeftBlank() {
        directApi();
        dialog(DESCRIPTION, "", true);
        OpenSpecProposeAction.propose(getProject());
        assertEquals("let-users-reset-their-password", proposed.get(0).get(0));
    }

    public void testTheDirectApiDialogNeedsADescriptionAndOffersAName() {
        directApi();
        dialog("", "", false);
        OpenSpecProposeAction.propose(getProject());
        ProposeChangeDialog d = shown.get(0);
        assertTrue(d.hasNameField());
        assertNotNull("an empty description can't be submitted", d.validateForTest());
        assertEquals("Propose a Change with Claude", d.getTitle());
        assertTrue(d.getModeLine(), d.getModeLine().startsWith("Creates the change and generates its planning artifacts with Claude."));
        assertTrue("cancelled: nothing proposed", proposed.isEmpty());
    }

    public void testDirectApiNeedsNoInstalledWorkflow() {
        directApi();
        dialog(DESCRIPTION, "", true);
        OpenSpecProposeAction.propose(getProject());
        assertEquals(1, proposed.size());
        assertTrue("no not-installed notice", raised.stream().noneMatch(n ->
                OpenSpecProposeAction.NOT_INSTALLED_NOTICE.equals(n.getContent())));
    }

    public void testTheFirstProposalSaysWhatsNextOnceGenerationHasFinished() {
        directApi();
        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        boolean saved = settings.isFirstProposalCompleted();
        try {
            settings.setFirstProposalCompleted(false);
            List<Runnable> afterRuns = new ArrayList<>();
            OpenSpecProposeAction.setDirectApiTargetForTest((project, name, description, schema, after) -> afterRuns.add(after));
            dialog(DESCRIPTION, "", true);
            OpenSpecProposeAction.propose(getProject());
            assertTrue("nothing until generation finishes", raised.stream()
                    .noneMatch(n -> OpenSpecProposeAction.WHATS_NEXT.equals(n.getContent())));

            afterRuns.get(0).run();
            afterRuns.get(0).run();
            assertEquals("once", 1, raised.stream().filter(n -> OpenSpecProposeAction.WHATS_NEXT.equals(n.getContent())).count());
            assertTrue(settings.isFirstProposalCompleted());
        } finally {
            settings.setFirstProposalCompleted(saved);
        }
    }

    // --- helpers ---

    private @Nullable String lastContent() {
        assertFalse("a notification was raised", raised.isEmpty());
        return raised.get(raised.size() - 1).getContent();
    }

    private static String clipboard() throws Exception {
        return (String) CopyPasteManager.getInstance().getContents().getTransferData(DataFlavor.stringFlavor);
    }

    private static String clipboardOrSentinel() {
        try {
            return clipboard();
        } catch (Exception e) {
            return null;
        }
    }

    private Path base() {
        return Path.of(getProject().getBasePath());
    }

    private void deleteUnderBase(String relative) throws Exception {
        Path root = base().resolve(relative);
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}

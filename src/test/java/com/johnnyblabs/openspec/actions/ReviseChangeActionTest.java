package com.johnnyblabs.openspec.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.dialogs.ReviseChangeDialog;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.NotNull;

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
 * Revise Change... hands a change to the tool's own update workflow: what the user would type, the change and the
 * note, never the workflow's text. It's gated on {@code update}, sits after Continue in the OpenSpec menu, and asks
 * for Update OpenSpec… when no tool has the workflow installed.
 */
public class ReviseChangeActionTest extends BasePlatformTestCase {

    private static final String CHANGE = "add-auth";
    private static final String NOTE = "drop the export feature";

    private final List<Notification> raised = new ArrayList<>();
    private WorkflowProfileService profile;
    private String savedDelivery;
    private String savedPreferredTool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("openspec/config.yaml", "schema: spec-driven\n"); // an OpenSpec project
        profile = mock(WorkflowProfileService.class);
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profile, getTestRootDisposable());
        raised.clear();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });
        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        savedDelivery = settings.getPreferredDeliveryMethod();
        savedPreferredTool = settings.getPreferredTool();
        settings.setPreferredTool("");
        settings.setPreferredDeliveryMethod("CLIPBOARD");
        Files.createDirectories(base());
        CopyPasteManager.getInstance().setContents(new StringSelection("clipboard-sentinel"));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            ReviseChangeAction.setDialogFactoryForTest(null);
            ReviseChangeAction.setChooserForTest(null);
            FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
            OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
            settings.setPreferredDeliveryMethod(savedDelivery);
            settings.setPreferredTool(savedPreferredTool);
            deleteUnderBase(".claude");
            deleteUnderBase(".agents");
            deleteUnderBase(".codex");
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    // --- registration and gating ---

    private static AnAction action() {
        return ActionManager.getInstance().getAction("OpenSpec.ReviseChange");
    }

    private AnActionEvent event(String changeName) {
        SimpleDataContext.Builder data = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject());
        if (changeName != null) data.add(OpenSpecDataKeys.CHANGE_NAME, changeName);
        return TestActionEvent.createTestEvent(action(), data.build());
    }

    public void testShownAndEnabledForAChange() {
        AnActionEvent e = event(CHANGE);
        action().update(e);

        assertTrue(e.getPresentation().isVisible());
        assertTrue(e.getPresentation().isEnabled());
    }

    public void testDisabledWhenTheProfileExcludesUpdate() {
        when(profile.isWorkflowEnabled("update")).thenReturn(false);
        AnActionEvent e = event(CHANGE);
        action().update(e);

        assertFalse(e.getPresentation().isEnabled());
    }

    public void testItIsGatedOnTheUpdateWorkflowAndRunsWhileIndexing() {
        assertEquals("update", ((OpenSpecBaseAction) action()).getWorkflowId());
        assertTrue(action() instanceof DumbAware);
    }

    public void testItIsNamedReviseAndNotUpdateAndSaysWhatItDoes() {
        String text = action().getTemplatePresentation().getText();
        String revise = action().getTemplatePresentation().getDescription();
        String update = ActionManager.getInstance().getAction("OpenSpec.Update").getTemplatePresentation().getDescription();

        assertEquals("Revise Change...", text);
        assertTrue(revise, revise.contains("proposal, specs, design and tasks"));
        assertTrue(update, update.contains("Refresh generated skills and commands"));
        assertFalse("the two descriptions must tell the actions apart", revise.equals(update));
    }

    public void testItComesAfterContinueAndBeforeApplyInTheMenu() {
        List<String> menu = new ArrayList<>();
        for (AnAction child : ((DefaultActionGroup) ActionManager.getInstance().getAction("OpenSpec.MainMenu"))
                .getChildActionsOrStubs()) {
            String id = ActionManager.getInstance().getId(child);
            if (id != null) menu.add(id);
        }
        int revise = menu.indexOf("OpenSpec.ReviseChange");

        assertTrue(menu.toString(), revise > 0);
        assertEquals(menu.toString(), "OpenSpec.Continue", menu.get(revise - 1));
        assertEquals(menu.toString(), "OpenSpec.Apply", menu.get(revise + 1));
    }

    public void testTheDialogCollapsesANoteToOneLine() {
        ReviseChangeDialog dialog = new ReviseChangeDialog(getProject(), CHANGE);
        try {
            dialog.setNoteForTest("  drop the export\n\tfeature  \n\n and its tests ");
            assertEquals("drop the export feature and its tests", dialog.getNote());
            dialog.setNoteForTest(" \n ");
            assertEquals("", dialog.getNote());
        } finally {
            dialog.close(0);
        }
    }

    // --- which change ---

    public void testFromTheMenuWithOneActiveChangeItRevisesThatChange() throws Exception {
        claudeCommand();
        ChangeService changes = mock(ChangeService.class);
        when(changes.getActiveChanges()).thenReturn(List.of(new Change("only-change", "/tmp/only-change")));
        ServiceContainerUtil.replaceService(getProject(), ChangeService.class, changes, getTestRootDisposable());
        List<String> titles = noteDialog("");

        action().actionPerformed(event(null));

        assertEquals(List.of("Revise Change: only-change"), titles);
        assertEquals("/opsx:update only-change", clipboard());
    }

    public void testFromTheMenuWithSeveralChangesTheChosenOneIsRevised() throws Exception {
        claudeCommand();
        activeChanges("first-change", "second-change");
        List<List<String>> offered = new ArrayList<>();
        ReviseChangeAction.setChooserForTest((changes, where, onChosen) -> {
            offered.add(changes);
            onChosen.accept("second-change");
        });
        List<String> titles = noteDialog("");

        action().actionPerformed(event(null));

        assertEquals(List.of(List.of("first-change", "second-change")), offered);
        assertEquals(List.of("Revise Change: second-change"), titles);
        assertEquals("/opsx:update second-change", clipboard());
    }

    public void testFromTheMenuWithNoActiveChangesItSaysSo() throws Exception {
        claudeCommand();
        activeChanges();
        List<String> titles = noteDialog(NOTE);

        action().actionPerformed(event(null));

        assertTrue(titles.isEmpty());
        assertEquals("clipboard-sentinel", clipboard());
        assertEquals("No active changes to revise.", lastContent());
    }

    public void testThePreferredToolWinsWhenSeveralHaveTheWorkflow() throws Exception {
        claudeCommand();
        Path agents = base().resolve(".agents/skills/openspec-update-change/SKILL.md");
        Files.createDirectories(agents.getParent());
        Files.writeString(agents, "UPDATE-WORKFLOW-BODY\n");
        noteDialog("");

        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Codex");
        ReviseChangeAction.revise(getProject(), CHANGE);
        assertEquals("$openspec-update-change add-auth", clipboard());

        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Claude Code");
        ReviseChangeAction.revise(getProject(), CHANGE);
        assertEquals("/opsx:update add-auth", clipboard());
    }

    // --- delivery ---

    public void testClaudesCommandGetsTheChangeAndTheNote() throws Exception {
        claudeCommand();
        noteDialog(NOTE);

        ReviseChangeAction.revise(getProject(), CHANGE);

        assertEquals("/opsx:update add-auth drop the export feature", clipboard());
        assertTrue(lastContent(), lastContent().contains("Copied /opsx:update add-auth drop the export feature for Claude Code"));
    }

    public void testAnEmptyNoteHandsOffTheChangeAlone() throws Exception {
        claudeCommand();
        noteDialog("");

        ReviseChangeAction.revise(getProject(), CHANGE);

        assertEquals("/opsx:update add-auth", clipboard());
    }

    public void testTheWorkflowsTextIsNeverCopiedIn() throws Exception {
        claudeCommand();
        noteDialog(NOTE);

        ReviseChangeAction.revise(getProject(), CHANGE);

        assertFalse("the handoff names the workflow, it doesn't inline it", clipboard().contains("UPDATE-WORKFLOW-BODY"));
        assertEquals("one line", 1, clipboard().lines().count());
    }

    public void testCodexsSkillGetsItsDollarInvocation() throws Exception {
        Path skill = base().resolve(".codex/skills/openspec-update-change/SKILL.md");
        Path agents = base().resolve(".agents/skills/openspec-update-change/SKILL.md");
        for (Path p : List.of(skill, agents)) {
            Files.createDirectories(p.getParent());
            Files.writeString(p, "UPDATE-WORKFLOW-BODY\n");
        }
        OpenSpecSettings.getInstance(getProject()).setPreferredTool("Codex");
        noteDialog("");

        ReviseChangeAction.revise(getProject(), CHANGE);

        assertEquals("$openspec-update-change add-auth", clipboard());
    }

    public void testWithoutTheWorkflowNothingIsDeliveredAndUpdateOpenSpecIsOffered() throws Exception {
        List<String> titles = noteDialog(NOTE);

        ReviseChangeAction.revise(getProject(), CHANGE);

        assertTrue("no dialog when there's nothing to deliver to", titles.isEmpty());
        assertEquals("clipboard-sentinel", clipboard());
        assertEquals(0, FileEditorManager.getInstance(getProject()).getOpenFiles().length);
        Notification notice = raised.get(raised.size() - 1);
        assertEquals(ReviseChangeAction.NOT_INSTALLED_NOTICE, notice.getContent());
        assertEquals(List.of("Update OpenSpec…"), notice.getActions().stream()
                .map(a -> a.getTemplatePresentation().getText()).toList());
    }

    public void testDirectApiCopiesTheHandoffAndSaysWhy() throws Exception {
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("DIRECT_API");
        claudeCommand();
        noteDialog(NOTE);

        ReviseChangeAction.revise(getProject(), CHANGE);

        assertEquals("/opsx:update add-auth drop the export feature", clipboard());
        assertTrue(lastContent(), lastContent().endsWith(ReviseChangeAction.DIRECT_API_NOTE));
    }

    public void testTheEditorTabHoldsTheSameHandoff() throws Exception {
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("EDITOR_TAB");
        claudeCommand();
        noteDialog(NOTE);

        ReviseChangeAction.revise(getProject(), CHANGE);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        VirtualFile[] open = FileEditorManager.getInstance(getProject()).getOpenFiles();
        assertEquals(1, open.length);
        assertEquals(ReviseChangeAction.EDITOR_TAB_FILE_NAME, open[0].getName());
        assertEquals("/opsx:update add-auth drop the export feature",
                FileDocumentManager.getInstance().getDocument(open[0]).getText());
        assertEquals("clipboard-sentinel", clipboard());
    }

    // --- helpers ---

    private Path base() {
        return Path.of(getProject().getBasePath());
    }

    private void activeChanges(String... names) {
        ChangeService changes = mock(ChangeService.class);
        when(changes.getActiveChanges()).thenReturn(java.util.Arrays.stream(names).map(n -> new Change(n, "/tmp/" + n)).toList());
        ServiceContainerUtil.replaceService(getProject(), ChangeService.class, changes, getTestRootDisposable());
    }

    private void claudeCommand() throws Exception {
        Path command = base().resolve(".claude/commands/opsx/update.md");
        Files.createDirectories(command.getParent());
        Files.writeString(command, "---\nname: OPSX: Update\n---\nUPDATE-WORKFLOW-BODY\n");
    }

    /** Replaces the dialog with one that records its title and returns {@code note} as if typed and confirmed. */
    private List<String> noteDialog(String note) {
        List<String> titles = new ArrayList<>();
        ReviseChangeAction.setDialogFactoryForTest((project, change) -> new ReviseChangeDialog(project, change) {
            @Override
            public boolean showAndGet() {
                titles.add(getTitle());
                setNoteForTest(note);
                return true;
            }
        });
        return titles;
    }

    private String lastContent() {
        assertFalse("a notification was raised", raised.isEmpty());
        return raised.get(raised.size() - 1).getContent();
    }

    private static String clipboard() throws Exception {
        return (String) CopyPasteManager.getInstance().getContents().getTransferData(DataFlavor.stringFlavor);
    }

    private void deleteUnderBase(String relative) throws Exception {
        Path root = base().resolve(relative);
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}

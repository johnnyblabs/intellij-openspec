package com.johnnyblabs.openspec.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.dialogs.ProposeChangeDialog;
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
 * Fast-Forward does what OpenSpec's ff workflow does. With clipboard or editor tab it hands the AI tool's own ff
 * workflow the description and creates nothing, so it needs no Direct API provider; with Direct API delivery it opens
 * the workflow panel's form (ProposeRunPanelTest covers the run) and needs a configured provider.
 */
public class FastForwardActionTest extends BasePlatformTestCase {

    private static final String DESCRIPTION = "Add a dark mode toggle";

    private final List<Notification> raised = new ArrayList<>();
    private final List<ProposeChangeDialog> shown = new ArrayList<>();
    private DirectApiService api;
    private String savedDelivery;
    private String savedPreferredTool;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("openspec/config.yaml", "schema: spec-driven\n");
        WorkflowProfileService profile = mock(WorkflowProfileService.class);
        when(profile.isWorkflowEnabled(anyString())).thenReturn(true);
        ServiceContainerUtil.replaceService(getProject(), WorkflowProfileService.class, profile, getTestRootDisposable());
        api = mock(DirectApiService.class);
        ServiceContainerUtil.replaceService(getProject(), DirectApiService.class, api, getTestRootDisposable());
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
        CopyPasteManager.getInstance().setContents(new StringSelection("clipboard-sentinel"));
        OpenSpecProposeAction.setDialogFactoryForTest((project, directApi, title, modeLine) ->
                new ProposeChangeDialog(project, directApi, title, modeLine) {
                    @Override
                    public boolean showAndGet() {
                        shown.add(this);
                        fillForTest(DESCRIPTION, "");
                        return true;
                    }
                });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecProposeAction.setDialogFactoryForTest(null);
            OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
            settings.setPreferredDeliveryMethod(savedDelivery);
            settings.setPreferredTool(savedPreferredTool);
            deleteUnderBase(".claude");
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    private static AnAction action() {
        return ActionManager.getInstance().getAction("OpenSpec.Ff");
    }

    private AnActionEvent event() {
        return TestActionEvent.createTestEvent(action(),
                SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject()).build());
    }

    private void claudeFfSkill() throws Exception {
        Path skill = base().resolve(".claude/skills/openspec-ff-change/SKILL.md");
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "---\nname: openspec-ff-change\n---\nFF-WORKFLOW-BODY\n");
    }

    public void testInAHandoffItNeedsNoDirectApi() {
        when(api.isConfigured()).thenReturn(false);
        AnActionEvent e = event();
        action().update(e);
        assertTrue(e.getPresentation().isEnabled());
    }

    public void testWithDirectApiDeliveryItNeedsAProvider() {
        OpenSpecSettings.getInstance(getProject()).setPreferredDeliveryMethod("DIRECT_API");
        when(api.isConfigured()).thenReturn(false);
        AnActionEvent e = event();
        action().update(e);
        assertFalse(e.getPresentation().isEnabled());

        when(api.isConfigured()).thenReturn(true);
        AnActionEvent configured = event();
        action().update(configured);
        assertTrue(configured.getPresentation().isEnabled());
    }

    public void testAHandoffGivesTheToolsFfWorkflowTheDescriptionAndCreatesNothing() throws Exception {
        claudeFfSkill();
        action().actionPerformed(event());

        assertEquals("/openspec-ff-change " + DESCRIPTION, clipboard());
        assertEquals("Fast-Forward with Claude Code", shown.get(0).getTitle());
        assertFalse("no name field in a handoff", shown.get(0).hasNameField());
        assertFalse(Files.exists(base().resolve("openspec/changes")));
    }

    public void testWithNoFfWorkflowItWarnsAndOffersUpdate() throws Exception {
        action().actionPerformed(event());

        assertTrue("no dialog", shown.isEmpty());
        Notification last = raised.get(raised.size() - 1);
        assertEquals(OpenSpecFfAction.HANDOFF.notInstalledNotice(), last.getContent());
        assertEquals("Fast-Forward", last.getTitle());
        assertEquals("Update OpenSpec…", last.getActions().get(0).getTemplateText());
        assertEquals("clipboard-sentinel", clipboard());
    }

    private static String clipboard() throws Exception {
        return (String) CopyPasteManager.getInstance().getContents().getTransferData(DataFlavor.stringFlavor);
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

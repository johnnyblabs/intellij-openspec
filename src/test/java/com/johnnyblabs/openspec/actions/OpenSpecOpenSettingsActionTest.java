package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/** OpenSpec → Settings… is last in the menu and on the toolbar, and opens the OpenSpec settings page. */
public class OpenSpecOpenSettingsActionTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecOpenSettingsAction.setOpenerForTest(null);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    private static AnAction action() {
        return ActionManager.getInstance().getAction("OpenSpec.Settings");
    }

    private static List<String> childIds(String groupId) {
        List<String> ids = new ArrayList<>();
        for (AnAction child : ((DefaultActionGroup) ActionManager.getInstance().getAction(groupId)).getChildActionsOrStubs()) {
            String id = ActionManager.getInstance().getId(child);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    public void testItIsLastInTheMenuAndOnTheToolbar() {
        List<String> menu = childIds("OpenSpec.MainMenu");
        List<String> toolbar = childIds("OpenSpec.ToolWindowToolbar");

        assertEquals(menu.toString(), "OpenSpec.Settings", menu.get(menu.size() - 1));
        assertEquals(toolbar.toString(), "OpenSpec.Settings", toolbar.get(toolbar.size() - 1));
    }

    public void testItsIconDiffersFromManageAiTools() {
        AnAction tools = ActionManager.getInstance().getAction("OpenSpec.ManageTools");

        assertNotNull(action().getTemplatePresentation().getIcon());
        assertNotSame("two gears on one toolbar can't be told apart",
                tools.getTemplatePresentation().getIcon(), action().getTemplatePresentation().getIcon());
        assertEquals("Settings…", action().getTemplatePresentation().getText());
    }

    public void testEnabledWithAProjectAndWhileIndexing() {
        AnActionEvent e = TestActionEvent.createTestEvent(action(),
                SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, getProject()));
        action().update(e);

        assertTrue(e.getPresentation().isEnabledAndVisible());
        assertTrue(action() instanceof DumbAware);
    }

    public void testItOpensTheOpenSpecSettingsPageForTheProject() {
        List<Project> opened = new ArrayList<>();
        OpenSpecOpenSettingsAction.setOpenerForTest(opened::add);

        action().actionPerformed(TestActionEvent.createTestEvent(action(),
                SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, getProject())));

        assertEquals(List.of(getProject()), opened);
    }
}

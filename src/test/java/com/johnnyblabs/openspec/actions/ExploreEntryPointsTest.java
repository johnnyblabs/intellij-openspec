package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Explore is the step before Propose: it comes first in the OpenSpec menu and the tool window toolbar, has an icon,
 * and runs while the IDE indexes, so in-panel "Explore an idea first" links aren't skipped.
 */
public class ExploreEntryPointsTest extends BasePlatformTestCase {

    /** The registered ids of a group's children, in order. */
    private static List<String> childIds(String groupId) {
        AnAction group = ActionManager.getInstance().getAction(groupId);
        assertTrue(groupId + " is a DefaultActionGroup", group instanceof DefaultActionGroup);
        List<String> ids = new ArrayList<>();
        for (AnAction child : ((DefaultActionGroup) group).getChildActionsOrStubs()) {
            String id = ActionManager.getInstance().getId(child);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    public void testExploreComesBeforeProposeAndFastForwardInTheMenu() {
        List<String> menu = childIds("OpenSpec.MainMenu");
        int explore = menu.indexOf("OpenSpec.Explore");
        int propose = menu.indexOf("OpenSpec.Propose");
        int ff = menu.indexOf("OpenSpec.Ff");

        assertTrue(menu.toString(), explore >= 0 && propose >= 0 && ff >= 0);
        assertTrue("Explore, then Propose, then Fast-Forward: " + menu, explore < propose && propose < ff);
    }

    public void testTheToolbarShowsExploreImmediatelyBeforePropose() {
        List<String> toolbar = childIds("OpenSpec.ToolWindowToolbar");
        int propose = toolbar.indexOf("OpenSpec.Propose");

        assertTrue(toolbar.toString(), propose > 0);
        assertEquals(toolbar.toString(), "OpenSpec.Explore", toolbar.get(propose - 1));
    }

    public void testExploreHasAnIconAndSaysNothingIsCreated() {
        AnAction explore = ActionManager.getInstance().getAction("OpenSpec.Explore");

        assertNotNull("a toolbar button needs an icon", explore.getTemplatePresentation().getIcon());
        assertTrue(explore.getTemplatePresentation().getDescription(),
                explore.getTemplatePresentation().getDescription().contains("nothing is created"));
    }

    public void testExploreRunsWhileIndexingLikePropose() {
        assertTrue(ActionManager.getInstance().getAction("OpenSpec.Explore") instanceof DumbAware);
        assertTrue(ActionManager.getInstance().getAction("OpenSpec.Propose") instanceof DumbAware);
    }
}

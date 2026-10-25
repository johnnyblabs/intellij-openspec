package com.johnnyblabs.openspec.settings;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.HyperlinkLabel;
import com.johnnyblabs.openspec.actions.OpenSpecManageToolsAction;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The Direct API section says it's optional and that an agent like Claude Code needs no key, and links Manage AI
 * Tools…. Headless: the panel is built for real, no CLI runs.
 */
public class OpenSpecSettingsPanelDirectApiHelpTest extends BasePlatformTestCase {

    private OpenSpecSettingsPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionService detection = new CliDetectionService(getProject());
        CliDetectionTestSupport.fakeAvailable(detection, "1.15.0");
        ServiceContainerUtil.replaceService(getProject(), CliDetectionService.class, detection, getTestRootDisposable());
        panel = new OpenSpecSettingsPanel(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            OpenSpecManageToolsAction.setDialogOpenerForTest(null);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, out);
        }
    }

    public void testTheHelpSaysDirectApiIsOptionalAndAnAgentNeedsNoKey() {
        String help = OpenSpecSettingsPanel.DIRECT_API_HELP;

        assertTrue(help, help.contains("inside the IDE with your own API key"));
        assertTrue(help, help.contains("Optional: an agent like Claude Code needs no key."));
        List<Component> all = new ArrayList<>();
        collect(panel.getPanel(), all);
        assertTrue("the section shows the help", all.stream().anyMatch(c ->
                c instanceof JLabel label && label.getText() != null && label.getText().contains(help)));
    }

    public void testTheSectionLinksManageAiToolsAndTheLinkOpensIt() {
        HyperlinkLabel link = panel.manageAiToolsLinkForTest();
        List<Component> all = new ArrayList<>();
        collect(panel.getPanel(), all);
        List<Project> opened = new ArrayList<>();
        OpenSpecManageToolsAction.setDialogOpenerForTest(opened::add);

        assertTrue("the link is in the panel", all.contains(link));
        assertEquals(OpenSpecSettingsPanel.MANAGE_AI_TOOLS_LINK, link.getText());
        link.doClick();

        assertEquals(List.of(getProject()), opened);
    }
}

package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.HyperlinkLabel;
import com.johnnyblabs.openspec.actions.DeltaSpecDiffAction;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.intellij.testFramework.ServiceContainerUtil;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionTestSupport;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The workflow panel offers "Diff vs current spec": on the done specs chip's menu, one item per capability, and as a
 * link in the popover after Direct API generates specs. Headless tests capture launches instead of opening the diff.
 */
public class DiffVsCurrentSpecPanelTest extends OpenSpecIntegrationTestBase {

    /** The one active change ChangeService reports, with delta specs for "actions" and "billing". */
    private static final String CHANGE = "test-change";
    private static final String CHANGE_DIR = "/fixture/openspec/changes/" + CHANGE;

    /** The capabilities ChangeService reports; a test may remove one. */
    private final List<String> capabilities = new ArrayList<>(List.of("actions", "billing"));

    private WorkflowActionPanel workflowPanel;
    private final List<AnAction> launched = new ArrayList<>();
    private final List<Component> sources = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        CliDetectionTestSupport.fakeUnavailable(getProject().getService(CliDetectionService.class));
        // The panel's change and its capabilities come from ChangeService. Stub it: the light fixture's change folder
        // isn't found on Windows (its faked Unix base path gains a drive letter), so the panel would show no change.
        ChangeService changes = mock(ChangeService.class);
        when(changes.getActiveChanges()).thenReturn(List.of(new Change(CHANGE, CHANGE_DIR)));
        when(changes.getDeltaSpecNames(any())).thenAnswer(inv -> List.copyOf(capabilities));
        ServiceContainerUtil.replaceService(getProject(), ChangeService.class, changes, getTestRootDisposable());
        DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("OpenSpec"));
        OpenSpecToolWindowPanel panel = new OpenSpecToolWindowPanel(getProject(), () -> model);
        Disposer.register(getTestRootDisposable(), panel);
        long end = System.currentTimeMillis() + 10_000;
        while (panel.treeModelForTest() != model && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        workflowPanel = panel.workflowPanelForTest();
        workflowPanel.setActiveChange(CHANGE);
        JButton revise = workflowPanel.reviseIconButtonForTest();
        end = System.currentTimeMillis() + 10_000;
        while (!(revise.isEnabled() && CHANGE.equals(workflowPanel.activeChangeForTest()))
                && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(20);
        }
        assertEquals(CHANGE, workflowPanel.activeChangeForTest());
        workflowPanel.setLauncherForTest((action, source) -> {
            launched.add(action);
            sources.add(source);
        });
    }

    private static final ArtifactInfo SPECS = new ArtifactInfo("specs", "specs/**/*.md", ArtifactStatus.DONE, List.of());

    private static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (Component c : menu.getComponents()) {
            texts.add(c instanceof JMenuItem item ? item.getText() : c instanceof JSeparator ? "---" : c.toString());
        }
        return texts;
    }

    private static JMenu diffMenu(JPopupMenu menu) {
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenu m && "Diff vs current spec".equals(m.getText())) return m;
        }
        return null;
    }

    private String launchedPath() {
        return ((DeltaSpecDiffAction) launched.getFirst()).deltaSpecPathForTest();
    }

    public void testTheSpecsChipMenuEndsWithOneDiffItemPerCapability() {
        JPopupMenu menu = workflowPanel.chipContextMenu(SPECS, new JLabel("specs"));

        assertEquals(List.of("Open file", "Regenerate", "Copy prompt", "---", "Diff vs current spec"), texts(menu));
        JMenu diff = diffMenu(menu);
        Set<String> items = new java.util.HashSet<>();
        for (int i = 0; i < diff.getItemCount(); i++) {
            JMenuItem item = diff.getItem(i);
            items.add(item.getText());
            String tooltip = item.getToolTipText().toLowerCase(Locale.ROOT);
            assertTrue(tooltip, tooltip.contains("specs/" + item.getText() + "/spec.md"));
            for (String word : List.of("changed", "last", "before", "previous")) {
                assertFalse(tooltip, tooltip.contains(word));
            }
        }
        assertEquals(Set.of("actions", "billing"), items);
    }

    public void testAnotherDoneChipMenuHasNoDiff() {
        JPopupMenu menu = workflowPanel.chipContextMenu(
                new ArtifactInfo("design", "design.md", ArtifactStatus.DONE, List.of()), new JLabel("design"));

        assertEquals(List.of("Open file", "Regenerate", "Copy prompt"), texts(menu));
    }

    public void testAnItemLaunchesTheDiffForItsCapabilityFromTheChip() {
        JLabel chip = new JLabel("specs");
        JMenu diff = diffMenu(workflowPanel.chipContextMenu(SPECS, chip));
        JMenuItem billing = null;
        for (int i = 0; i < diff.getItemCount(); i++) {
            if ("billing".equals(diff.getItem(i).getText())) billing = diff.getItem(i);
        }

        billing.doClick();

        assertEquals(1, launched.size());
        assertEquals(CHANGE_DIR + "/specs/billing/spec.md", launchedPath());
        assertSame(chip, sources.getFirst());
    }

    public void testTheDirectApiPopoverLinksTheDiffForSpecsOnly() {
        JLabel anchor = new JLabel();
        WorkflowActionPanel.GuidanceLink link = workflowPanel.directApiGuidanceLink("specs/**/*.md", anchor);

        assertNotNull(link);
        assertEquals("Diff vs current spec", link.text());
        for (String other : List.of("proposal.md", "design.md", "tasks.md")) {
            assertNull(other, workflowPanel.directApiGuidanceLink(other, anchor));
        }
        assertNull(workflowPanel.directApiGuidanceLink(null, anchor));
    }

    public void testThePopoverShowsTheLinkItIsGiven() {
        JPanel with = WorkflowActionPanel.guidanceContent("✓ Generated specs", "Saved to: x", null,
                new WorkflowActionPanel.GuidanceLink("Diff vs current spec", () -> { }));
        JPanel without = WorkflowActionPanel.guidanceContent("✓ Generated specs", "Saved to: x", null, null);

        assertEquals(List.of("Diff vs current spec"), links(with));
        assertEquals(List.of(), links(without));
    }

    public void testWithSeveralCapabilitiesTheLinkAsksWhichAndOpensTheChosenOne() {
        List<List<DiffVsCurrentSpec.Entry>> offered = new ArrayList<>();
        workflowPanel.setDiffChooserForTest((entries, open) -> {
            offered.add(entries);
            open.accept(entries.stream().filter(e -> e.capability().equals("actions")).findFirst().orElseThrow());
        });
        JLabel anchor = new JLabel();

        workflowPanel.directApiGuidanceLink("specs/**/*.md", anchor).action().run();

        assertEquals(1, offered.size());
        assertEquals(Set.of("actions", "billing"),
                offered.getFirst().stream().map(DiffVsCurrentSpec.Entry::capability).collect(Collectors.toSet()));
        assertEquals(1, launched.size());
        assertTrue(launchedPath().endsWith("/specs/actions/spec.md"));
        assertSame(anchor, sources.getFirst());
    }

    public void testWithOneCapabilityTheLinkOpensItsDiffWithoutAsking() throws Exception {
        capabilities.remove("billing");
        List<Object> offered = new ArrayList<>();
        workflowPanel.setDiffChooserForTest((entries, open) -> offered.add(entries));

        workflowPanel.directApiGuidanceLink("specs/**/*.md", new JLabel()).action().run();

        assertTrue("one capability is opened directly", offered.isEmpty());
        assertEquals(1, launched.size());
        assertTrue(launchedPath().endsWith("/specs/actions/spec.md"));
    }

    private static List<String> links(JPanel content) {
        List<String> links = new ArrayList<>();
        for (Component c : content.getComponents()) {
            if (c instanceof HyperlinkLabel h) links.add(h.getText());
        }
        return links;
    }
}

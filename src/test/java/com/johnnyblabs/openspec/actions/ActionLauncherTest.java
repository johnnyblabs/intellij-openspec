package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.openapi.util.ActionCallback;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link ActionLauncher} runs an action through the platform pipeline: the action's {@code update()} gates whether it
 * runs at all, the place is passed through, and (because of that update session) a non-{@link DumbAware} action is
 * skipped while the IDE is indexing.
 *
 * <p>Not observable here: that the data context is taken from the {@code source} component. The light test fixture
 * swaps in a headless {@code DataManager} that ignores the component and always returns the test project's context
 * (verified: a key only the source panel answers arrives as {@code null}). That routing is checked by the change's
 * sandbox pass, which clicks every real call site, and by {@code verifyPlugin} for the API itself.
 */
public class ActionLauncherTest extends OpenSpecIntegrationTestBase {

    /** Counts performs and records what its event carried; {@code enabled} drives its update(). */
    private static class ProbeAction extends AnAction {
        private final boolean enabled;
        final AtomicInteger performed = new AtomicInteger();
        final AtomicReference<String> seenPlace = new AtomicReference<>();

        ProbeAction(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabledAndVisible(enabled && e.getProject() != null);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            performed.incrementAndGet();
            seenPlace.set(e.getPlace());
        }
    }

    private static final class DumbAwareProbeAction extends ProbeAction implements DumbAware {
        DumbAwareProbeAction() {
            super(true);
        }
    }

    /** Stands in for the panel the user clicked (its data context is not consulted headlessly — see class doc). */
    private static JPanel sourcePanel() {
        return new JPanel();
    }

    public void testEnabledActionRunsOnceWithThePlacePassedThrough() {
        ProbeAction action = new ProbeAction(true);

        ActionCallback callback = ActionLauncher.launch(action, sourcePanel(), "TestPlace");

        assertEquals("an enabled action runs exactly once", 1, action.performed.get());
        assertEquals("TestPlace", action.seenPlace.get());
        assertTrue("callback resolves as done", callback.isDone());
    }

    public void testDisabledActionDoesNotRun() {
        ProbeAction action = new ProbeAction(false);

        ActionCallback callback = ActionLauncher.launch(action, sourcePanel(), "Test");

        assertEquals("update() reported disabled, so the action must not run", 0, action.performed.get());
        assertTrue("callback is rejected for a disabled action", callback.isRejected());
    }

    public void testDuringIndexingANonDumbAwareActionIsSkipped() {
        // The platform contract that makes OpenSpecProposeAction / DeltaSpecDiffAction need the DumbAware marker.
        ProbeAction action = new ProbeAction(true);
        DumbModeTestUtils.runInDumbModeSynchronously(getProject(),
                () -> assertTrue(ActionLauncher.launch(action, sourcePanel(), "Test").isRejected()));
        assertEquals(0, action.performed.get());
    }

    public void testDuringIndexingADumbAwareActionRuns() {
        DumbAwareProbeAction action = new DumbAwareProbeAction();
        DumbModeTestUtils.runInDumbModeSynchronously(getProject(),
                () -> assertTrue(ActionLauncher.launch(action, sourcePanel(), "Test").isDone()));
        assertEquals(1, action.performed.get());
    }
}

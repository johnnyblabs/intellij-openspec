package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The focus-regain subscription is wired to the right bus and callback, and ends with its parent disposable.
 * Publishes {@link ApplicationActivationListener#TOPIC} on the real application message bus (delivered synchronously).
 */
public class OpenSpecToolWindowFactoryActivationWiringTest extends BasePlatformTestCase {

    private static IdeFrame frameFor(Project project) {
        IdeFrame frame = mock(IdeFrame.class);
        when(frame.getProject()).thenReturn(project);
        return frame;
    }

    private static void activate(IdeFrame frame) {
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(ApplicationActivationListener.TOPIC).applicationActivated(frame);
    }

    public void testThisProjectsActivationRefreshes_otherProjectsDoNot_andDisposalEndsTheSubscription() {
        Disposable parent = Disposer.newDisposable(getTestRootDisposable(), "activation-wiring");
        AtomicInteger refreshes = new AtomicInteger();
        OpenSpecToolWindowFactory.subscribeActivation(getProject(), parent, refreshes::incrementAndGet);

        activate(frameFor(getProject()));
        assertEquals("this project's window regaining focus refreshes once", 1, refreshes.get());

        activate(frameFor(mock(Project.class)));
        assertEquals("another project's window must not refresh this one", 1, refreshes.get());

        Disposer.dispose(parent);
        activate(frameFor(getProject()));
        assertEquals("after the tool window is disposed the subscription must be gone (no leak)", 1, refreshes.get());
    }
}

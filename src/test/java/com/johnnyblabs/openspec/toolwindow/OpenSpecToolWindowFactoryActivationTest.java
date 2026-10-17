package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Only the project whose IDE window regained focus re-detects its CLI. */
class OpenSpecToolWindowFactoryActivationTest {

    @Test
    void thisProjectsWindowRegainingFocusRefreshes() {
        Project project = mock(Project.class);
        IdeFrame frame = mock(IdeFrame.class);
        when(frame.getProject()).thenReturn(project);

        assertTrue(OpenSpecToolWindowFactory.refreshesOnActivation(frame, project));
    }

    @Test
    void anotherProjectsWindowDoesNotRefreshThisOne() {
        Project project = mock(Project.class);
        IdeFrame otherFrame = mock(IdeFrame.class);
        when(otherFrame.getProject()).thenReturn(mock(Project.class));

        assertFalse(OpenSpecToolWindowFactory.refreshesOnActivation(otherFrame, project));
    }

    @Test
    void theWelcomeFrameOrNullRefreshesNothing() {
        Project project = mock(Project.class);
        IdeFrame welcome = mock(IdeFrame.class);
        when(welcome.getProject()).thenReturn(null);

        assertFalse(OpenSpecToolWindowFactory.refreshesOnActivation(welcome, project));
        assertFalse(OpenSpecToolWindowFactory.refreshesOnActivation(null, project));
    }
}

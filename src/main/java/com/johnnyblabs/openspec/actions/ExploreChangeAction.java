package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * "Explore this change..." on an active change's right-click menu: starts Explore with the change's name as the topic,
 * which upstream's explore accepts as "explore in the context of that change". Gated on the {@code explore} workflow
 * like the Explore action, and shown only where the data context names a change.
 */
public class ExploreChangeAction extends OpenSpecBaseAction implements DumbAware {

    @Override
    protected String getWorkflowId() {
        return "explore";
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        if (e.getData(OpenSpecDataKeys.CHANGE_NAME) == null) {
            e.getPresentation().setEnabledAndVisible(false);
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        String changeName = e.getData(OpenSpecDataKeys.CHANGE_NAME);
        if (project == null || changeName == null) return;
        ExploreContextAction.exploreTopic(project, changeName);
    }
}

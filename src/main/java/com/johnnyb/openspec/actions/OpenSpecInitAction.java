package com.johnnyb.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

public class OpenSpecInitAction extends OpenSpecCliAction {

    @Override
    protected String[] getCliArgs() {
        return new String[]{"init"};
    }

    @Override
    protected String getCommandLabel() {
        return "init";
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        // Init is available even in non-OpenSpec projects
        e.getPresentation().setEnabledAndVisible(project != null);
    }
}

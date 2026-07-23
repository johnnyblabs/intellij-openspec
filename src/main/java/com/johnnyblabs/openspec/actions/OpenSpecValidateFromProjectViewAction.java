package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * "Validate OpenSpec" in the IDE's Project View context menu, scoped to the clicked
 * change or spec.
 *
 * <p>Subclasses {@link OpenSpecValidateAction} to inherit its background built-in+CLI
 * validate pipeline, but replaces {@code update()} with a selection-aware gate: the
 * item is shown only when at least one selected file is under an {@code openspec/}
 * directory. It deliberately does <b>not</b> call {@code super.update()} (whose gate is
 * only "is this an OpenSpec project", which would show the item on every right-click).</p>
 *
 * <p>{@code actionPerformed} resolves the selection to a {@link ValidateTarget} via
 * {@link ValidateTarget#resolveTarget} — a spec/change directory maps to that item; an
 * archived change, {@code config.yaml}, the root, or a multi-item selection falls back to
 * whole-project — and runs the scoped validate.</p>
 */
public class OpenSpecValidateFromProjectViewAction extends OpenSpecValidateAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        boolean show = false;
        if (project != null && files != null) {
            for (VirtualFile file : files) {
                // Cheap path check; short-circuit on the first openspec/ file.
                if (OpenSpecFileUtil.isUnderOpenSpec(file, project)) {
                    show = true;
                    break;
                }
            }
        }
        e.getPresentation().setEnabledAndVisible(show);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        ValidateTarget target = ValidateTarget.resolveTarget(files, project);
        runValidation(project, target);
    }
}

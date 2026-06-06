package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.johnnyblabs.openspec.toolwindow.OpenSpecToolWindowPanel;
import com.johnnyblabs.openspec.services.WorkflowProfileService;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Base class for all OpenSpec actions.
 *
 * <h3>CLI / Built-in Strategy</h3>
 * <p>The plugin uses a hybrid approach to ensure predictable results whether or
 * not the OpenSpec CLI ({@code @fission-ai/openspec}) is installed:</p>
 * <ul>
 *   <li><b>Write operations</b> (Init, Propose, Apply, Archive) — <b>Always built-in.</b>
 *       These create/move/update files on disk. The built-in implementation guarantees
 *       a consistent directory layout, artifact set, and metadata format. This avoids
 *       divergence when the CLI version differs from what the plugin expects.</li>
 *   <li><b>Read operations</b> (Validate, List) — <b>Built-in + CLI enhancement.</b>
 *       The built-in implementation handles the baseline. When the CLI is available,
 *       its output is merged or preferred — the CLI may provide richer information
 *       (custom rule validation, DAG status, cross-reference analysis) that the
 *       plugin's built-in logic doesn't cover.</li>
 * </ul>
 *
 * @see OpenSpecCliAction for actions that delegate to CLI for read operations
 */
public abstract class OpenSpecBaseAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * Returns the workflow ID this action maps to (e.g., "propose", "ff", "verify"),
     * or {@code null} if this is a utility action that is always enabled.
     */
    @Nullable
    protected String getWorkflowId() {
        return null;
    }

    /**
     * Suffix appended to action text when the action is disabled because its workflow
     * is not in the active profile. Mirrors JetBrains' "(Ultimate)" convention for
     * IDE-edition-gated features. Telegraphs the gating reason without requiring a
     * tooltip hover.
     */
    static final String CUSTOM_PROFILE_SUFFIX = " (custom)";

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || !OpenSpecFileUtil.isOpenSpecProject(project)) {
            e.getPresentation().setEnabledAndVisible(false);
            // Strip the suffix so it does not linger if the project changes.
            e.getPresentation().setText(stripCustomProfileSuffix(e.getPresentation().getText()));
            return;
        }

        e.getPresentation().setVisible(true);

        String workflowId = getWorkflowId();
        if (workflowId != null) {
            WorkflowProfileService profileService = project.getService(WorkflowProfileService.class);
            if (profileService != null && !profileService.isWorkflowEnabled(workflowId)) {
                e.getPresentation().setEnabled(false);
                e.getPresentation().setText(applyCustomProfileSuffix(e.getPresentation().getText(), true));
                e.getPresentation().setDescription(
                        "Requires custom profile. Change in Settings \u2192 Tools \u2192 OpenSpec.");
                return;
            }
        }

        e.getPresentation().setText(applyCustomProfileSuffix(e.getPresentation().getText(), false));
        e.getPresentation().setEnabled(true);
    }

    /**
     * Returns {@code text} with the {@code (custom)} suffix appended when
     * {@code profileGatedDisabled} is true, or stripped when false. Idempotent \u2014
     * safe to call repeatedly across {@link #update(AnActionEvent)} invocations.
     */
    static String applyCustomProfileSuffix(String text, boolean profileGatedDisabled) {
        if (text == null) return null;
        String stripped = stripCustomProfileSuffix(text);
        return profileGatedDisabled ? stripped + CUSTOM_PROFILE_SUFFIX : stripped;
    }

    static String stripCustomProfileSuffix(String text) {
        if (text == null) return null;
        return text.endsWith(CUSTOM_PROFILE_SUFFIX)
                ? text.substring(0, text.length() - CUSTOM_PROFILE_SUFFIX.length())
                : text;
    }

    /**
     * Refreshes the VFS synchronously and then rebuilds the OpenSpec tool window tree.
     * Sync refresh ensures the tree sees newly created files (e.g., after propose).
     */
    protected static void refreshToolWindow(Project project) {
        VirtualFileManager.getInstance().syncRefresh();
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
            if (toolWindow == null) return;
            for (Content content : toolWindow.getContentManager().getContents()) {
                Component component = content.getComponent();
                if (component instanceof OpenSpecToolWindowPanel panel) {
                    panel.refresh();
                    break;
                }
            }
        });
    }
}

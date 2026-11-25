package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.toolwindow.WorkflowActionPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Does what OpenSpec's {@code ff} workflow does: creates a change and the artifacts it needs before apply. With
 * Direct API it opens the workflow panel's Fast-Forward form, which creates and generates the change; with clipboard
 * or editor tab it hands the description to the AI tool's own ff workflow and creates nothing, as Propose does.
 */
public class OpenSpecFfAction extends OpenSpecBaseAction {

    @Override
    protected String getWorkflowId() { return "ff"; }

    static final OpenSpecProposeAction.Handoff HANDOFF = new OpenSpecProposeAction.Handoff(
            com.johnnyblabs.openspec.services.WorkflowInvocation.FF, com.johnnyblabs.openspec.services.WorkflowInvocation.FF_SKILL,
            "Fast-Forward", "Fast-Forward", "openspec-ff-change.md",
            "Fast-Forward hands your description to your AI tool's fast-forward workflow, which this project doesn't "
                    + "have yet. Run Update OpenSpec… to install it, or use Direct API.");

    /** The delivery mode Fast-Forward uses, resolved as Propose resolves it. */
    private static com.johnnyblabs.openspec.ai.DeliveryMode mode(Project project) {
        com.johnnyblabs.openspec.services.DeliveryMethodResolver resolver =
                project.getService(com.johnnyblabs.openspec.services.DeliveryMethodResolver.class);
        return resolver != null ? resolver.resolve().mode() : com.johnnyblabs.openspec.ai.DeliveryMode.CLIPBOARD;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        if (!e.getPresentation().isEnabled()) return;

        Project project = e.getProject();
        if (project == null) return;

        // Only the Direct API run needs a provider; in a handoff the AI tool's own ff workflow does the work.
        if (mode(project) != com.johnnyblabs.openspec.ai.DeliveryMode.DIRECT_API) return;
        DirectApiService apiService = project.getService(DirectApiService.class);
        if (apiService == null || !apiService.isConfigured()) {
            e.getPresentation().setEnabled(false);
            e.getPresentation().setDescription(
                    "Requires AI provider. Configure in Settings \u2192 Tools \u2192 OpenSpec.");
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        com.johnnyblabs.openspec.ai.DeliveryMode mode = mode(project);
        if (mode != com.johnnyblabs.openspec.ai.DeliveryMode.DIRECT_API) {
            // As OpenSpec's ff workflow: the agent creates the change and every artifact it needs before apply.
            OpenSpecProposeAction.handOff(project, mode, HANDOFF);
            return;
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenSpec");
        if (toolWindow == null) return;

        toolWindow.activate(() -> {
            // Find the WorkflowActionPanel in the Browse tab
            var content = toolWindow.getContentManager().findContent("Browse");
            if (content == null) return;

            Component component = content.getComponent();
            WorkflowActionPanel panel = findWorkflowPanel(component);
            if (panel != null) {
                panel.activateFfInput();
            }
        });
    }

    private static WorkflowActionPanel findWorkflowPanel(Component root) {
        if (root instanceof WorkflowActionPanel wap) return wap;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                WorkflowActionPanel found = findWorkflowPanel(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}

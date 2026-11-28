package com.johnnyblabs.openspec.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.dialogs.AskAgentDialog;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import com.johnnyblabs.openspec.util.CliVersion;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Function;

/**
 * "Ask the Agent": hands a free-text request to OpenSpec's router skill (CLI 2.2's {@code /openspec}), spelled for the
 * user's AI tool as upstream spells it. The plugin doesn't read the request, choose a skill or run a command for it:
 * the router decides. It exists only from CLI 2.2.0, so the action is hidden on an older CLI.
 */
public class OpenSpecAskAgentAction extends OpenSpecBaseAction implements DumbAware {

    static final String TITLE = "Ask the Agent";
    static final String EDITOR_TAB_FILE_NAME = "openspec-ask-agent.md";
    /** The router is an agent skill; a model call in the IDE can't run it. */
    static final String DIRECT_API_NOTE = "The OpenSpec router runs in your AI agent, so it was copied for you to paste.";
    static final String NOT_INSTALLED_NOTICE = "Ask the Agent needs OpenSpec's router skill for your AI tool, which this "
            + "project doesn't have. Run openspec update (Update OpenSpec…), or set a tool up in Manage AI Tools. A profile "
            + "with no workflows gets no router: choose one with openspec config profile.";

    private static @Nullable Function<Project, String> requestForTest;

    /** The request the next run asks for, in place of the dialog ({@code null}: cancelled). */
    @TestOnly
    static void setRequestForTest(@Nullable Function<Project, String> request) {
        requestForTest = request;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Project project = e.getProject();
        if (!e.getPresentation().isVisible() || project == null) return;
        CliDetectionService cli = project.getService(CliDetectionService.class);
        // From the cached detection only: never the disk or the CLI here.
        if (!shown(cli != null && cli.isAvailable() ? cli.getDetectedVersion() : null)) {
            e.getPresentation().setEnabledAndVisible(false);
        }
    }

    /** Whether the action is offered: on CLI 2.2.0+, or with no CLI detected (the lookup on click decides). */
    static boolean shown(@Nullable String cliVersion) {
        return cliVersion == null || cliVersion.isBlank() || CliVersion.atLeast(cliVersion, "2.2.0");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        String request;
        if (requestForTest != null) {
            request = requestForTest.apply(project);
        } else {
            AskAgentDialog dialog = new AskAgentDialog(project);
            request = dialog.showAndGet() ? dialog.getRequest() : null;
        }
        if (request == null || request.isBlank()) return;
        ask(project, request);
    }

    /** Hands {@code request} to the router in the active delivery mode. Call on the EDT. */
    static void ask(@NotNull Project project, @NotNull String request) {
        WorkflowInvocation.Found found = WorkflowInvocation.find(project, WorkflowInvocation.ROUTER, WorkflowInvocation.ROUTER_SKILL);
        if (found == null) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_WORKFLOW, TITLE, NOT_INSTALLED_NOTICE,
                    NotificationType.WARNING, ActionLauncher.notificationAction(project, "Update OpenSpec…", "OpenSpec.Update"));
            return;
        }
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMode mode = resolver != null ? resolver.resolve().mode() : DeliveryMode.CLIPBOARD;
        WorkflowHandoffDelivery.deliver(project, TITLE, EDITOR_TAB_FILE_NAME, DIRECT_API_NOTE, found, handoff(found, request), mode);
    }

    /** What the agent gets: the tool's router invocation and the request, as typed. */
    static String handoff(WorkflowInvocation.Found found, String request) {
        return WorkflowInvocation.handoff(found.invocation(), WorkflowInvocation.ROUTER_SKILL, request);
    }
}

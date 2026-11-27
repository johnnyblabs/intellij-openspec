package com.johnnyblabs.openspec.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Verify's hand-off to the agent's verify skill, outside Direct API. Upstream's {@code verify} is an agent workflow
 * (completeness, correctness, coherence); the plugin's report assesses correctness only with Direct API, so in the
 * other modes the report offers to hand the change to the AI tool, as Continue hands off its workflow.
 */
public final class VerifyHandoff {

    static final String TITLE = "Verify";
    static final String EDITOR_TAB_FILE_NAME = "openspec-verify-change.md";
    static final String NOT_INSTALLED_NOTICE = "Verify needs your AI tool's verify workflow, which this project "
            + "doesn't have. Run Update OpenSpec… (or add verify to your profile), then retry.";
    /** Only reached if Direct API is chosen and a hand-off is still delivered; says why it was copied. */
    static final String DIRECT_API_NOTE = "The full verify runs in your AI agent, so it was copied for you to paste.";

    /** What the Verify task resolves in the background: the delivery mode and the tool that has the verify workflow. */
    public record Target(DeliveryMode mode, WorkflowInvocation.@Nullable Found found) {
        /** The report's hand-off button label, or {@code null} for none. */
        public @Nullable String label() {
            return VerifyHandoff.label(mode, found);
        }
    }

    private VerifyHandoff() {
    }

    /** Resolves the delivery mode and the verify invocation. Reads the disk: call off the UI thread. */
    public static Target resolve(@NotNull Project project) {
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMode mode = resolver != null ? resolver.resolve().mode() : DeliveryMode.CLIPBOARD;
        WorkflowInvocation.Found found = mode == DeliveryMode.DIRECT_API ? null
                : WorkflowInvocation.find(project, WorkflowInvocation.VERIFY, WorkflowInvocation.VERIFY_SKILL);
        return new Target(mode, found);
    }

    /**
     * The hand-off button label: none with Direct API, which assesses correctness in the IDE; the tool's name when a
     * tool has the verify workflow; otherwise a generic label whose click explains what's missing.
     */
    static @Nullable String label(DeliveryMode mode, WorkflowInvocation.@Nullable Found found) {
        if (mode == DeliveryMode.DIRECT_API) return null;
        return found != null ? "Verify with " + found.tool().name() + "…" : "Verify with your AI tool…";
    }

    /** What the agent gets: the tool's verify invocation and the change name. */
    static String handoff(WorkflowInvocation.Found found, String changeName) {
        return WorkflowInvocation.handoff(found.invocation(), WorkflowInvocation.VERIFY_SKILL, changeName);
    }

    /** Hands {@code changeName} to the tool in {@code target}, or says the verify workflow is missing. Call on the EDT. */
    public static void deliver(@NotNull Project project, @NotNull String changeName, @NotNull Target target) {
        if (target.found() == null) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_WORKFLOW, TITLE, NOT_INSTALLED_NOTICE,
                    NotificationType.WARNING, ActionLauncher.notificationAction(project, "Update OpenSpec…", "OpenSpec.Update"));
            return;
        }
        WorkflowHandoffDelivery.deliver(project, TITLE, EDITOR_TAB_FILE_NAME, DIRECT_API_NOTE, target.found(),
                handoff(target.found(), changeName), target.mode());
    }
}

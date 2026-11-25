package com.johnnyblabs.openspec.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * "Continue": generates the change's next artifact. With Direct API it generates the artifact in the IDE. With the
 * clipboard or an editor tab it hands the user's AI tool its continue workflow for the change, as Revise does, because
 * upstream's continue is an agent workflow.
 */
public class OpenSpecContinueAction extends OpenSpecBaseAction {

    static final String TITLE = "Continue";
    static final String EDITOR_TAB_FILE_NAME = "openspec-continue-change.md";
    static final String NOT_INSTALLED_NOTICE = "Continue needs your AI tool's continue workflow, which this project "
            + "doesn't have. Run Update OpenSpec… (or add continue to your profile), then retry.";
    /** Only reached if Direct API is chosen and then falls back to a handoff; the copy says why it wasn't generated. */
    static final String DIRECT_API_NOTE = "Continue runs in your AI agent, so it was copied for you to paste.";

    @Override
    protected String getWorkflowId() { return "continue"; }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> activeChanges = changeService.getActiveChanges();
        if (activeChanges.isEmpty()) {
            OpenSpecNotifier.warn(project, "No active changes", noChangeHint(project));
            return;
        }

        ChangePicker.pick(activeChanges.stream().map(Change::getName).toList(), e.getDataContext(), TITLE,
                name -> continueChange(project, name));
    }

    /**
     * What to do when there is no change to continue: Propose, and New Change too when the profile has {@code new}.
     * Never Fast-Forward, which the core profile doesn't have.
     */
    static String noChangeHint(Project project) {
        com.johnnyblabs.openspec.services.WorkflowProfileService profile =
                project.getService(com.johnnyblabs.openspec.services.WorkflowProfileService.class);
        boolean hasNew = profile != null && profile.isWorkflowEnabled("new");
        return hasNew ? "Create a change first with Propose or New Change." : "Create a change first with Propose.";
    }

    /** Continues {@code changeName} in the active delivery mode. Call on the EDT. */
    static void continueChange(@NotNull Project project, @NotNull String changeName) {
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMode mode = resolver != null ? resolver.resolve().mode() : DeliveryMode.CLIPBOARD;
        if (routesToDirectApi(mode)) {
            generateNextArtifact(project, changeName);
            return;
        }

        WorkflowInvocation.Found found = WorkflowInvocation.find(project,
                WorkflowInvocation.CONTINUE, WorkflowInvocation.CONTINUE_SKILL);
        if (found == null) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_WORKFLOW, TITLE, NOT_INSTALLED_NOTICE,
                    NotificationType.WARNING, ActionLauncher.notificationAction(project, "Update OpenSpec…", "OpenSpec.Update"));
            return;
        }
        WorkflowHandoffDelivery.deliver(project, TITLE, EDITOR_TAB_FILE_NAME, DIRECT_API_NOTE, found,
                handoff(found, changeName), mode);
    }

    /** Only Direct API generates in the IDE; the clipboard and editor tab hand the work to the user's AI tool. */
    static boolean routesToDirectApi(DeliveryMode mode) {
        return mode == DeliveryMode.DIRECT_API;
    }

    /** What the agent gets: the tool's continue invocation and the change name. */
    static String handoff(WorkflowInvocation.Found found, String changeName) {
        return WorkflowInvocation.handoff(found.invocation(), WorkflowInvocation.CONTINUE_SKILL, changeName);
    }

    private static void generateNextArtifact(@NotNull Project project, @NotNull String changeName) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Continue: " + changeName, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ArtifactOrchestrationService orchestration =
                        project.getService(ArtifactOrchestrationService.class);

                ArtifactInfo nextArtifact = orchestration.getNextReadyArtifact(changeName);
                if (nextArtifact == null) {
                    // Check if all complete
                    ChangeArtifactDag dag = orchestration.getCachedArtifactStatus(changeName);
                    if (dag != null && dag.isComplete()) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                OpenSpecNotifier.info(project, "All artifacts complete",
                                        "Change '" + changeName + "' is ready for implementation. Run Apply or Archive."));
                    } else {
                        ApplicationManager.getApplication().invokeLater(() ->
                                OpenSpecNotifier.warn(project, "No ready artifacts",
                                        "No artifacts are ready for generation in '" + changeName + "'."));
                    }
                    return;
                }

                indicator.setText("Generating " + nextArtifact.id() + "...");
                DirectApiService apiService = project.getService(DirectApiService.class);
                if (apiService == null) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            OpenSpecNotifier.error(project, "API not configured",
                                    "Configure an AI provider in Settings to use Continue."));
                    return;
                }

                try {
                    ArtifactInstruction instruction = orchestration.getInstruction(changeName, nextArtifact.id());
                    String result = apiService.generate(instruction);
                    orchestration.writeArtifactResult(instruction, result);
                    orchestration.invalidateCache(changeName);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        OpenSpecNotifier.info(project, "Artifact generated",
                                "Created " + nextArtifact.id() + " for '" + changeName + "'.");
                        refreshToolWindow(project);
                    });
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            OpenSpecNotifier.error(project, "Generation failed",
                                    "Failed to generate " + nextArtifact.id() + ": " + ex.getMessage()));
                }
            }
        });
    }
}

package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OpenSpecContinueAction extends OpenSpecBaseAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> activeChanges = changeService.getActiveChanges();
        if (activeChanges.isEmpty()) {
            OpenSpecNotifier.warn(project, "No active changes",
                    "Create a change first with Propose or Fast-Forward.");
            return;
        }

        String changeName = activeChanges.getFirst().getName();

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

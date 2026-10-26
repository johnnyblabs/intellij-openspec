package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService.PlannedWrite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shows a Direct API regeneration's files against the current ones before anything is written. Called on the EDT.
 * Returns the writes to apply when the user accepts, or null when they reject or close the review.
 */
@FunctionalInterface
public interface ArtifactReviewer {

    @Nullable List<PlannedWrite> review(@NotNull Project project, @NotNull String artifactId,
                                        @NotNull String changeDir, @NotNull List<PlannedWrite> writes);

    /** The modal review dialog. */
    ArtifactReviewer DIALOG = (project, artifactId, changeDir, writes) ->
            new ArtifactReviewDialog(project, artifactId, changeDir, writes).showAndGet() ? writes : null;
}

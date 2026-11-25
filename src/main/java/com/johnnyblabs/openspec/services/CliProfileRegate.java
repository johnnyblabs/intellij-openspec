package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * Re-gates workflow actions when the detected OpenSpec CLI changes during a session (registered in {@code plugin.xml}
 * under {@code projectListeners}): the core set differs across CLI releases, and the custom-profile rules too.
 * Stateless, as a declarative listener must be.
 */
public final class CliProfileRegate implements CliDetectionService.Listener {

    private final Project project;

    public CliProfileRegate(Project project) {
        this.project = project;
    }

    @Override
    public void detectionCompleted(boolean available, @Nullable String version) {
        WorkflowProfileService profiles = project.getService(WorkflowProfileService.class);
        if (profiles != null) profiles.onCliDetected(available, version);
    }
}

package com.johnnyb.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class OpenSpecProjectService {

    private final Project project;

    public OpenSpecProjectService(Project project) {
        this.project = project;
    }

    public boolean isOpenSpecProject() {
        return OpenSpecFileUtil.isOpenSpecProject(project);
    }

    public ConfigService getConfigService() {
        return project.getService(ConfigService.class);
    }

    public SpecParsingService getSpecParsingService() {
        return project.getService(SpecParsingService.class);
    }

    public ChangeService getChangeService() {
        return project.getService(ChangeService.class);
    }

    public OpenSpecSettings getSettings() {
        return OpenSpecSettings.getInstance(project);
    }

    public CliDetectionService getCliDetectionService() {
        return project.getService(CliDetectionService.class);
    }

    public AiToolDetectionService getAiToolDetectionService() {
        return project.getService(AiToolDetectionService.class);
    }

    public ArtifactOrchestrationService getArtifactOrchestrationService() {
        return project.getService(ArtifactOrchestrationService.class);
    }

    /**
     * Startup activity that detects CLI and shows notification if missing.
     */
    public static class StartupDetection implements ProjectActivity {
        @Nullable
        @Override
        public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
            if (!OpenSpecFileUtil.isOpenSpecProject(project)) {
                return Unit.INSTANCE;
            }

            CliDetectionService detection = project.getService(CliDetectionService.class);
            if (detection != null) {
                detection.detect();
                if (!detection.isAvailable()) {
                    OpenSpecNotifier.cliMissing(project);
                }
            }

            AiToolDetectionService aiDetection = project.getService(AiToolDetectionService.class);
            if (aiDetection != null) {
                aiDetection.detect();
            }

            return Unit.INSTANCE;
        }
    }
}

package com.johnnyb.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.util.OpenSpecFileUtil;

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
}

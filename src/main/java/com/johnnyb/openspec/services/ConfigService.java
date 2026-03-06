package com.johnnyb.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyb.openspec.model.OpenSpecConfig;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

@Service(Service.Level.PROJECT)
public final class ConfigService {
    private static final Logger LOG = Logger.getInstance(ConfigService.class);

    private final Project project;
    private OpenSpecConfig config;

    public ConfigService(Project project) {
        this.project = project;
    }

    public OpenSpecConfig getConfig() {
        if (config == null) {
            reload();
        }
        return config;
    }

    public void reload() {
        VirtualFile configFile = OpenSpecFileUtil.getConfigFile(project);
        if (configFile == null || !configFile.exists()) {
            LOG.warn("OpenSpec config.yaml not found");
            config = null;
            return;
        }
        try (InputStream is = configFile.getInputStream()) {
            Yaml yaml = new Yaml();
            config = yaml.loadAs(is, OpenSpecConfig.class);
        } catch (Exception e) {
            LOG.error("Failed to parse OpenSpec config.yaml", e);
            config = null;
        }
    }

    public boolean isConfigLoaded() {
        return config != null;
    }
}

package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class ConfigService {
    private static final Logger LOG = Logger.getInstance(ConfigService.class);

    private final Project project;
    private volatile OpenSpecConfig config;

    public ConfigService(Project project) {
        this.project = project;
    }

    public OpenSpecConfig getConfig() {
        OpenSpecConfig c = config;
        if (c == null) {
            synchronized (this) {
                c = config;
                if (c == null) {
                    reload();
                    c = config;
                }
            }
        }
        return c;
    }

    public void reload() {
        VirtualFile configFile = OpenSpecFileUtil.getConfigFile(project);

        // If VFS hasn't indexed yet, try direct filesystem access
        if (configFile == null && project.getBasePath() != null) {
            String configPath = project.getBasePath() + "/openspec/config.yaml";
            configFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(configPath);
        }

        if (configFile == null || !configFile.exists()) {
            LOG.warn("OpenSpec config.yaml not found");
            config = null;
            return;
        }
        try {
            Map<String, Object> raw;
            if (ApplicationManager.getApplication() == null
                    || ApplicationManager.getApplication().isDispatchThread()) {
                try (InputStream is = configFile.getInputStream()) {
                    raw = new Yaml().load(is);
                }
            } else {
                VirtualFile cfgFile = configFile;
                raw = ReadAction.computeCancellable(() -> {
                    try (InputStream is = cfgFile.getInputStream()) {
                        return new Yaml().load(is);
                    }
                });
            }
            config = OpenSpecConfig.fromMap(raw);
        } catch (Exception e) {
            config = new OpenSpecConfig();
            LOG.debug("Failed to parse config.yaml: " + configFile.getPath(), e);
        }
    }

    public boolean isConfigLoaded() {
        return config != null;
    }
}

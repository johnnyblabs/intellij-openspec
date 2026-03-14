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
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.InputStream;

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
            OpenSpecConfig loaded;
            if (ApplicationManager.getApplication() == null
                    || ApplicationManager.getApplication().isDispatchThread()) {
                try (InputStream is = configFile.getInputStream()) {
                    Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
                    loaded = yaml.loadAs(is, OpenSpecConfig.class);
                }
            } else {
                VirtualFile cfgFile = configFile;
                loaded = ReadAction.computeCancellable(() -> {
                    try (InputStream is = cfgFile.getInputStream()) {
                        Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
                        return yaml.loadAs(is, OpenSpecConfig.class);
                    }
                });
            }
            config = loaded;
        } catch (MarkedYAMLException e) {
            config = null;
            String location = "";
            if (e.getProblemMark() != null) {
                location = "line " + (e.getProblemMark().getLine() + 1)
                        + ", column " + (e.getProblemMark().getColumn() + 1) + ": ";
            }
            String message = configFile.getPath() + ": " + location
                    + (e.getProblem() != null ? e.getProblem() : "invalid YAML");
            LOG.warn("Failed to parse config.yaml: " + message, e);
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "Configuration",
                    "config.yaml parse error: " + message, com.intellij.notification.NotificationType.WARNING);
        } catch (Exception e) {
            config = null;
            LOG.warn("Failed to read config.yaml: " + configFile.getPath(), e);
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "Configuration",
                    "Failed to read config.yaml: " + e.getMessage(), com.intellij.notification.NotificationType.WARNING);
        }
    }

    public boolean isConfigLoaded() {
        return config != null;
    }
}

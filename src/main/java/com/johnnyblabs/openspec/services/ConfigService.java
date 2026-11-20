package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class ConfigService {
    private static final Logger LOG = Logger.getInstance(ConfigService.class);

    private final Project project;
    /** The last load, published in one write so its config and problem always belong together; null when absent. */
    private volatile LoadResult state;

    /**
     * Why {@code config.yaml} could not be loaded. The plugin still runs on defaults, but CLI 1.16+ fails
     * {@code validate} on both kinds, so the built-in check reports them rather than hiding them.
     */
    public enum ProblemKind { UNREADABLE, UNPARSEABLE }

    /** A load failure: its kind and the first line of the underlying message. */
    public record LoadProblem(@NotNull ProblemKind kind, @NotNull String detail) {
    }

    /** The outcome of one load: the config to use (defaults on failure) and the problem, if any. */
    public record LoadResult(@NotNull OpenSpecConfig config, @Nullable LoadProblem problem) {
    }

    /** Opens the file's content; may throw {@link IOException} when the file can't be read. */
    @FunctionalInterface
    public interface Source {
        InputStream open() throws IOException;
    }

    public ConfigService(Project project) {
        this.project = project;
    }

    public OpenSpecConfig getConfig() {
        LoadResult s = current();
        return s == null ? null : s.config();
    }

    /**
     * The last load's config and problem as one snapshot, loading first when nothing is loaded; null when
     * {@code config.yaml} is absent. Read the config and its problem from one snapshot: a concurrent reload could
     * otherwise pair one load's config with another load's problem.
     */
    public @Nullable LoadResult current() {
        LoadResult s = state;
        if (s == null) {
            synchronized (this) {
                s = state;
                if (s == null) {
                    reload();
                    s = state;
                }
            }
        }
        return s;
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
            state = null;
            return;
        }
        VirtualFile cfgFile = configFile;
        Source source = cfgFile::getInputStream;
        LoadResult result;
        if (ApplicationManager.getApplication() == null
                || ApplicationManager.getApplication().isDispatchThread()) {
            result = load(source);
        } else {
            result = ApplicationManager.getApplication().runReadAction(
                    (com.intellij.openapi.util.Computable<LoadResult>) () -> load(source));
        }
        if (result.problem() != null) {
            LOG.debug("Failed to load config.yaml: " + cfgFile.getPath() + ": " + result.problem().detail());
        }
        state = result;
    }

    /**
     * Loads a config, falling back to defaults on any failure. Only the failures upstream reports are recorded,
     * matching its {@code readConfigParseError}: the file can't be read, or the YAML parser throws. YAML that parses
     * to a non-mapping (a scalar or a list) is not a parse failure upstream, so it records nothing here either.
     */
    public static LoadResult load(Source source) {
        Map<String, Object> raw;
        try (InputStream is = source.open()) {
            raw = new Yaml().load(is);
        } catch (YAMLException e) {
            return new LoadResult(new OpenSpecConfig(), new LoadProblem(ProblemKind.UNPARSEABLE, firstLine(e)));
        } catch (IOException e) {
            return new LoadResult(new OpenSpecConfig(), new LoadProblem(ProblemKind.UNREADABLE, firstLine(e)));
        } catch (ClassCastException e) {
            // Valid YAML whose top level is not a mapping.
            LOG.debug("config.yaml is not a mapping; using defaults", e);
            return new LoadResult(new OpenSpecConfig(), null);
        } catch (RuntimeException e) {
            // Anything else keeps the old lenient behaviour: defaults, nothing reported.
            LOG.debug("Failed to read config.yaml; using defaults", e);
            return new LoadResult(new OpenSpecConfig(), null);
        }
        try {
            return new LoadResult(OpenSpecConfig.fromMap(raw), null);
        } catch (RuntimeException e) {
            LOG.debug("Failed to map config.yaml; using defaults", e);
            return new LoadResult(new OpenSpecConfig(), null);
        }
    }

    private static String firstLine(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) return e.getClass().getSimpleName();
        String line = m.strip().lines().findFirst().orElse("").strip();
        return line.isEmpty() ? e.getClass().getSimpleName() : line;
    }

    public boolean isConfigLoaded() {
        return state != null;
    }
}

package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.util.CliJson;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.CliVersion;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Assembles project context from config, active changes, specs, and detected AI tools
 * into a Markdown-formatted string. Shared by both {@code ExplorePanel} and
 * {@code ExploreContextAction}.
 *
 * <p>Change artifacts come from {@code openspec status --change <n> --json} ({@code artifactPaths}), and capabilities
 * from {@code openspec list --specs --json}, each falling back to a walk of the project files that mirrors upstream's
 * rules. Runs the CLI, so call it off the EDT.
 */
@Service(Service.Level.PROJECT)
public final class ExploreContextService {
    private static final Logger LOG = Logger.getInstance(ExploreContextService.class);
    /** Spec-driven's single-file artifacts, which the fallback walk lists first, in this order. */
    private static final List<String> CHANGE_ARTIFACTS = List.of("proposal.md", "design.md", "tasks.md");

    /**
     * The first CLI whose {@code list --specs --json} ids are always the folder of a real {@code spec.md}, nested ids
     * included. On 1.5–1.6 it lists every top-level folder. A CLI-capability gate, not the config-format axis.
     */
    public static final String LIST_SPECS_FLOOR = "1.7.0";

    /** The CLI as this service uses it; a seam so tests can feed captured output. */
    public interface Cli {
        /** The detected CLI version, or null when no CLI is available. */
        @Nullable String version();

        CliRunner.CliResult run(String... args) throws CliRunner.CliException;
    }

    private final Project project;
    private final Cli realCli;
    private Cli cli;

    public ExploreContextService(Project project) {
        this.project = project;
        this.realCli = new Cli() {
            @Override
            public @Nullable String version() {
                CliDetectionService detection = project.getService(CliDetectionService.class);
                return detection != null && detection.isAvailable() ? detection.getDetectedVersion() : null;
            }

            @Override
            public CliRunner.CliResult run(String... args) throws CliRunner.CliException {
                return CliRunner.run(project, args);
            }
        };
        this.cli = realCli;
    }

    /** Replaces the CLI; {@code null} restores the real one. */
    @TestOnly
    public void setCliForTest(@Nullable Cli cli) {
        this.cli = cli == null ? realCli : cli;
    }

    /**
     * Assembles the full project context as a Markdown-formatted string.
     *
     * @return Markdown string containing config summary, active changes with proposal
     *         summaries, spec domains, and detected AI tools.
     */
    public String assembleContext() {
        StringBuilder context = new StringBuilder();
        context.append("# OpenSpec Explore Context\n\n");

        appendConfigSummary(context);
        appendDetectedTools(context);
        appendActiveChanges(context);
        appendSpecsDomains(context);

        return context.toString();
    }

    private void appendConfigSummary(StringBuilder context) {
        ConfigService configService = project.getService(ConfigService.class);
        if (configService == null) return;

        OpenSpecConfig config = configService.getConfig();
        if (config != null) {
            context.append("## Project Config\n");
            // `version:` is intentionally NOT surfaced here: it's a plugin-internal field upstream
            // strips and never reads, so emitting it into the AI-prompt context presents an off-model
            // field as real project config. The getVersion() reader stays for the config-format-axis
            // fallback (OpenSpecSettings.getEffectiveVersion), just not surfaced.
            if (config.getSchema() != null) {
                context.append("- Schema: ").append(config.getSchema()).append("\n");
            }
            if (!config.getContext().isEmpty()) {
                context.append("\n> ").append(config.getContext().replace("\n", "\n> ")).append("\n");
            }
            if (!config.getRules().isEmpty()) {
                context.append("\n**Rules:**\n");
                config.getRules().forEach((name, rule) ->
                        context.append("- **").append(name).append("**: ").append(rule).append("\n"));
            }
            context.append("\n");
        }
    }

    private void appendDetectedTools(StringBuilder context) {
        AiToolDetectionService detection = project.getService(AiToolDetectionService.class);
        if (detection == null) return;

        detection.detect();
        List<String> tools = detection.getDetectedTools();
        context.append("## Detected AI Tools\n");
        if (tools.isEmpty()) {
            context.append("None detected.\n");
        } else {
            for (String tool : tools) {
                AiToolDetectionService.ToolType type = AiToolDetectionService.getToolType(tool);
                context.append("- ").append(tool).append(" [").append(type).append("]\n");
            }
        }
        context.append("\n");
    }

    private void appendActiveChanges(StringBuilder context) {
        ChangeService changeService = project.getService(ChangeService.class);
        if (changeService == null) return;

        List<Change> changes = changeService.getActiveChanges();
        context.append("## Active Changes\n");
        if (changes.isEmpty()) {
            context.append("No active changes.\n");
        } else {
            for (Change change : changes) {
                context.append("\n### ").append(change.getName());
                if (change.getMetadata() != null && change.getMetadata().getSchema() != null) {
                    context.append(" (").append(change.getMetadata().getSchema()).append(")");
                }
                context.append("\n");

                appendChangeArtifacts(change.getName(), context);
            }
        }
        context.append("\n");
    }

    private void appendChangeArtifacts(String changeName, StringBuilder context) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        Path changeDir = Path.of(basePath, "openspec", "changes", changeName);
        List<Artifact> artifacts = artifactsFromStatus(changeName, changeDir);
        if (artifacts == null) artifacts = artifactsFromWalk(changeDir);

        for (Artifact artifact : artifacts) {
            try {
                String content = Files.readString(artifact.file());
                context.append("\n**").append(artifact.label()).append(":**\n\n").append(content.strip()).append("\n");
            } catch (IOException ignored) {
                // Skip if unreadable
            }
        }
    }

    /** A file to include, with the label the context shows for it. */
    record Artifact(String label, Path file) {
    }

    /**
     * The change's files from {@code status --json}'s {@code artifactPaths}, in the CLI's (schema) order, or null when
     * the CLI gives no usable answer: it's missing, fails, rejects the change, predates {@code artifactPaths} (1.4.0),
     * or answers for a store. Files outside the change directory are dropped.
     */
    private @Nullable List<Artifact> artifactsFromStatus(String changeName, Path changeDir) {
        JsonElement payload = cliPayload("status", "--change", changeName, "--json");
        if (payload == null) return null;
        ChangeArtifactDag dag;
        try {
            dag = CliOutputParser.parseChangeStatus(payload);
        } catch (JsonParseException e) {
            LOG.warn("Unreadable openspec status output for change " + changeName + ": " + e.getMessage());
            return null;
        }
        if (dag == null || dag.getArtifactPaths().isEmpty() || (dag.getRoot() != null && dag.getRoot().isStore())) {
            return null;
        }
        Path realChangeDir = realPath(changeDir);
        if (realChangeDir == null) return null;

        List<Artifact> artifacts = new ArrayList<>();
        dag.getArtifactPaths().forEach((id, paths) -> {
            for (String output : paths.existingOutputPaths()) {
                Path file = realPath(Path.of(output));
                if (file == null || !file.startsWith(realChangeDir) || file.equals(realChangeDir)) continue;
                String label = paths.isGlob() ? id + " (" + relative(realChangeDir, file) + ")" : id;
                artifacts.add(new Artifact(label, file));
            }
        });
        return artifacts;
    }

    /**
     * Every Markdown file under the change directory, at any depth: spec-driven's proposal, design and tasks first,
     * then the rest in path order. Names starting with a dot are skipped (as upstream's globs skip them), and
     * symlinked directories aren't followed.
     */
    static List<Artifact> artifactsFromWalk(Path changeDir) {
        if (!Files.isDirectory(changeDir)) return List.of();
        List<String> paths;
        try (Stream<Path> walk = Files.walk(changeDir)) {
            paths = walk.filter(Files::isRegularFile)
                    .map(file -> relative(changeDir, file))
                    .filter(rel -> rel.endsWith(".md") && !hasDotSegment(rel))
                    .sorted()
                    .toList();
        } catch (IOException | java.io.UncheckedIOException e) {
            return List.of();
        }
        List<Artifact> artifacts = new ArrayList<>();
        for (String first : CHANGE_ARTIFACTS) {
            if (paths.contains(first)) artifacts.add(new Artifact(first, changeDir.resolve(first)));
        }
        for (String rel : paths) {
            if (!CHANGE_ARTIFACTS.contains(rel)) artifacts.add(new Artifact(rel, changeDir.resolve(rel)));
        }
        return artifacts;
    }

    /**
     * The CLI's JSON payload for {@code args}, or null when there's no CLI or it gives no usable answer. Checks for
     * cancellation first, since each call is a subprocess.
     */
    private @Nullable JsonElement cliPayload(String... args) {
        ProgressManager.checkCanceled();
        if (cli.version() == null) return null;
        try {
            return switch (CliJson.read(cli.run(args))) {
                case CliJson.Ok ok -> ok.payload();
                case CliJson.Failed failed -> {
                    LOG.info("openspec " + String.join(" ", args) + " gave no usable answer: " + failed.describe());
                    yield null;
                }
            };
        } catch (CliRunner.CliException | JsonParseException e) {
            LOG.info("openspec " + String.join(" ", args) + " failed: " + e.getMessage());
            return null;
        }
    }

    private static @Nullable Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return null;
        }
    }

    /** {@code file}'s path inside {@code dir}, with {@code /} separators on every OS. */
    private static String relative(Path dir, Path file) {
        return dir.relativize(file).toString().replace('\\', '/');
    }

    private static boolean hasDotSegment(String relativePath) {
        for (String segment : relativePath.split("/")) {
            if (segment.startsWith(".")) return true;
        }
        return false;
    }

    private static final Pattern REQUIREMENT_PATTERN =
            com.johnnyblabs.openspec.util.SpecPatterns.REQUIREMENT_HEADER;
    private static final Pattern SCENARIO_PATTERN = Pattern.compile(
            "^#### Scenario:", Pattern.MULTILINE);
    private static final Pattern NEXT_HEADER_PATTERN = Pattern.compile("^#{2,3} ", Pattern.MULTILINE);

    private void appendSpecsDomains(StringBuilder context) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        Path specsDir = Path.of(basePath, "openspec", "specs");
        if (!Files.isDirectory(specsDir)) return;

        List<String> ids = capabilitiesFromCli();
        if (ids == null) ids = capabilitiesFromWalk(specsDir);

        Path root = specsDir.normalize();
        context.append("## Specs\n");
        for (String id : ids) {
            // An id names a folder under openspec/specs/; one that climbs out (`..`) is ignored. The check is on the
            // path, not its realpath, because upstream counts a symlinked spec.md wherever it points.
            Path specFile = specsDir.resolve(id).resolve("spec.md").normalize();
            if (!specFile.startsWith(root)) continue;
            String content;
            try {
                content = Files.readString(specFile);
            } catch (IOException e) {
                continue; // Skip if unreadable
            }
            context.append("\n### ").append(id).append("\n");
            appendRequirementSummaries(content, context);
        }
        context.append("\n");
    }

    /**
     * The capability ids from {@code list --specs --json}, or null when the CLI is missing, older than
     * {@link #LIST_SPECS_FLOOR}, gives no usable answer, or answers for a store.
     */
    private @Nullable List<String> capabilitiesFromCli() {
        if (!CliVersion.atLeast(cli.version(), LIST_SPECS_FLOOR)) return null;
        JsonElement payload = cliPayload("list", "--specs", "--json");
        if (payload == null || !payload.isJsonObject()) return null;
        JsonObject root = payload.getAsJsonObject();
        if (root.has("root") && root.get("root").isJsonObject()) {
            JsonObject r = root.getAsJsonObject("root");
            ChangeArtifactDag.Root parsed = new ChangeArtifactDag.Root(string(r, "path"), string(r, "source"),
                    string(r, "store_id"));
            if (parsed.isStore()) return null;
        }
        if (!root.has("specs") || !root.get("specs").isJsonArray()) return null;
        List<String> ids = new ArrayList<>();
        for (JsonElement spec : root.getAsJsonArray("specs")) {
            if (spec.isJsonObject()) {
                String id = string(spec.getAsJsonObject(), "id");
                if (id != null && !id.isBlank()) ids.add(id);
            }
        }
        return ids;
    }

    private static @Nullable String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /**
     * Capability ids found the way upstream's spec discovery finds them (1.7.0+): every {@code spec.md} below
     * {@code specsDir}, named by its folder's path, sorted. Dot-folders are skipped, symlinked folders aren't followed,
     * and a {@code spec.md} directly in {@code specsDir} is ignored.
     */
    static List<String> capabilitiesFromWalk(Path specsDir) {
        try (Stream<Path> walk = Files.walk(specsDir)) {
            return walk.filter(file -> file.getFileName().toString().equals("spec.md") && Files.isRegularFile(file))
                    .map(file -> relative(specsDir, file.getParent()))
                    .filter(id -> !id.isEmpty() && !hasDotSegment(id))
                    .sorted()
                    .toList();
        } catch (IOException | java.io.UncheckedIOException e) {
            return List.of();
        }
    }

    private static void appendRequirementSummaries(String content, StringBuilder context) {
        Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(content);
        while (reqMatcher.find()) {
            String reqName = reqMatcher.group(1).trim();
            // Extract description: text between requirement header and first scenario (or next requirement/section)
            int descStart = reqMatcher.end();
            Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(content);
            int descEnd = content.length();
            if (scenarioMatcher.find(descStart)) {
                descEnd = scenarioMatcher.start();
            }
            // Also stop at next ### or ##
            Matcher nextHeader = NEXT_HEADER_PATTERN.matcher(content);
            if (nextHeader.find(descStart)) {
                descEnd = Math.min(descEnd, nextHeader.start());
            }
            String description = content.substring(descStart, descEnd).strip();
            context.append("- **").append(reqName).append("**");
            if (!description.isEmpty()) {
                context.append(": ").append(description);
            }
            context.append("\n");
        }
    }
}

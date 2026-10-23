package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.model.ArtifactStatus;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.model.MultiFileArtifact;
import com.johnnyblabs.openspec.util.CliJson;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
public final class ArtifactOrchestrationService {
    private static final Logger LOG = Logger.getInstance(ArtifactOrchestrationService.class);

    private final Project project;
    private final Map<String, ChangeArtifactDag> dagCache = new ConcurrentHashMap<>();
    /** The CLI's reason for rejecting a change's last {@code status} run, while it keeps rejecting it. */
    private final Map<String, String> statusErrors = new ConcurrentHashMap<>();
    private final AtomicBoolean generateAllCancelled = new AtomicBoolean(false);

    public ArtifactOrchestrationService(Project project) {
        this.project = project;
    }

    /**
     * Returns the cached DAG without spawning a CLI process.
     * Safe to call from EDT. Returns null if no cached data exists.
     */
    public ChangeArtifactDag getCachedArtifactStatus(String changeName) {
        return dagCache.get(changeName);
    }

    /**
     * Gets artifact DAG status for a change by calling the CLI.
     * <b>Must NOT be called on EDT</b> — spawns an external process.
     * Falls back to cache on failure.
     */
    /** Runs the OpenSpec CLI; a seam so tests can feed captured {@code status} and {@code instructions} output. */
    public interface StatusCli {
        CliRunner.CliResult run(Project project, String... args) throws CliRunner.CliException;
    }

    private static final StatusCli REAL_STATUS_CLI = CliRunner::run;
    private StatusCli statusCli = REAL_STATUS_CLI;

    /** Replaces the CLI runner for {@code status} and {@code instructions}; {@code null} restores the real one. */
    @org.jetbrains.annotations.TestOnly
    public void setStatusCliForTest(@org.jetbrains.annotations.Nullable StatusCli cli) {
        this.statusCli = cli == null ? REAL_STATUS_CLI : cli;
    }

    /**
     * The change's artifact status exactly as {@code openspec status --change <name> --json} reports it. Upstream
     * decides done-ness by the file existing, and the plugin doesn't second-guess it; a done file that still looks
     * like a template is only pointed out ({@link #templateLookingArtifacts}).
     *
     * <p>When the CLI rejects the change (an error envelope, e.g. the change was renamed or deleted), the cached
     * status is dropped, {@link #statusError} holds the CLI's reason, and this returns null: the CLI's answer about
     * the change is "no". When the CLI gives no answer (it could not run, timed out, or printed nothing readable),
     * the last known status is returned.
     */
    public ChangeArtifactDag getArtifactStatus(String changeName) {
        try {
            CliRunner.CliResult result = statusCli.run(project, "status", "--change", changeName, "--json");
            switch (CliJson.read(result)) {
                case CliJson.Ok ok -> {
                    ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(ok.payload());
                    if (dag != null) {
                        dagCache.put(changeName, dag);
                        statusErrors.remove(changeName);
                        return dag;
                    }
                }
                case CliJson.Failed failed when failed.kind() == CliJson.Kind.ENVELOPE -> {
                    LOG.info("openspec status rejected change " + changeName + ": " + failed.message());
                    dagCache.remove(changeName);
                    statusErrors.put(changeName, failed.describe());
                    return null;
                }
                case CliJson.Failed failed ->
                        LOG.warn("Unreadable openspec status output for change " + changeName + ": " + failed.message());
            }
        } catch (CliRunner.CliException | com.google.gson.JsonParseException e) {
            LOG.warn("Failed to get artifact status for change: " + changeName, e);
        }
        return dagCache.get(changeName);
    }

    /**
     * Why the CLI rejected this change's last {@code status} run, or null when it last answered normally (or has not
     * been asked). Cleared by the next successful status.
     */
    public @org.jetbrains.annotations.Nullable String statusError(String changeName) {
        return statusErrors.get(changeName);
    }

    /**
     * The ids of done, single-file artifacts whose file still looks like an unfilled template, for a non-blocking hint
     * on the pipeline chips. Never changes {@code dag}. Glob outputs (specs) aren't checked. Reads files: call off the
     * UI thread.
     */
    public Set<String> templateLookingArtifacts(String changeName, @org.jetbrains.annotations.Nullable ChangeArtifactDag dag) {
        String basePath = project.getBasePath();
        ScaffoldingDetectionService detector = project.getService(ScaffoldingDetectionService.class);
        if (dag == null || basePath == null || detector == null) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        for (ArtifactInfo artifact : dag.getArtifacts()) {
            if (artifact.status() != ArtifactStatus.DONE) continue;
            String outputPath = artifact.outputPath();
            if (outputPath == null || outputPath.contains("*")) continue;
            if (detector.isScaffolding(basePath + "/openspec/changes/" + changeName + "/" + outputPath)) {
                ids.add(artifact.id());
            }
        }
        return Set.copyOf(ids);
    }

    /**
     * Gets generation instructions for a specific artifact.
     */
    public ArtifactInstruction getInstruction(String changeName, String artifactId) throws CliRunner.CliException {
        CliRunner.CliResult result = statusCli.run(project,
                "instructions", artifactId, "--change", changeName, "--json");
        return switch (CliJson.read(result)) {
            case CliJson.Ok ok -> {
                try {
                    yield CliOutputParser.parseArtifactInstruction(ok.payload());
                } catch (com.google.gson.JsonParseException e) {
                    throw new CliRunner.CliException("Failed to get instructions: " + e.getMessage(), e);
                }
            }
            case CliJson.Failed failed -> throw new CliRunner.CliException("Failed to get instructions: " + failed.describe());
        };
    }

    /**
     * Checks if regenerating the given artifact would affect downstream artifacts
     * that are already complete.
     */
    public List<String> getCompletedDownstream(String changeName, String artifactId) {
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) return List.of();
        return completedDownstream(dag.getArtifacts(), artifactId);
    }

    /**
     * The DONE artifacts that depend on {@code artifactId} — i.e. those a regeneration of it could
     * make inconsistent. Pure and static so it can be pinned directly against captured status DAGs.
     *
     * <p>When the DAG carries the CLI's own dependency edges ({@code requires}, 1.7+), this walks
     * those edges transitively — precise and independent of the array's order, so it never
     * over-lists a sibling that merely appears later (1.7 reordered {@code artifacts[]} to schema
     * order, which broke the old positional heuristic's assumption). Pre-1.7 output has no
     * {@code requires}, so it falls back to the original list-order heuristic (every DONE artifact
     * appearing after {@code artifactId}), preserving 1.3–1.6 behavior exactly.
     */
    static List<String> completedDownstream(List<ArtifactInfo> artifacts, String artifactId) {
        boolean hasRequiresEdges = artifacts.stream().anyMatch(a -> !a.requires().isEmpty());

        if (hasRequiresEdges) {
            // Transitive dependents over the reverse `requires` edges.
            Set<String> dependents = new HashSet<>();
            Deque<String> frontier = new ArrayDeque<>();
            frontier.add(artifactId);
            while (!frontier.isEmpty()) {
                String target = frontier.poll();
                for (ArtifactInfo a : artifacts) {
                    if (a.requires().contains(target) && dependents.add(a.id())) {
                        frontier.add(a.id());
                    }
                }
            }
            return artifacts.stream()
                    .filter(a -> dependents.contains(a.id()) && a.status() == ArtifactStatus.DONE)
                    .map(ArtifactInfo::id)
                    .toList();
        }

        // Fallback (pre-1.7): positional — DONE artifacts appearing after `artifactId`.
        boolean found = false;
        List<String> downstream = new ArrayList<>();
        for (ArtifactInfo a : artifacts) {
            if (a.id().equals(artifactId)) {
                found = true;
                continue;
            }
            if (found && a.status() == ArtifactStatus.DONE) {
                downstream.add(a.id());
            }
        }
        return downstream;
    }

    /**
     * Returns IDs of artifacts that are ready for generation.
     */
    public List<String> getGenerationOrder(String changeName) {
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) return List.of();
        return dag.getReadyArtifacts().stream()
                .map(ArtifactInfo::id)
                .collect(Collectors.toList());
    }

    /**
     * Returns the first ready artifact for a change, or null if none are ready.
     * Must NOT be called on EDT — spawns an external process.
     */
    public ArtifactInfo getNextReadyArtifact(String changeName) {
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) return null;
        List<ArtifactInfo> ready = dag.getReadyArtifacts();
        return ready.isEmpty() ? null : ready.getFirst();
    }

    /**
     * Clears the cached DAG for a change (e.g., after generation). Also invalidates the
     * derived {@link WorkflowSchemaContextService} cache so mode/version context stays in
     * lockstep with the underlying status.
     */
    public void invalidateCache(String changeName) {
        dagCache.remove(changeName);
        statusErrors.remove(changeName);
        WorkflowSchemaContextService contextService = project.getService(WorkflowSchemaContextService.class);
        if (contextService != null) {
            contextService.invalidateCache(changeName);
        }
    }

    /**
     * Clears all cached DAGs and the derived schema-context cache.
     */
    public void invalidateAllCaches() {
        dagCache.clear();
        statusErrors.clear();
        WorkflowSchemaContextService contextService = project.getService(WorkflowSchemaContextService.class);
        if (contextService != null) {
            contextService.invalidateAllCaches();
        }
    }

    /**
     * Generates all remaining artifacts for a change in dependency order.
     * Calls the DirectApiService for each artifact, writes the result to disk,
     * and fires listener callbacks at each stage. Checks the cancellation flag
     * between artifacts.
     *
     * Must be called from a background thread.
     */
    public void generateAllRemaining(String changeName, DirectApiService apiService,
                                     GenerateAllListener listener) {
        generateAllCancelled.set(false);

        // Count total remaining artifacts
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) {
            listener.onError(null, new RuntimeException("Failed to load artifact status"));
            return;
        }
        int total = (int) dag.getArtifacts().stream()
                .filter(a -> a.status() != ArtifactStatus.DONE)
                .count();
        int index = 0;

        while (true) {
            if (generateAllCancelled.get()) {
                String nextId = findNextReadyArtifactId(dag);
                listener.onCancelled(nextId);
                return;
            }

            // Re-read DAG to respect current dependency state
            invalidateCache(changeName);
            dag = getArtifactStatus(changeName);
            if (dag == null) {
                listener.onError(null, new RuntimeException("Failed to reload artifact status"));
                return;
            }

            if (dag.isComplete()) {
                listener.onAllComplete();
                return;
            }

            String artifactId = findNextReadyArtifactId(dag);
            if (artifactId == null) {
                // No ready artifacts but not complete — shouldn't happen, but handle gracefully
                listener.onAllComplete();
                return;
            }

            index++;
            listener.onArtifactStarted(artifactId, index, total);

            try {
                ArtifactInstruction instruction = getInstruction(changeName, artifactId);
                String result = apiService.generate(instruction);
                writeArtifactResult(instruction, result);
                invalidateCache(changeName);
                listener.onArtifactCompleted(artifactId);
            } catch (AiApiException e) {
                listener.onError(artifactId, e);
                return;
            } catch (Exception e) {
                listener.onError(artifactId, e);
                return;
            }
        }
    }

    /**
     * Cancels a running generateAllRemaining operation.
     * The cancellation takes effect before the next artifact starts.
     */
    public void cancelGenerateAll() {
        generateAllCancelled.set(true);
    }

    private String findNextReadyArtifactId(ChangeArtifactDag dag) {
        List<ArtifactInfo> ready = dag.getReadyArtifacts();
        return ready.isEmpty() ? null : ready.getFirst().id();
    }

    /**
     * Writes generated artifact content to the instruction's output path and returns the files written. A glob output
     * path (the CLI declares {@code specs} as {@code specs/**}{@code /*.md}) is a set of files: the content is parsed as
     * a multi-file response and every file is written, or none if the response is rejected (see
     * {@link MultiFileArtifact}). Call off the EDT; the VFS write hops to the EDT itself.
     */
    public List<Path> writeArtifactResult(ArtifactInstruction instruction, String content) throws IOException {
        String changeDir = instruction.changeDir();
        String outputPath = instruction.outputPath();
        if (changeDir == null || outputPath == null) {
            throw new IOException("Missing changeDir or outputPath in instruction");
        }
        if (MultiFileArtifact.isGlob(outputPath)) {
            List<MultiFileArtifact.FileEntry> files = MultiFileArtifact.parse(content, outputPath);
            List<Path> written = new java.util.ArrayList<>();
            for (MultiFileArtifact.FileEntry file : files) {
                Path target = Path.of(changeDir, file.path().split("/"));
                writeFile(target, file.content());
                written.add(target);
            }
            return written;
        }
        Path filePath = Path.of(changeDir, outputPath);
        writeFile(filePath, content);
        return List.of(filePath);
    }

    private static void writeFile(Path filePath, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        if (ApplicationManager.getApplication() == null) {
            // Fallback for unit test context (no IntelliJ Application)
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            return;
        }

        String parentPath = filePath.getParent().toString();
        WriteAction.runAndWait(() -> {
            VirtualFile parentDir = VfsUtil.createDirectoryIfMissing(parentPath);
            if (parentDir == null) {
                throw new IOException("Failed to create directory: " + parentPath);
            }
            String fileName = filePath.getFileName().toString();
            VirtualFile file = parentDir.findChild(fileName);
            if (file == null) {
                file = parentDir.createChildData(ArtifactOrchestrationService.class, fileName);
            }
            file.setBinaryContent(bytes);
        });

        // Safety net: ensure parent directory is fully indexed
        VfsUtil.markDirtyAndRefresh(false, true, true,
                LocalFileSystem.getInstance().findFileByPath(parentPath));
    }
}

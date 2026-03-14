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
import java.util.ArrayList;
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
    public ChangeArtifactDag getArtifactStatus(String changeName) {
        try {
            CliRunner.CliResult result = CliRunner.run(project, "status", "--change", changeName, "--json");
            if (result.isSuccess() && !result.stdout().isBlank()) {
                ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(result.stdout());
                if (dag != null) {
                    applyScaffoldingOverrides(dag, changeName);
                    dagCache.put(changeName, dag);
                    return dag;
                }
            }
        } catch (CliRunner.CliException e) {
            LOG.warn("Failed to get artifact status for change: " + changeName, e);
        }
        return dagCache.get(changeName);
    }

    /**
     * Checks each "done" artifact for scaffolding content and overrides status accordingly.
     * Skips glob-pattern output paths (e.g., specs) since they can't be resolved to a single file.
     */
    void applyScaffoldingOverrides(ChangeArtifactDag dag, String changeName) {
        String basePath = project.getBasePath();
        if (basePath == null) return;

        ScaffoldingDetectionService detector = project.getService(ScaffoldingDetectionService.class);
        if (detector == null) return;

        String changeDir = basePath + "/openspec/changes/" + changeName;
        List<ArtifactInfo> artifacts = dag.getArtifacts();

        // First pass: identify which artifacts are scaffolding
        Set<String> scaffoldedIds = new HashSet<>();
        for (ArtifactInfo artifact : artifacts) {
            if (artifact.status() != ArtifactStatus.DONE) continue;

            String outputPath = artifact.outputPath();
            // Skip glob patterns — can't resolve to a single file
            if (outputPath == null || outputPath.contains("*")) continue;

            String filePath = changeDir + "/" + outputPath;
            if (detector.isScaffolding(filePath)) {
                scaffoldedIds.add(artifact.id());
            }
        }

        if (scaffoldedIds.isEmpty()) return;

        // Second pass: override status based on dependency analysis
        // Use earlier artifacts in the list as implicit dependencies
        // (DAG order: proposal → design/specs → tasks)
        List<ArtifactInfo> updatedArtifacts = new ArrayList<>();
        for (ArtifactInfo artifact : artifacts) {
            if (!scaffoldedIds.contains(artifact.id())) {
                updatedArtifacts.add(artifact);
                continue;
            }

            // Check if any earlier artifacts in the list are also scaffolded
            List<String> blockedBy = new ArrayList<>();
            for (ArtifactInfo earlier : artifacts) {
                if (earlier.id().equals(artifact.id())) break;
                if (scaffoldedIds.contains(earlier.id())) {
                    blockedBy.add(earlier.id());
                }
            }

            if (blockedBy.isEmpty()) {
                updatedArtifacts.add(new ArtifactInfo(artifact.id(), artifact.outputPath(), ArtifactStatus.READY, List.of()));
            } else {
                updatedArtifacts.add(new ArtifactInfo(artifact.id(), artifact.outputPath(), ArtifactStatus.BLOCKED, blockedBy));
            }
        }
        dag.setArtifacts(updatedArtifacts);

        // Recalculate isComplete
        boolean allDone = updatedArtifacts.stream()
                .allMatch(a -> a.status() == ArtifactStatus.DONE);
        dag.setComplete(allDone);
    }

    /**
     * Gets generation instructions for a specific artifact.
     */
    public ArtifactInstruction getInstruction(String changeName, String artifactId) throws CliRunner.CliException {
        CliRunner.CliResult result = CliRunner.run(project,
                "instructions", artifactId, "--change", changeName, "--json");
        if (result.isSuccess() && !result.stdout().isBlank()) {
            return CliOutputParser.parseArtifactInstruction(result.stdout());
        }
        throw new CliRunner.CliException(
                "Failed to get instructions: " + (result.stderr().isBlank() ? "empty response" : result.stderr()));
    }

    /**
     * Checks if regenerating the given artifact would affect downstream artifacts
     * that are already complete.
     */
    public List<String> getCompletedDownstream(String changeName, String artifactId) {
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) return List.of();

        List<ArtifactInfo> artifacts = dag.getArtifacts();
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
     * Clears the cached DAG for a change (e.g., after generation).
     */
    public void invalidateCache(String changeName) {
        dagCache.remove(changeName);
    }

    /**
     * Clears all cached DAGs.
     */
    public void invalidateAllCaches() {
        dagCache.clear();
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

    private void writeArtifactResult(ArtifactInstruction instruction, String content) throws IOException {
        String changeDir = instruction.changeDir();
        String outputPath = instruction.outputPath();
        if (changeDir == null || outputPath == null) {
            throw new IOException("Missing changeDir or outputPath in instruction");
        }

        Path filePath = Path.of(changeDir, outputPath);
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
                file = parentDir.createChildData(this, fileName);
            }
            file.setBinaryContent(bytes);
        });

        // Safety net: ensure parent directory is fully indexed
        VfsUtil.markDirtyAndRefresh(false, true, true,
                LocalFileSystem.getInstance().findFileByPath(parentPath));
    }
}

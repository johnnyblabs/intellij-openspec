package com.johnnyb.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.model.ArtifactInfo;
import com.johnnyb.openspec.model.ArtifactInstruction;
import com.johnnyb.openspec.model.ArtifactStatus;
import com.johnnyb.openspec.model.ChangeArtifactDag;
import com.johnnyb.openspec.util.CliOutputParser;
import com.johnnyb.openspec.util.CliRunner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
public final class ArtifactOrchestrationService {
    private static final Logger LOG = Logger.getInstance(ArtifactOrchestrationService.class);

    private final Project project;
    private final Map<String, ChangeArtifactDag> dagCache = new ConcurrentHashMap<>();

    public ArtifactOrchestrationService(Project project) {
        this.project = project;
    }

    /**
     * Gets artifact DAG status for a change. Uses cache to avoid blocking EDT.
     * Applies scaffolding detection to override CLI-reported status when files
     * contain only placeholder content.
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
            if (artifact.getStatus() != ArtifactStatus.DONE) continue;

            String outputPath = artifact.getOutputPath();
            // Skip glob patterns — can't resolve to a single file
            if (outputPath == null || outputPath.contains("*")) continue;

            String filePath = changeDir + "/" + outputPath;
            if (detector.isScaffolding(filePath)) {
                scaffoldedIds.add(artifact.getId());
            }
        }

        if (scaffoldedIds.isEmpty()) return;

        // Second pass: override status based on dependency analysis
        // Use earlier artifacts in the list as implicit dependencies
        // (DAG order: proposal → design/specs → tasks)
        Set<String> trulyDone = new HashSet<>();
        for (ArtifactInfo artifact : artifacts) {
            if (artifact.getStatus() == ArtifactStatus.DONE && !scaffoldedIds.contains(artifact.getId())) {
                trulyDone.add(artifact.getId());
            }
        }

        for (ArtifactInfo artifact : artifacts) {
            if (!scaffoldedIds.contains(artifact.getId())) continue;

            // Check if any earlier artifacts in the list are also scaffolded
            List<String> blockedBy = new ArrayList<>();
            for (ArtifactInfo earlier : artifacts) {
                if (earlier.getId().equals(artifact.getId())) break;
                if (scaffoldedIds.contains(earlier.getId())) {
                    blockedBy.add(earlier.getId());
                }
            }

            if (blockedBy.isEmpty()) {
                artifact.setStatus(ArtifactStatus.READY);
            } else {
                artifact.setStatus(ArtifactStatus.BLOCKED);
                artifact.setMissingDeps(blockedBy);
            }
        }

        // Recalculate isComplete
        boolean allDone = artifacts.stream()
                .allMatch(a -> a.getStatus() == ArtifactStatus.DONE);
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
     * Returns IDs of artifacts that are ready for generation.
     */
    public List<String> getGenerationOrder(String changeName) {
        ChangeArtifactDag dag = getArtifactStatus(changeName);
        if (dag == null) return List.of();
        return dag.getReadyArtifacts().stream()
                .map(ArtifactInfo::getId)
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
}

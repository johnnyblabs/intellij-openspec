package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract: for a change holding the plugin's old design/tasks templates, real CLI 1.14.3 reports every artifact done
 * ({@code fixtures/cli/1.14.3/status-template-artifacts/}). The plugin passes that through unchanged and only names
 * the template files.
 */
class StatusPassThroughContractTest {

    private static final Path CAPTURE = Paths.get("src/test/resources/fixtures/cli/1.14.3/status-template-artifacts");

    @TempDir
    Path base;

    @Test
    void theCliStatusIsUsedUnchangedAndTheTemplatesAreOnlyNamed() throws IOException {
        Path change = base.resolve("openspec/changes/c");
        try (Stream<Path> files = Files.walk(CAPTURE.resolve("change"))) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path to = change.resolve(CAPTURE.resolve("change").relativize(f).toString());
                Files.createDirectories(to.getParent());
                Files.copy(f, to);
            }
        }
        String captured = Files.readString(CAPTURE.resolve("status.json"));
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(base.toString());
        ScaffoldingDetectionService detector = org.mockito.Mockito.spy(new ScaffoldingDetectionService(null));
        when(project.getService(ScaffoldingDetectionService.class)).thenReturn(detector);
        ArtifactOrchestrationService service = new ArtifactOrchestrationService(project);
        service.setStatusCliForTest((p, args) -> {
            assertEquals(List.of("status", "--change", "c", "--json"), List.of(args));
            return new CliRunner.CliResult(0, captured, "");
        });

        ChangeArtifactDag dag = service.getArtifactStatus("c");
        ChangeArtifactDag cli = CliOutputParser.parseChangeStatus(captured);
        org.mockito.Mockito.verify(detector, org.mockito.Mockito.never()).isScaffolding(org.mockito.ArgumentMatchers.any());
        assertSame(dag, service.getCachedArtifactStatus("c"));

        assertEquals(cli.isComplete(), dag.isComplete());
        assertTrue(dag.isComplete(), "the CLI reports the templated change complete");
        assertEquals(cli.getArtifacts(), dag.getArtifacts(), "every artifact's status as the CLI reported it");
        assertEquals(Set.of("design", "tasks"), service.templateLookingArtifacts("c", dag));
        assertTrue(dag.getArtifacts().stream().map(ArtifactInfo::id).toList().containsAll(List.of("proposal", "specs", "design", "tasks")));
    }
}

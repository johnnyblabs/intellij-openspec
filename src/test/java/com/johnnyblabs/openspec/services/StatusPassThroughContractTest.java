package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.ArtifactInfo;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.model.ArtifactInstruction;
import com.johnnyblabs.openspec.util.CliFixtures;
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

    // ---- A change the CLI rejects (captured 1.14.3 failures) ----------------------------------------------------

    private static final String GOOD_STATUS = CliFixtures.text("1.14.3/status-template-artifacts/status.json");

    private static ArtifactOrchestrationService serviceAnswering(java.util.function.Supplier<CliRunner.CliResult> next) {
        Project project = mock(Project.class);
        ArtifactOrchestrationService service = new ArtifactOrchestrationService(project);
        service.setStatusCliForTest((p, args) -> next.get());
        return service;
    }

    @Test
    void aRejectedChangeDropsTheCachedStatusAndKeepsTheCliReason() {
        java.util.concurrent.atomic.AtomicReference<Object> answer = new java.util.concurrent.atomic.AtomicReference<>(
                new CliRunner.CliResult(0, GOOD_STATUS, ""));
        ArtifactOrchestrationService service = new ArtifactOrchestrationService(mock(Project.class));
        service.setStatusCliForTest((p, args) -> {
            Object a = answer.get();
            if (a instanceof CliRunner.CliException e) throw e;
            return (CliRunner.CliResult) a;
        });
        assertNotNull(service.getArtifactStatus("c"), "cache seeded with a good status");

        CliRunner.CliResult rejected = CliFixtures.error("status-unknown");
        answer.set(rejected);
        assertNull(service.getArtifactStatus("c"), "the earlier status used to come back for a rejected change");
        String reason = service.statusError("c");
        assertNotNull(reason);
        assertTrue(rejected.stdout().contains(reason.split("\n")[0].replace("\"", "\\\"")),
                "the reason is the CLI's message");

        // The cache was dropped: a later run that gives no answer has nothing stale to fall back to.
        answer.set(new CliRunner.CliException("OpenSpec CLI command timed out after 30s"));
        assertNull(service.getArtifactStatus("c"));

        answer.set(new CliRunner.CliResult(0, GOOD_STATUS, ""));
        assertNotNull(service.getArtifactStatus("c"));
        assertNull(service.statusError("c"), "a normal answer clears the reason");
    }

    @Test
    void noAnswerKeepsTheLastKnownStatus() {
        java.util.concurrent.atomic.AtomicBoolean timeout = new java.util.concurrent.atomic.AtomicBoolean();
        ArtifactOrchestrationService service = new ArtifactOrchestrationService(mock(Project.class));
        service.setStatusCliForTest((p, args) -> {
            if (timeout.get()) throw new CliRunner.CliException("OpenSpec CLI command timed out after 30s");
            return new CliRunner.CliResult(0, GOOD_STATUS, "");
        });
        ChangeArtifactDag first = service.getArtifactStatus("c");
        timeout.set(true);
        assertSame(first, service.getArtifactStatus("c"));
        assertNull(service.statusError("c"));
    }

    @Test
    void unreadableOutputKeepsTheLastKnownStatus() {
        // Truncated real status output is no answer about the change, unlike an error envelope.
        java.util.concurrent.atomic.AtomicBoolean truncated = new java.util.concurrent.atomic.AtomicBoolean();
        ArtifactOrchestrationService service = new ArtifactOrchestrationService(mock(Project.class));
        service.setStatusCliForTest((p, args) -> new CliRunner.CliResult(0,
                truncated.get() ? CliFixtures.truncated(GOOD_STATUS) : GOOD_STATUS, ""));
        ChangeArtifactDag first = service.getArtifactStatus("c");
        truncated.set(true);
        assertSame(first, service.getArtifactStatus("c"));
        assertNull(service.statusError("c"));
    }

    @Test
    void unreadableInstructionsCarryStderr() {
        CliRunner.CliResult failed = CliFixtures.error("update-fail"); // a real text-only failure: exit 1, stderr only
        ArtifactOrchestrationService service = serviceAnswering(() -> failed);
        CliRunner.CliException e = assertThrows(CliRunner.CliException.class, () -> service.getInstruction("c", "proposal"));
        assertTrue(e.getMessage().contains(failed.stderr().strip()), e.getMessage());
    }

    @Test
    void textBeforeTheStatusJsonStillParses() {
        // Used to throw JsonSyntaxException out of getArtifactStatus.
        ArtifactOrchestrationService service = serviceAnswering(
                () -> new CliRunner.CliResult(0, "Note: printed first\n" + GOOD_STATUS, ""));
        ChangeArtifactDag dag = service.getArtifactStatus("c");
        assertNotNull(dag);
        assertEquals(CliOutputParser.parseChangeStatus(GOOD_STATUS).getArtifacts(), dag.getArtifacts());
    }

    @Test
    void rejectedInstructionsCarryTheCliReason() {
        CliRunner.CliResult rejected = CliFixtures.error("instructions-unknown-artifact");
        assertTrue(rejected.stderr().isEmpty(), "capture precondition: the reason is only on stdout");
        ArtifactOrchestrationService service = serviceAnswering(() -> rejected);

        CliRunner.CliException e = assertThrows(CliRunner.CliException.class,
                () -> service.getInstruction("good", "bogusart"));
        assertTrue(e.getMessage().contains("Artifact 'bogusart' not found"), "used to say 'empty response': " + e.getMessage());
    }

    @Test
    void instructionsWithTextBeforeTheJsonParse() throws CliRunner.CliException {
        String captured = CliFixtures.text("instructions-proposal.json");
        ArtifactOrchestrationService service = serviceAnswering(
                () -> new CliRunner.CliResult(0, "Note: printed first\n" + captured, ""));
        ArtifactInstruction instruction = service.getInstruction("c", "proposal");
        assertEquals(CliOutputParser.parseArtifactInstruction(captured).artifactId(), instruction.artifactId());
    }
}

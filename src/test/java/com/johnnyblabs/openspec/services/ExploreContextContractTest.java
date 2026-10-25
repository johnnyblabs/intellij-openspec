package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ChangeArtifactDag;
import com.johnnyblabs.openspec.util.CliOutputParser;
import com.johnnyblabs.openspec.util.CliRunner;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the Explore context's CLI sources, against real {@code list --specs --json} and
 * {@code status --json} output captured by {@code scripts/capture-explore-context-fixtures.py}. The fixtures name
 * {@code /fixture/project}; each test builds the same project under a temp dir, with a unique marker in every file,
 * and points the fixture paths at it. The CLI reports realpaths, so the fixtures point at the temp dir's realpath while
 * the project's base path stays as JUnit gave it (on macOS {@code /var/...} vs {@code /private/var/...}).
 */
class ExploreContextContractTest {

    private static final String V115 = "/fixtures/cli/1.15.0/explore-context/";
    private static final String V131 = "/fixtures/cli/1.3.1/explore-context/";

    @TempDir
    Path tempDir;
    private Path root;

    @BeforeEach
    void setUp() throws IOException {
        root = tempDir.toRealPath();
    }

    @Nested
    class StatusModel {

        @Test
        void artifactPathsKeepTheSchemasDeclarationOrder() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture(V115 + "status-research-sso.json"));

            assertEquals(List.of("research", "proposal", "specs", "design", "tasks"),
                    List.copyOf(dag.getArtifactPaths().keySet()));
            ChangeArtifactDag.ArtifactPaths research = dag.getArtifactPaths().get("research");
            assertEquals("research/**/*.md", research.outputPath());
            assertTrue(research.isGlob());
            assertEquals(List.of("/fixture/project/openspec/changes/research-sso/research/summary.md",
                    "/fixture/project/openspec/changes/research-sso/research/vendors/okta.md"),
                    research.existingOutputPaths());
            assertFalse(dag.getArtifactPaths().get("proposal").isGlob());
            assertEquals(List.of(), dag.getArtifactPaths().get("design").existingOutputPaths());
        }

        @Test
        void aProjectRootIsNotAStore() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture(V115 + "status-add-mfa.json"));

            assertNotNull(dag.getRoot());
            assertEquals("nearest", dag.getRoot().source());
            assertFalse(dag.getRoot().isStore());
        }

        @Test
        void aDeclaredRootIsAStore() {
            String declared = fixture(V115 + "status-add-mfa.json")
                    .replace("\"source\": \"nearest\"", "\"source\": \"declared\", \"store_id\": \"team\"");

            ChangeArtifactDag.Root r = CliOutputParser.parseChangeStatus(declared).getRoot();

            assertEquals("team", r.storeId());
            assertTrue(r.isStore());
        }

        @Test
        void aCliBefore140HasNoArtifactPaths() {
            ChangeArtifactDag dag = CliOutputParser.parseChangeStatus(fixture(V131 + "status-add-mfa.json"));

            assertFalse(dag.getArtifacts().isEmpty());
            assertTrue(dag.getArtifactPaths().isEmpty());
            assertNull(dag.getRoot());
        }
    }

    @Nested
    class ChangeArtifacts {

        @Test
        void addMfaArtifactsComeInTheClisOrderLabelledById() throws IOException {
            FakeCli cli = cli("1.15.0").answer("status --change add-mfa --json", V115 + "status-add-mfa.json");
            String context = assemble(cli, "add-mfa");

            int proposal = context.indexOf("MARK-MFA-PROPOSAL");
            int specs = context.indexOf("MARK-MFA-DELTA");
            int design = context.indexOf("MARK-MFA-DESIGN");
            int tasks = context.indexOf("MARK-MFA-TASKS");
            assertTrue(proposal >= 0 && specs >= 0 && design >= 0 && tasks >= 0, context);
            // The CLI lists proposal, specs, design, tasks; the fallback walk would put specs last.
            assertTrue(proposal < specs && specs < design && design < tasks, context);
            assertTrue(context.contains("**proposal:**"));
            assertTrue(context.contains("**design:**"));
            assertTrue(context.contains("**tasks:**"));
            assertTrue(context.contains("**specs (specs/identity/user-auth/spec.md):**"), context);
            assertTrue(cli.calls.contains("status --change add-mfa --json"), cli.calls.toString());
        }

        @Test
        void customSchemaArtifactsAreIncludedWithTheirPaths() throws IOException {
            FakeCli cli = cli("1.15.0").answer("status --change research-sso --json", V115 + "status-research-sso.json");
            String context = assemble(cli, "research-sso");

            assertTrue(context.contains("**research (research/summary.md):**\n\nMARK-SSO-SUMMARY"), context);
            assertTrue(context.contains("**research (research/vendors/okta.md):**\n\nMARK-SSO-OKTA"), context);
            assertTrue(context.indexOf("MARK-SSO-OKTA") < context.indexOf("MARK-SSO-PROPOSAL"),
                    "research is declared before proposal in the schema");
        }

        @Test
        void aFileTheCliDoesNotListIsLeftOut() throws IOException {
            FakeCli cli = cli("1.15.0").answer("status --change research-sso --json", V115 + "status-research-sso.json");
            String context = assemble(cli, "research-sso");

            assertFalse(context.contains("MARK-SSO-UNLISTED"), "the walk would include notes.md; the CLI doesn't");
        }

        @Test
        void aListedFileOutsideTheChangeIsDropped() throws IOException {
            Files.writeString(root.resolve("secret.md"), "MARK-OUTSIDE");
            String escaped = fixture(V115 + "status-add-mfa.json").replace(
                    "/fixture/project/openspec/changes/add-mfa/design.md\"", "/fixture/project/secret.md\"");
            FakeCli cli = cli("1.15.0").answerText("status --change add-mfa --json", escaped);

            String context = assemble(cli, "add-mfa");

            assertFalse(context.contains("MARK-OUTSIDE"), context);
            assertTrue(context.contains("MARK-MFA-PROPOSAL"), "the rest of the CLI answer is still used");
        }

        @Test
        void aListedSymlinkThatLeadsOutOfTheChangeIsDropped() throws IOException {
            Files.writeString(root.resolve("secret.md"), "MARK-OUTSIDE");
            Path link = root.resolve("openspec/changes/add-mfa/link.md");
            Files.createDirectories(link.getParent());
            try {
                Files.createSymbolicLink(link, root.resolve("secret.md"));
            } catch (UnsupportedOperationException | IOException | SecurityException e) {
                org.junit.jupiter.api.Assumptions.abort("symlinks unavailable here: " + e);
            }
            String listed = fixture(V115 + "status-add-mfa.json").replace(
                    "/fixture/project/openspec/changes/add-mfa/design.md\"", "/fixture/project/openspec/changes/add-mfa/link.md\"");
            FakeCli cli = cli("1.15.0").answerText("status --change add-mfa --json", listed);

            String context = assemble(cli, "add-mfa");

            assertFalse(context.contains("MARK-OUTSIDE"), context);
            assertTrue(context.contains("MARK-MFA-PROPOSAL"), context);
        }

        @Test
        void theErrorEnvelopeFallsBackToTheWalk() throws IOException {
            FakeCli cli = cli("1.15.0").answer("status --change research-sso --json", V115 + "status-missing.json");
            String context = assemble(cli, "research-sso");

            assertTrue(context.contains("**research/vendors/okta.md:**"), context);
            assertTrue(context.contains("MARK-SSO-UNLISTED"), "the walk includes every Markdown file");
        }

        @Test
        void aCliWithoutArtifactPathsFallsBackToTheWalk() throws IOException {
            FakeCli cli = cli("1.3.1").answer("status --change add-mfa --json", V131 + "status-add-mfa.json");
            String context = assemble(cli, "add-mfa");

            assertTrue(context.contains("**specs/identity/user-auth/spec.md:**\n\nMARK-MFA-DELTA"), context);
            assertTrue(context.contains("**proposal.md:**"), context);
        }

        @Test
        void aStoreRootedAnswerFallsBackToTheWalk() throws IOException {
            String store = fixture(V115 + "status-research-sso.json")
                    .replace("\"source\": \"nearest\"", "\"source\": \"declared\", \"store_id\": \"team\"");
            FakeCli cli = cli("1.15.0").answerText("status --change research-sso --json", store);

            String context = assemble(cli, "research-sso");

            assertTrue(context.contains("MARK-SSO-UNLISTED"), "a store's answer is ignored, so the walk is used");
            assertFalse(context.contains("**research (research/summary.md):**"), context);
        }

        @Test
        void withoutACliTheWalkIsUsedAndTheCliIsNotRun() throws IOException {
            FakeCli cli = cli(null);
            String context = assemble(cli, "add-mfa");

            assertTrue(context.contains("**specs/identity/user-auth/spec.md:**"), context);
            assertTrue(cli.calls.isEmpty(), cli.calls.toString());
        }
    }

    @Nested
    class MainSpecs {

        @Test
        void capabilitiesComeFromListSpecsWithNestedIds() throws IOException {
            FakeCli cli = cli("1.15.0").answer("list --specs --json", V115 + "list-specs.json");
            String context = assemble(cli);

            assertTrue(context.contains("\n### billing\n- **Invoice**"), context);
            assertTrue(context.contains("\n### identity/user-auth\n- **Login**"), context);
            assertFalse(context.contains("### orphan"), "the walk would list orphan; the CLI doesn't");
            assertTrue(cli.calls.contains("list --specs --json"), cli.calls.toString());
        }

        @Test
        void belowTheFloorTheWalkIsUsedAndListSpecsIsNotRun() throws IOException {
            FakeCli cli = cli("1.6.0").answer("list --specs --json", V115 + "list-specs.json");
            String context = assemble(cli);

            assertTrue(context.contains("### identity/user-auth"), context);
            assertTrue(context.contains("### orphan"), context);
            assertFalse(cli.calls.contains("list --specs --json"), cli.calls.toString());
        }

        @Test
        void aFailingListSpecsFallsBackToTheWalk() throws IOException {
            FakeCli cli = cli("1.15.0").failing("list --specs --json");
            String context = assemble(cli);

            assertTrue(context.contains("### identity/user-auth"), context);
            assertTrue(context.contains("### orphan"), context);
        }

        @Test
        void anIdThatClimbsOutOfSpecsIsIgnored() throws IOException {
            write("openspec/outside/spec.md", spec("Outside"));
            String climbing = fixture(V115 + "list-specs.json").replace("\"id\": \"billing\"", "\"id\": \"../outside\"");
            FakeCli cli = cli("1.15.0").answerText("list --specs --json", climbing);

            String context = assemble(cli);

            assertFalse(context.contains("Outside"), context);
            assertTrue(context.contains("### identity/user-auth"), "the other ids are still listed");
        }

        @Test
        void aStoreRootedListIsIgnored() throws IOException {
            String store = fixture(V115 + "list-specs.json")
                    .replace("\"source\": \"nearest\"", "\"source\": \"global_default\"");
            FakeCli cli = cli("1.15.0").answerText("list --specs --json", store);

            String context = assemble(cli);

            assertTrue(context.contains("### orphan"), "a store's list is ignored, so the walk is used");
        }
    }

    // --- the project the fixtures describe ---

    private void seedProject() throws IOException {
        write("openspec/specs/billing/spec.md", spec("Invoice"));
        write("openspec/specs/identity/user-auth/spec.md", spec("Login"));
        write("openspec/specs/orphan/spec.md", spec("Orphan"));

        write("openspec/changes/add-mfa/.openspec.yaml", "schema: spec-driven\n");
        write("openspec/changes/add-mfa/proposal.md", "MARK-MFA-PROPOSAL");
        write("openspec/changes/add-mfa/design.md", "MARK-MFA-DESIGN");
        write("openspec/changes/add-mfa/tasks.md", "MARK-MFA-TASKS");
        write("openspec/changes/add-mfa/specs/identity/user-auth/spec.md", "MARK-MFA-DELTA");

        write("openspec/changes/research-sso/.openspec.yaml", "schema: research-first\n");
        write("openspec/changes/research-sso/proposal.md", "MARK-SSO-PROPOSAL");
        write("openspec/changes/research-sso/research/summary.md", "MARK-SSO-SUMMARY");
        write("openspec/changes/research-sso/research/vendors/okta.md", "MARK-SSO-OKTA");
        write("openspec/changes/research-sso/notes.md", "MARK-SSO-UNLISTED");
    }

    private static String spec(String requirement) {
        return "# Spec\n\n## Purpose\nP.\n\n## Requirements\n\n### Requirement: " + requirement
                + "\nThe system SHALL do it.\n\n#### Scenario: Works\n- **WHEN** used\n- **THEN** it SHALL work\n";
    }

    private void write(String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** Assembles the context with {@code cli}, with the given changes active. */
    private String assemble(FakeCli cli, String... changes) throws IOException {
        seedProject();
        Project project = mock(Project.class);
        ChangeService changeService = mock(ChangeService.class);
        when(project.getBasePath()).thenReturn(tempDir.toString());
        when(project.getService(ChangeService.class)).thenReturn(changeService);
        List<Change> active = new ArrayList<>();
        for (String name : changes) {
            active.add(new Change(name, tempDir.resolve("openspec/changes/" + name).toString()));
        }
        when(changeService.getActiveChanges()).thenReturn(active);

        ExploreContextService service = new ExploreContextService(project);
        service.setCliForTest(cli);
        return service.assembleContext();
    }

    // --- fixtures and the fake CLI ---

    private static String fixture(String path) {
        try (InputStream is = ExploreContextContractTest.class.getResourceAsStream(path)) {
            assertNotNull(is, "missing fixture " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    /** {@code text} with {@code /fixture/project} pointing at the temp project, in forward slashes on every OS. */
    private String remap(String text) {
        return text.replace("/fixture/project", root.toString().replace('\\', '/'));
    }

    private FakeCli cli(@Nullable String version) {
        return new FakeCli(version);
    }

    private final class FakeCli implements ExploreContextService.Cli {
        private final String version;
        private final Map<String, String> answers = new LinkedHashMap<>();
        private final List<String> failing = new ArrayList<>();
        final List<String> calls = new ArrayList<>();

        FakeCli(@Nullable String version) {
            this.version = version;
        }

        FakeCli answer(String args, String fixturePath) {
            return answerText(args, fixture(fixturePath));
        }

        FakeCli answerText(String args, String text) {
            answers.put(args, remap(text));
            return this;
        }

        FakeCli failing(String args) {
            failing.add(args);
            return this;
        }

        @Override
        public @Nullable String version() {
            return version;
        }

        @Override
        public CliRunner.CliResult run(String... args) throws CliRunner.CliException {
            String joined = String.join(" ", args);
            calls.add(joined);
            if (failing.contains(joined)) throw new CliRunner.CliException("simulated failure");
            String out = answers.get(joined);
            if (out == null) throw new CliRunner.CliException("no answer for " + joined);
            return new CliRunner.CliResult(0, out, "");
        }
    }
}

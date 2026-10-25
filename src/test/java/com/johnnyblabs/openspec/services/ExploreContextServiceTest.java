package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.ChangeMetadata;
import com.johnnyblabs.openspec.model.OpenSpecConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExploreContextServiceTest {

    @Mock Project project;
    @Mock ConfigService configService;
    @Mock ChangeService changeService;
    @Mock AiToolDetectionService aiToolDetectionService;

    private ExploreContextService service;

    @BeforeEach
    void setUp() {
        service = new ExploreContextService(project);
    }

    @Nested
    class EmptyProjectState {

        @Test
        void assemblesContext_withNoConfigChangesOrSpecs() {
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);

            when(configService.getConfig()).thenReturn(null);
            when(changeService.getActiveChanges()).thenReturn(List.of());
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of());
            when(project.getBasePath()).thenReturn("/nonexistent");

            String context = service.assembleContext();

            assertNotNull(context);
            assertTrue(context.contains("# OpenSpec Explore Context"));
            assertTrue(context.contains("## Detected AI Tools"));
            assertTrue(context.contains("None detected."));
            assertTrue(context.contains("## Active Changes"));
            assertTrue(context.contains("No active changes."));
        }
    }

    @Nested
    class ConfigContextAndRules {

        @Test
        void includesContextAndRulesInConfigSection() {
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);
            when(project.getBasePath()).thenReturn("/nonexistent");

            when(changeService.getActiveChanges()).thenReturn(List.of());
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of());

            OpenSpecConfig config = new OpenSpecConfig();
            config.setVersion("1.2.0");
            config.setSchema("spec-driven");
            config.setContext("An IntelliJ plugin for spec-driven development.");
            config.setRules(Map.of("services", "All services SHALL be project services"));
            when(configService.getConfig()).thenReturn(config);

            String context = service.assembleContext();

            // `version:` is no longer surfaced — it's a plugin-internal field, off-model for the AI prompt.
            assertFalse(context.contains("Version:"));
            assertTrue(context.contains("Schema: spec-driven"));
            assertTrue(context.contains("> An IntelliJ plugin for spec-driven development."));
            assertTrue(context.contains("**Rules:**"));
            assertTrue(context.contains("**services**: All services SHALL be project services"));
        }

        @Test
        void omitsVersionLineWhenVersionAbsent() {
            // A schema-only config (the new scaffold shape) has no version:. The AI context must NOT
            // emit "- Version: null" — the line is omitted entirely when the field is absent.
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);
            when(project.getBasePath()).thenReturn("/nonexistent");
            when(changeService.getActiveChanges()).thenReturn(List.of());
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of());

            OpenSpecConfig config = new OpenSpecConfig(); // schema-only: no version:
            config.setSchema("spec-driven");
            when(configService.getConfig()).thenReturn(config);

            String context = service.assembleContext();

            assertFalse(context.contains("Version: null"), "must never emit 'Version: null'");
            assertFalse(context.contains("- Version:"), "the Version: line is omitted entirely when absent");
            assertTrue(context.contains("Schema: spec-driven"), "schema still appears");
        }
    }

    @Nested
    class SpecRequirementSummaries {

        @TempDir
        Path tempDir;

        @Test
        void showsRequirementNamesAndDescriptions() throws IOException {
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);
            when(project.getBasePath()).thenReturn(tempDir.toString());

            when(configService.getConfig()).thenReturn(null);
            when(changeService.getActiveChanges()).thenReturn(List.of());
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of());

            Path specDomain = tempDir.resolve("openspec/specs/validation");
            Files.createDirectories(specDomain);
            Files.writeString(specDomain.resolve("spec.md"),
                    "# Validation\n\n## Requirements\n\n" +
                    "### Requirement: Config validation\n\n" +
                    "The plugin SHALL validate config.yaml for correctness.\n\n" +
                    "#### Scenario: Config missing\n- **WHEN** config missing\n- **THEN** report error\n\n" +
                    "### Requirement: Spec format validation\n\n" +
                    "The plugin SHALL validate spec files for completeness.\n\n" +
                    "#### Scenario: Missing title\n- **WHEN** no title\n- **THEN** report error\n");

            String context = service.assembleContext();

            assertTrue(context.contains("### validation"));
            assertTrue(context.contains("**Config validation**: The plugin SHALL validate config.yaml for correctness."));
            assertTrue(context.contains("**Spec format validation**: The plugin SHALL validate spec files for completeness."));
            // Should NOT contain scenario details
            assertFalse(context.contains("Config missing"));
        }
    }

    @Nested
    class FullChangeArtifacts {

        @TempDir
        Path tempDir;

        @Test
        void includesFullArtifactContent() throws IOException {
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);
            when(project.getBasePath()).thenReturn(tempDir.toString());

            when(configService.getConfig()).thenReturn(null);
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of());

            Change change = new Change("my-change", tempDir.resolve("openspec/changes/my-change").toString());
            ChangeMetadata meta = new ChangeMetadata();
            meta.setSchema("spec-driven");
            change.setMetadata(meta);
            when(changeService.getActiveChanges()).thenReturn(List.of(change));

            Path changeDir = tempDir.resolve("openspec/changes/my-change");
            Files.createDirectories(changeDir);
            Files.writeString(changeDir.resolve("proposal.md"), "## Why\n\nThis is the full proposal.");
            Files.writeString(changeDir.resolve("design.md"), "## Context\n\nThis is the full design.");
            Files.writeString(changeDir.resolve("tasks.md"), "## 1. Tasks\n\n- [ ] 1.1 Do something");

            // Delta spec
            Path deltaDir = changeDir.resolve("specs/validation");
            Files.createDirectories(deltaDir);
            Files.writeString(deltaDir.resolve("spec.md"), "## MODIFIED Requirements\n\n### Requirement: Config\n\nUpdated.");

            String context = service.assembleContext();

            assertTrue(context.contains("### my-change (spec-driven)"));
            assertTrue(context.contains("**proposal.md:**"));
            assertTrue(context.contains("This is the full proposal."));
            assertTrue(context.contains("**design.md:**"));
            assertTrue(context.contains("This is the full design."));
            assertTrue(context.contains("**tasks.md:**"));
            assertTrue(context.contains("- [ ] 1.1 Do something"));
            assertTrue(context.contains("**specs/validation/spec.md:**"));
            assertTrue(context.contains("## MODIFIED Requirements"));
        }

        @Test
        void skipsMissingArtifactsSilently() throws IOException {
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);
            when(project.getBasePath()).thenReturn(tempDir.toString());

            when(configService.getConfig()).thenReturn(null);
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of());

            Change change = new Change("partial-change", tempDir.resolve("openspec/changes/partial-change").toString());
            when(changeService.getActiveChanges()).thenReturn(List.of(change));

            // Only create proposal — no design, tasks, or specs
            Path changeDir = tempDir.resolve("openspec/changes/partial-change");
            Files.createDirectories(changeDir);
            Files.writeString(changeDir.resolve("proposal.md"), "Just a proposal.");

            String context = service.assembleContext();

            assertTrue(context.contains("### partial-change"));
            assertTrue(context.contains("**proposal.md:**"));
            assertTrue(context.contains("Just a proposal."));
            assertFalse(context.contains("**design.md:**"));
            assertFalse(context.contains("**tasks.md:**"));
        }
    }

    @Nested
    class ContextFormat {

        @Test
        void contextContainsExpectedMarkdownHeaders() {
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);
            when(project.getBasePath()).thenReturn("/nonexistent");

            when(configService.getConfig()).thenReturn(null);
            when(changeService.getActiveChanges()).thenReturn(List.of());
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of());

            String context = service.assembleContext();

            assertTrue(context.startsWith("# OpenSpec Explore Context\n"));
            assertTrue(context.contains("## Detected AI Tools\n"));
            assertTrue(context.contains("## Active Changes\n"));
        }
    }

    @Nested
    class FallbackWalks {

        @TempDir
        Path tempDir;

        private void write(Path file, String content) throws IOException {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        }

        @Test
        void changeWalkIncludesNestedMarkdownAndSkipsDotNamesAndOtherFiles() throws IOException {
            Path change = tempDir.resolve("my-change");
            write(change.resolve("tasks.md"), "t");
            write(change.resolve("proposal.md"), "p");
            write(change.resolve("design.md"), "d");
            write(change.resolve("specs/a/b/spec.md"), "delta");
            write(change.resolve("research/x/y.md"), "notes");
            write(change.resolve(".openspec.yaml"), "schema: spec-driven");
            write(change.resolve(".hidden/secret.md"), "hidden");
            write(change.resolve("specs/.draft.md"), "draft");
            write(change.resolve("diagram.png"), "png");

            List<String> labels = ExploreContextService.artifactsFromWalk(change).stream()
                    .map(ExploreContextService.Artifact::label).toList();

            assertEquals(List.of("proposal.md", "design.md", "tasks.md", "research/x/y.md", "specs/a/b/spec.md"),
                    labels);
        }

        @Test
        void changeWalkOfAMissingDirectoryIsEmpty() {
            assertEquals(List.of(), ExploreContextService.artifactsFromWalk(tempDir.resolve("gone")));
        }

        @Test
        void specWalkNamesCapabilitiesByFolderPathLikeUpstream() throws IOException {
            Path specs = tempDir.resolve("specs");
            write(specs.resolve("billing/spec.md"), "s");
            write(specs.resolve("identity/spec.md"), "s");
            write(specs.resolve("identity/user-auth/spec.md"), "s");
            write(specs.resolve("spec.md"), "directly in specs/, ignored");
            write(specs.resolve(".archive/old/spec.md"), "dot-folder, skipped");
            write(specs.resolve("empty/README.md"), "no spec.md, not a capability");

            assertEquals(List.of("billing", "identity", "identity/user-auth"),
                    ExploreContextService.capabilitiesFromWalk(specs));
        }
    }
}

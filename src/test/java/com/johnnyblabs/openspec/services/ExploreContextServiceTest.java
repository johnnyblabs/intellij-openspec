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

            assertTrue(context.contains("Version: 1.2.0"));
            assertTrue(context.contains("Schema: spec-driven"));
            assertTrue(context.contains("> An IntelliJ plugin for spec-driven development."));
            assertTrue(context.contains("**Rules:**"));
            assertTrue(context.contains("**services**: All services SHALL be project services"));
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
            assertTrue(context.contains("**proposal:**"));
            assertTrue(context.contains("This is the full proposal."));
            assertTrue(context.contains("**design:**"));
            assertTrue(context.contains("This is the full design."));
            assertTrue(context.contains("**tasks:**"));
            assertTrue(context.contains("- [ ] 1.1 Do something"));
            assertTrue(context.contains("**delta spec (validation):**"));
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
            assertTrue(context.contains("**proposal:**"));
            assertTrue(context.contains("Just a proposal."));
            assertFalse(context.contains("**design:**"));
            assertFalse(context.contains("**tasks:**"));
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
}

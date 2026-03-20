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
    class FullProjectState {

        @TempDir
        Path tempDir;

        @Test
        void assemblesContext_withAllSectionsPopulated() throws IOException {
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);
            when(project.getBasePath()).thenReturn(tempDir.toString());

            // Config
            OpenSpecConfig config = new OpenSpecConfig();
            config.setVersion("0.3.0");
            config.setSchema("spec-driven");
            when(configService.getConfig()).thenReturn(config);

            // AI tools
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of("Claude Code", "GitHub Copilot"));

            // Active changes
            Change change = new Change("enhanced-explore", tempDir.resolve("openspec/changes/enhanced-explore").toString());
            ChangeMetadata meta = new ChangeMetadata();
            meta.setSchema("spec-driven");
            change.setMetadata(meta);
            when(changeService.getActiveChanges()).thenReturn(List.of(change));

            // Create proposal file
            Path proposalDir = tempDir.resolve("openspec/changes/enhanced-explore");
            Files.createDirectories(proposalDir);
            Files.writeString(proposalDir.resolve("proposal.md"), "This is a test proposal for enhanced explore.");

            // Create spec domains
            Path specDomain = tempDir.resolve("openspec/specs/explore-panel");
            Files.createDirectories(specDomain);
            Files.writeString(specDomain.resolve("spec.md"), "# Explore Panel Spec");

            String context = service.assembleContext();

            assertNotNull(context);
            assertTrue(context.contains("# OpenSpec Explore Context"));
            assertTrue(context.contains("## Project Config"));
            assertTrue(context.contains("Version: 0.3.0"));
            assertTrue(context.contains("Schema: spec-driven"));
            assertTrue(context.contains("## Detected AI Tools"));
            assertTrue(context.contains("Claude Code [CLI]"));
            assertTrue(context.contains("GitHub Copilot [IDE_PANEL]"));
            assertTrue(context.contains("## Active Changes"));
            assertTrue(context.contains("**enhanced-explore**"));
            assertTrue(context.contains("This is a test proposal"));
            assertTrue(context.contains("## Specs"));
            assertTrue(context.contains("explore-panel"));
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
    class ProposalTruncation {

        @TempDir
        Path tempDir;

        @Test
        void truncatesProposalSummaryAt500Characters() throws IOException {
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);
            when(project.getBasePath()).thenReturn(tempDir.toString());

            when(configService.getConfig()).thenReturn(null);
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of());

            // Create a change with a long proposal
            Change change = new Change("long-proposal", tempDir.resolve("openspec/changes/long-proposal").toString());
            when(changeService.getActiveChanges()).thenReturn(List.of(change));

            Path proposalDir = tempDir.resolve("openspec/changes/long-proposal");
            Files.createDirectories(proposalDir);

            // Create a proposal longer than 500 chars
            String longContent = "A".repeat(600);
            Files.writeString(proposalDir.resolve("proposal.md"), longContent);

            String context = service.assembleContext();

            // The assembled context should contain the truncated summary (500 chars + "...")
            assertTrue(context.contains("A".repeat(500) + "..."));
            assertFalse(context.contains("A".repeat(501)));
        }

        @Test
        void doesNotTruncateShortProposals() throws IOException {
            when(project.getService(ConfigService.class)).thenReturn(configService);
            when(project.getService(ChangeService.class)).thenReturn(changeService);
            when(project.getService(AiToolDetectionService.class)).thenReturn(aiToolDetectionService);
            when(project.getBasePath()).thenReturn(tempDir.toString());

            when(configService.getConfig()).thenReturn(null);
            when(aiToolDetectionService.getDetectedTools()).thenReturn(List.of());

            Change change = new Change("short-proposal", tempDir.resolve("openspec/changes/short-proposal").toString());
            when(changeService.getActiveChanges()).thenReturn(List.of(change));

            Path proposalDir = tempDir.resolve("openspec/changes/short-proposal");
            Files.createDirectories(proposalDir);
            Files.writeString(proposalDir.resolve("proposal.md"), "Short proposal content.");

            String context = service.assembleContext();

            assertTrue(context.contains("Short proposal content."));
            assertFalse(context.contains("..."));
        }
    }
}

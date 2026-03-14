package com.johnnyblabs.openspec.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScaffoldingDetectionServiceTest {

    private ScaffoldingDetectionService service;

    @BeforeEach
    void setUp() {
        service = new ScaffoldingDetectionService(null);
    }

    @Test
    void scaffoldedDesign_isDetected() {
        String content = """
                # Design: my-change

                ## Approach

                <!-- Describe the technical approach -->

                ## Components Affected

                <!-- List affected components -->

                ## Trade-offs

                <!-- Document any trade-offs -->
                """;
        assertTrue(service.isScaffoldingContent(content));
    }

    @Test
    void scaffoldedTasks_isDetected() {
        String content = """
                # Tasks: my-change

                ## Implementation Tasks

                - [ ] Task 1
                - [ ] Task 2
                - [ ] Task 3

                ## Testing Tasks

                - [ ] Write unit tests
                - [ ] Integration testing
                """;
        assertTrue(service.isScaffoldingContent(content));
    }

    @Test
    void realDesignContent_isNotScaffolding() {
        String content = """
                ## Context

                The WorkflowActionPanel already uses a CardLayout to swap between panels.
                After clipboard delivery, it currently shows a small link that is easy to miss.

                ## Goals / Non-Goals

                **Goals:**
                - User immediately sees what happened after clicking Generate
                - Guidance is tool-aware
                """;
        assertFalse(service.isScaffoldingContent(content));
    }

    @Test
    void realTasksContent_isNotScaffolding() {
        String content = """
                ## 1. Setup

                - [ ] 1.1 Create ScaffoldingDetectionService
                - [ ] 1.2 Implement content analysis

                ## 2. Integration

                - [ ] 2.1 Wire into ArtifactOrchestrationService
                """;
        assertFalse(service.isScaffoldingContent(content));
    }

    @Test
    void emptyContent_isScaffolding() {
        assertTrue(service.isScaffoldingContent(""));
    }

    @Test
    void nullContent_isScaffolding() {
        assertTrue(service.isScaffoldingContent(null));
    }

    @Test
    void blankContent_isScaffolding() {
        assertTrue(service.isScaffoldingContent("   \n  \n  "));
    }

    @Test
    void shortButRealContent_isNotScaffolding() {
        String content = """
                ## Approach

                Use REST API with token auth.
                """;
        assertFalse(service.isScaffoldingContent(content));
    }

    @Test
    void headingsOnly_isScaffolding() {
        String content = """
                # Title

                ## Section One

                ## Section Two
                """;
        assertTrue(service.isScaffoldingContent(content));
    }

    @Test
    void scaffoldedTasksWithLeadingSpace_isDetected() {
        String content = " # Tasks: ensure the code uses java 21 symatics\n" +
                "\n" +
                "## Implementation Tasks\n" +
                "\n" +
                "- [ ] Task 1\n" +
                "- [ ] Task 2\n" +
                "- [ ] Task 3\n" +
                "\n" +
                "## Testing Tasks\n" +
                "\n" +
                "- [ ] Write unit tests\n" +
                "- [ ] Integration testing\n";
        assertTrue(service.isScaffoldingContent(content));
    }
}

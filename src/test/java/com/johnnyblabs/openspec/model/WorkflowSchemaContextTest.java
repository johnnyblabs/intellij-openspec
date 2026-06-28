package com.johnnyblabs.openspec.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowSchemaContextTest {

    @Test
    void fallbackIsSpecDrivenRepoLocal() {
        WorkflowSchemaContext ctx = WorkflowSchemaContext.fallback("1.2.0", null);

        assertEquals(WorkflowSchemaContext.DEFAULT_SCHEMA, ctx.schemaName());
        assertEquals(WorkflowSchemaContext.DEFAULT_MODE, ctx.mode());
        assertEquals(WorkflowSchemaContext.DEFAULT_SOURCE_OF_TRUTH, ctx.sourceOfTruth());
        assertTrue(ctx.isSpecDrivenRepoLocal());
        assertFalse(ctx.isNonDefaultMode());
        assertFalse(ctx.cliActionContextAvailable(),
                "fallback means actionContext was not available");
    }

    @Test
    void fallbackKeepsVersionAxesSeparate() {
        // config-format version and CLI version are distinct inputs, not conflated.
        WorkflowSchemaContext ctx = WorkflowSchemaContext.fallback("1.2.0", "1.4.0");

        assertEquals("1.2.0", ctx.configFormatVersion());
        assertEquals("1.4.0", ctx.cliVersion());
    }

    @Test
    void nonDefaultModeIsReflected() {
        WorkflowSchemaContext ctx = new WorkflowSchemaContext(
                "workspace-planning", "workspace-planning", "workspace",
                List.of("/repo/a"), "1.4.0", "1.2.0", true);

        assertTrue(ctx.isNonDefaultMode());
        assertFalse(ctx.isSpecDrivenRepoLocal());
        assertTrue(ctx.cliActionContextAvailable());
    }

    @Test
    void specDrivenRepoLocalFromCliIsNotNonDefault() {
        WorkflowSchemaContext ctx = new WorkflowSchemaContext(
                "spec-driven", "repo-local", "repo",
                List.of("/repo"), "1.4.0", "1.2.0", true);

        assertTrue(ctx.isSpecDrivenRepoLocal());
        assertFalse(ctx.isNonDefaultMode());
    }

    @Test
    void allowedEditRootsNullBecomesEmptyAndImmutable() {
        WorkflowSchemaContext ctx = new WorkflowSchemaContext(
                "spec-driven", "repo-local", "repo", null, null, null, false);

        assertNotNull(ctx.allowedEditRoots());
        assertTrue(ctx.allowedEditRoots().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.allowedEditRoots().add("/x"),
                "allowedEditRoots must be an immutable copy");
    }
}

package com.johnnyblabs.openspec.scaffolding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Initialising with the CLI passes the detected tools, or {@code none}, so {@code init} never prompts. */
class ScaffoldingInitArgsTest {

    @Test
    void theDetectedToolsArePassedAsOneCommaList() {
        assertEquals(List.of("init", "--tools", "claude,cursor"),
                ScaffoldingService.initArgs(List.of("claude", "cursor")).requested());
    }

    @Test
    void noDetectedToolsPassesNone() {
        assertEquals(List.of("init", "--tools", "none"), ScaffoldingService.initArgs(List.of()).requested());
    }
}

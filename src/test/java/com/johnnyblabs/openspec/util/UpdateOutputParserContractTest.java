package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the update legacy-cleanup parser against REAL {@code openspec update}
 * output captured from CLI 1.4.1 (a project initialized by CLI 1.3.1 with the junie tool;
 * isolated environment). The regenerated variant is the post-`--force` state — the CLI
 * refreshed the tool and re-created the files it flags, which is the regeneration loop the
 * flow must recognize. If the CLI rewords the block, re-capture and fix failures.
 *
 * <p>The {@code ...V16} nest asserts the {@code 1.6.0/} twins (same 1.3.1-initialized
 * recipe, updated by CLI 1.6.0). Generation differences pinned there: a
 * {@code Migrated: custom profile ...} preamble ahead of the block, and the post-force
 * regenerated list staying at four files — 1.6's migrated "custom profile" preserves the
 * old workflow set instead of adding {@code opsx-sync.md}. The clean capture uses the
 * claude tool (skills-only delivery at 1.6; junie still ships legacy command files that
 * update immediately flags). See {@code fixtures/cli/README.md}.
 */
class UpdateOutputParserContractTest {

    private static String loadFixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = UpdateOutputParserContractTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    @Nested
    class RealOutputContract {

        @Test
        void parsesPendingBlock() {
            List<String> files = UpdateOutputParser.parseLegacyCleanup(loadFixture("update-legacy-pending.txt"));
            assertEquals(List.of(
                    ".junie/commands/opsx-apply.md",
                    ".junie/commands/opsx-archive.md",
                    ".junie/commands/opsx-explore.md",
                    ".junie/commands/opsx-propose.md"), files);
        }

        @Test
        void parsesRegeneratedPendingBlock() {
            List<String> files = UpdateOutputParser.parseLegacyCleanup(loadFixture("update-legacy-pending-regenerated.txt"));
            assertEquals(5, files.size(), "post-force state lists five files (opsx-sync.md regenerated)");
            assertTrue(files.contains(".junie/commands/opsx-sync.md"));
        }

        @Test
        void cleanOutputYieldsEmpty() {
            assertTrue(UpdateOutputParser.parseLegacyCleanup(loadFixture("update-clean.txt")).isEmpty());
        }
    }

    /** 1.6-generation twin of {@link RealOutputContract}. */
    @Nested
    class RealOutputContractV16 {

        @Test
        void parsesPendingBlockDespiteMigratedPreamble() {
            List<String> files = UpdateOutputParser.parseLegacyCleanup(
                    loadFixture("1.6.0/update-legacy-pending.txt"));
            assertEquals(List.of(
                    ".junie/commands/opsx-apply.md",
                    ".junie/commands/opsx-archive.md",
                    ".junie/commands/opsx-explore.md",
                    ".junie/commands/opsx-propose.md"), files);
        }

        @Test
        void parsesRegeneratedPendingBlock() {
            List<String> files = UpdateOutputParser.parseLegacyCleanup(
                    loadFixture("1.6.0/update-legacy-pending-regenerated.txt"));
            assertEquals(List.of(
                    ".junie/commands/opsx-apply.md",
                    ".junie/commands/opsx-archive.md",
                    ".junie/commands/opsx-explore.md",
                    ".junie/commands/opsx-propose.md"), files,
                    "1.6 post-force regeneration keeps the migrated custom-profile set (no opsx-sync.md)");
        }

        @Test
        void cleanOutputWithMigratedPreambleYieldsEmpty() {
            assertTrue(UpdateOutputParser.parseLegacyCleanup(
                            loadFixture("1.6.0/update-clean.txt")).isEmpty(),
                    "the Migrated: preamble alone must not read as a pending block");
        }
    }

    @Nested
    class Degradation {

        @Test
        void noBlock_returnsEmpty() {
            assertTrue(UpdateOutputParser.parseLegacyCleanup("✓ All 5 tool(s) up to date (v1.4.1)").isEmpty());
            assertTrue(UpdateOutputParser.parseLegacyCleanup("").isEmpty());
            assertTrue(UpdateOutputParser.parseLegacyCleanup(null).isEmpty());
        }

        @Test
        void headerWithoutRecognizableEntries_returnsEmpty() {
            String odd = "Files to remove\nSomething entirely unexpected here\n";
            assertTrue(UpdateOutputParser.parseLegacyCleanup(odd).isEmpty(),
                    "unknown structure after the header must not invent files");
        }

        @Test
        void bulletRunEndsAtFirstNonBullet() {
            String partial = """
                    Files to remove
                    No user content to preserve:
                      • .junie/commands/opsx-apply.md
                    ⚠ Run with --force to auto-cleanup legacy files, or run interactively.
                      • .junie/commands/opsx-sync.md
                    """;
            assertEquals(List.of(".junie/commands/opsx-apply.md"),
                    UpdateOutputParser.parseLegacyCleanup(partial),
                    "entries after the block ends are never picked up — the set shrinks, never grows");
        }

        @Test
        void midLineFilesToRemoveDoesNotTrigger() {
            String prose = "The output mentioned Files to remove somewhere in prose\n  • not/a/real/entry.md\n";
            assertTrue(UpdateOutputParser.parseLegacyCleanup(prose).isEmpty(),
                    "the header must be its own line");
        }
    }
}

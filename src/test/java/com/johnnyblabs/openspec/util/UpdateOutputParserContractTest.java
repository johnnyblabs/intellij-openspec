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
        void colouredOutputParsesLikeThePlainCapture() {
            String ansi = loadFixture("1.14.3/errors/update-legacy-pending-ansi.stdout");
            String plain = loadFixture("1.14.3/errors/update-legacy-pending-plain.stdout");
            // The escape codes sit on the anchored header line itself, so unstripped matching misses the block.
            assertTrue(ansi.lines().anyMatch(l -> l.contains("\u001B[") && l.contains("Files to remove")),
                    "capture precondition: the header line is coloured");
            UpdateOutputParser.LegacyCleanup coloured = UpdateOutputParser.parseLegacyCleanupBlock(ansi);
            UpdateOutputParser.LegacyCleanup expected = UpdateOutputParser.parseLegacyCleanupBlock(plain);
            assertEquals(3, expected.files().size());
            assertEquals(expected.files(), coloured.files());
            assertEquals(expected.guidance(), coloured.guidance());
        }

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

    /**
     * 1.7-generation twin of {@link RealOutputContract}. Two generation differences pinned here: 1.7
     * dropped the {@code Migrated: custom profile} preamble from the clean-update output entirely, and
     * {@code init --tools junie} now scaffolds six legacy {@code opsx-*} command files (the four 1.6
     * ones plus {@code opsx-sync.md} and {@code opsx-update.md}) — so both the pending and the
     * post-force regenerated "Files to remove" lists carry six entries (1.6's regenerated list stayed
     * at four because its migrated custom profile didn't regenerate {@code opsx-sync}).
     */
    @Nested
    class RealOutputContractV17 {

        @Test
        void parsesPendingBlockOfSixFiles() {
            List<String> files = UpdateOutputParser.parseLegacyCleanup(
                    loadFixture("1.7.0/update-legacy-pending.txt"));
            assertEquals(List.of(
                    ".junie/commands/opsx-apply.md",
                    ".junie/commands/opsx-archive.md",
                    ".junie/commands/opsx-explore.md",
                    ".junie/commands/opsx-propose.md",
                    ".junie/commands/opsx-sync.md",
                    ".junie/commands/opsx-update.md"), files,
                    "1.7 init --tools junie scaffolds six legacy opsx-* files");
        }

        @Test
        void parsesRegeneratedPendingBlockOfSixFiles() {
            List<String> files = UpdateOutputParser.parseLegacyCleanup(
                    loadFixture("1.7.0/update-legacy-pending-regenerated.txt"));
            assertEquals(6, files.size(),
                    "1.7 post-force regeneration re-flags all six legacy junie command files");
            assertTrue(files.contains(".junie/commands/opsx-sync.md"));
            assertTrue(files.contains(".junie/commands/opsx-update.md"));
        }

        @Test
        void cleanOutputWithoutMigratedPreambleYieldsEmpty() {
            assertTrue(UpdateOutputParser.parseLegacyCleanup(
                            loadFixture("1.7.0/update-clean.txt")).isEmpty(),
                    "1.7's clean update (no migration preamble) must not read as a pending block");
        }
    }

    /**
     * The guidance line across every captured wording. 1.6.0/1.7.0 (Junie) and 1.13.2 (Cursor) print
     * "No user content to preserve:"; 1.14.0 reworded it to a back-up warning, which the old parser rejected,
     * so the cleanup flow never appeared on 1.14.x. Expected values are read from each capture's own text.
     */
    @Nested
    class GuidanceLineAcrossGenerations {

        /** The capture's lines from just after "Files to remove" to the ⚠ footer: [guidance, bullet...]. */
        private List<String> blockLines(String capture) {
            List<String> lines = capture.lines().toList();
            int header = lines.indexOf("Files to remove");
            assertTrue(header >= 0, "capture must contain the block");
            List<String> block = new java.util.ArrayList<>();
            for (String line : lines.subList(header + 1, lines.size())) {
                if (line.startsWith("⚠") || line.isBlank()) break;
                block.add(line);
            }
            return block;
        }

        private void assertParsesAsCaptured(String fixture) {
            String capture = loadFixture(fixture);
            List<String> block = blockLines(capture);
            String expectedGuidance = block.get(0);
            List<String> expectedFiles = block.subList(1, block.size()).stream()
                    .map(l -> l.replaceFirst("^\\s*•\\s+", "")).toList();

            UpdateOutputParser.LegacyCleanup parsed = UpdateOutputParser.parseLegacyCleanupBlock(capture);

            assertEquals(expectedFiles, parsed.files(), fixture);
            assertEquals(expectedGuidance, parsed.guidance(), fixture);
        }

        @Test
        void v1_6_0() {
            assertParsesAsCaptured("1.6.0/update-legacy-pending.txt");
            assertTrue(UpdateOutputParser.parseLegacyCleanupBlock(loadFixture("1.6.0/update-legacy-pending.txt"))
                    .certifiesNoUserContent());
        }

        @Test
        void v1_7_0() {
            assertParsesAsCaptured("1.7.0/update-legacy-pending.txt");
        }

        @Test
        void v1_13_2() {
            assertParsesAsCaptured("1.13.2/update-legacy-pending.txt");
            assertTrue(UpdateOutputParser.parseLegacyCleanupBlock(loadFixture("1.13.2/update-legacy-pending.txt"))
                    .certifiesNoUserContent(), "up to 1.13 the CLI certifies there is no user content");
        }

        @Test
        void v1_14_3_rewordedGuidanceStillParses() {
            assertParsesAsCaptured("1.14.3/update-legacy-pending.txt");
            UpdateOutputParser.LegacyCleanup parsed =
                    UpdateOutputParser.parseLegacyCleanupBlock(loadFixture("1.14.3/update-legacy-pending.txt"));
            assertEquals(3, parsed.files().size());
            assertFalse(parsed.certifiesNoUserContent(), "1.14 warns to back up custom content instead");
            assertTrue(parsed.guidance().contains("Back up any custom content"));
        }

        @Test
        void tooMuchProseBeforeTheBulletsIsNotTheBlock() {
            String unexpected = """
                    Files to remove
                    one line of prose
                    another line of prose
                    and a third
                      • looks/like/an/entry.md
                    """;
            assertTrue(UpdateOutputParser.parseLegacyCleanupBlock(unexpected).isEmpty(),
                    "more than a short guidance before the bullets means unknown structure — offer nothing");
        }
    }

    /** What a 2.x applied update kept, left or replaced, read from the real runs ({@code <v>/update-migration/}). */
    @Nested
    class KeptReports {

        private static String applied(String version, String name) throws java.io.IOException {
            return java.nio.file.Files.readString(FixtureVersions.CLI.resolve(version + "/update-migration/" + name
                    + "/applied.txt"));
        }

        private static final List<String> RELEASES = List.of("2.0.0", "2.1.0", "2.1.1");
        private static final String MOVE = "Move anything worth keeping into the skill, then delete by hand.";

        @Test
        void anEditedCommandFileIsKeptOnEveryRelease() throws java.io.IOException {
            for (String v : RELEASES) {
                List<UpdateOutputParser.KeptReport> reports = UpdateOutputParser.parseKept(applied(v, "edited"));
                var commands = reports.stream().filter(r -> r.header().contains("command file")).toList();
                assertEquals(1, commands.size(), v + " " + reports);
                assertEquals(UpdateOutputParser.KeptKind.KEPT, commands.get(0).kind());
                assertEquals(List.of(".claude/commands/opsx/explore.md"), commands.get(0).paths(), v);
                assertEquals(MOVE, commands.get(0).guidance(), v);
            }
        }

        @Test
        void twoZeroZeroAlsoReportsTheSkillFoldersItKept() throws java.io.IOException {
            var folders = UpdateOutputParser.parseKept(applied("2.0.0", "edited")).stream()
                    .filter(r -> r.header().startsWith("Kept 2 skill folders")).toList();
            assertEquals(1, folders.size());
            assertEquals(List.of(".claude/skills/openspec-explore", ".cursor/skills/openspec-explore"), folders.get(0).paths());
            assertTrue(folders.get(0).guidance().startsWith("Nothing was overwritten."));
            for (String v : List.of("2.1.0", "2.1.1")) {
                assertTrue(UpdateOutputParser.parseKept(applied(v, "edited")).stream()
                        .noneMatch(r -> r.header().contains("skill folder")), v + ": 2.1 reports nothing about the copies");
            }
        }

        @Test
        void twoZeroZeroReportsReplacedEditsAndASkillKeptAsTheUsersOwn() throws java.io.IOException {
            var replaced = UpdateOutputParser.parseKept(applied("2.0.0", "shared-folder"));
            assertEquals(List.of(UpdateOutputParser.KeptKind.REPLACED), replaced.stream().map(UpdateOutputParser.KeptReport::kind).toList());
            assertEquals(List.of(".agents/skills/openspec-explore/SKILL.md"), replaced.get(0).paths());
            var own = UpdateOutputParser.parseKept(applied("2.0.0", "shared-own-skill")).stream()
                    .filter(r -> r.header().contains("as your own")).toList();
            assertEquals(List.of(".agents/skills/openspec-explore"), own.get(0).paths());
            assertEquals(UpdateOutputParser.KeptKind.KEPT, own.get(0).kind());
        }

        @Test
        void commandFilesThatCouldNotBeRemovedAreLeftInPlace() throws java.io.IOException {
            for (String v : RELEASES) {
                var left = UpdateOutputParser.parseKept(applied(v, "command-left-in-place"));
                assertEquals(1, left.size(), v + " " + left);
                assertEquals(UpdateOutputParser.KeptKind.LEFT, left.get(0).kind());
                assertEquals(6, left.get(0).paths().size(), v);
                assertTrue(left.get(0).paths().stream().allMatch(p -> p.startsWith(".claude/commands/opsx/")), v);
                assertEquals("Check the file permissions or symbolic links, then delete by hand.", left.get(0).guidance());
            }
        }

        @Test
        void aRetiredToolsKeptFilesAreIndentedAndCarryNoGuidanceLine() throws java.io.IOException {
            for (String v : RELEASES) {
                var kept = UpdateOutputParser.parseKept(applied(v, "retired-tool-edited"));
                assertEquals(1, kept.size(), v + " " + kept);
                assertEquals(List.of(".amazonq/prompts/opsx-apply.md"), kept.get(0).paths(), v);
                assertNull(kept.get(0).guidance(), "the line after the paths is unrelated output, " + v);
            }
        }

        @Test
        void runsThatKeepNothingReportNothing() throws java.io.IOException {
            for (String v : RELEASES) {
                assertEquals(List.of(), UpdateOutputParser.parseKept(applied(v, "unedited")), v);
            }
            int seen = 0;
            for (String v : List.of("1.6.0", "1.7.0", "1.13.2", "1.14.3")) {
                for (String name : List.of("update-clean.txt", "update-legacy-pending.txt",
                        "update-legacy-pending-regenerated.txt")) {
                    java.nio.file.Path f = FixtureVersions.CLI.resolve(v).resolve(name);
                    if (!java.nio.file.Files.exists(f)) continue;
                    seen++;
                    assertEquals(List.of(), UpdateOutputParser.parseKept(java.nio.file.Files.readString(f)), v + " " + name);
                }
            }
            assertTrue(seen >= 6, "sanity: 1.x update captures " + seen);
        }

        /**
         * The two home-folder prompt reports print only after the legacy-cleanup flow, which a non-interactive run doesn't
         * reach, so no capture has them. These lines are built from 2.1.1's own format strings in
         * {@code dist/core/update.js}: {@code reportEditedGlobalPrompts} and the "Left … prompt file(s)" line after
         * {@code performImmediateLegacyCleanup}.
         */
        @Test
        void theHomeFolderPromptReportsAsTheirSourcePrintsThem() {
            String edited = "Kept 2 prompt files in your home folder that differ from what OpenSpec generated (likely edited):\n"
                    + "  /fixture/home/.codex/prompts/openspec-apply.md\n"
                    + "  /fixture/home/.codex/prompts/openspec-archive.md\n"
                    + MOVE + "\n\n";
            var kept = UpdateOutputParser.parseKept(edited);
            assertEquals(1, kept.size());
            assertEquals(List.of("/fixture/home/.codex/prompts/openspec-apply.md",
                    "/fixture/home/.codex/prompts/openspec-archive.md"), kept.get(0).paths());
            assertEquals(MOVE, kept.get(0).guidance());

            String left = "Left 3 OpenSpec prompt file(s) in your home folder: other projects may still use them. "
                    + "Run \"openspec update --force\" to remove them.\n";
            var leftReports = UpdateOutputParser.parseKept(left);
            assertEquals(1, leftReports.size());
            assertEquals(UpdateOutputParser.KeptKind.LEFT, leftReports.get(0).kind());
            assertEquals(List.of(), leftReports.get(0).paths(), "this report names no files");
            assertTrue(leftReports.get(0).guidance().startsWith("other projects may still use them."));
        }

        /** The singular form, and the colours the CLI prints when a wrapper forces them, as {@code chalk} writes them. */
        @Test
        void theSingularHomePromptFormAndColouredOutputAreRead() {
            String one = "\u001b[33mKept 1 prompt file in your home folder that differs from what OpenSpec generated"
                    + " (likely edited):\u001b[39m\n"
                    + "\u001b[2m  /fixture/home/.codex/prompts/openspec-apply.md\u001b[22m\n"
                    + "\u001b[2m" + MOVE + "\u001b[22m\n";
            var kept = UpdateOutputParser.parseKept(one);
            assertEquals(1, kept.size());
            assertEquals(List.of("/fixture/home/.codex/prompts/openspec-apply.md"), kept.get(0).paths());
            assertEquals(MOVE, kept.get(0).guidance());
        }

        @Test
        void aTruncatedHeaderYieldsNothing() {
            assertEquals(List.of(), UpdateOutputParser.parseKept(
                    "Kept 1 command file that does not match what OpenSpec generated\n  .claude/commands/opsx/explore.md\n"));
            assertEquals(List.of(), UpdateOutputParser.parseKept(
                    "Kept 1 command file that does not match what OpenSpec generated (likely edited):\nno indented path\n"));
        }
    }
}

package com.johnnyblabs.openspec.model;

import com.johnnyblabs.openspec.util.CliOutputParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one-off regeneration note: without one, prompts are byte-identical to before; with one, it is appended once,
 * after the dependencies and before the multi-file block, and a noted single-file Direct API prompt carries the current
 * file. A Fast-Forward request is its own section after the dependencies and before any note. Every artifact
 * instruction is parsed from captured CLI output.
 */
class RegenerationNotePromptTest {

    /** Every captured `openspec instructions <artifact> --json` fixture. */
    private static final List<String> FIXTURES = List.of(
            "instructions-proposal.json", "instructions-specs.json", "instructions-tasks.json",
            "1.6.0/instructions-proposal.json", "1.6.0/instructions-specs.json", "1.6.0/instructions-tasks.json",
            "1.7.0/instructions-proposal.json", "1.7.0/instructions-specs.json", "1.7.0/instructions-tasks.json",
            "1.11.0/instructions-tasks-specs-done.json", "1.14.3/additive-shapes/instructions-proposal.json",
            "1.15.0/instructions/instructions-design.json", "1.15.0/instructions/instructions-specs.json");

    private static ArtifactInstruction captured(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = RegenerationNotePromptTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            ArtifactInstruction parsed = CliOutputParser.parseArtifactInstruction(
                    new String(is.readAllBytes(), StandardCharsets.UTF_8));
            assertNotNull(parsed, name);
            return parsed;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The Direct API prompt as it was defined before notes: the plain prompt, plus the multi-file block for a glob. */
    private static String directApiPromptWithoutNotes(ArtifactInstruction inst) {
        String prompt = inst.buildPrompt();
        if (!MultiFileArtifact.isGlob(inst.outputPath())) return prompt;
        return prompt + MultiFileArtifact.directApiPromptSection(
                inst.changeDir() != null ? Path.of(inst.changeDir()) : null, inst.outputPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " \n\t "})
    void aBlankNoteLeavesEveryCapturedPromptByteIdentical(String blank) {
        for (String name : FIXTURES) {
            ArtifactInstruction inst = captured(name);
            assertEquals(inst.buildPrompt(), inst.buildPrompt(blank), name);
            assertEquals(directApiPromptWithoutNotes(inst), inst.buildDirectApiPrompt(blank), name);
        }
    }

    @Test
    void noNoteLeavesEveryCapturedPromptByteIdentical() {
        for (String name : FIXTURES) {
            ArtifactInstruction inst = captured(name);
            assertEquals(inst.buildPrompt(), inst.buildPrompt(null), name);
            assertEquals(directApiPromptWithoutNotes(inst), inst.buildDirectApiPrompt(null), name);
            assertEquals(directApiPromptWithoutNotes(inst), inst.buildDirectApiPrompt(), name);
        }
    }

    @Test
    void aNoteIsAppendedOnceAfterTheDependencies() {
        for (String name : FIXTURES) {
            ArtifactInstruction inst = captured(name);
            String prompt = inst.buildPrompt("  drop the caching section\n");

            assertEquals(inst.buildPrompt() + "\n\n---\n\n" + ArtifactInstruction.NOTE_HEADING
                    + "\ndrop the caching section\n", prompt, name);
            assertEquals(1, count(prompt, ArtifactInstruction.NOTE_HEADING), name);
        }
    }

    @Test
    void aGlobArtifactsNoteComesBeforeTheMultiFileBlock() {
        ArtifactInstruction specs = captured("1.7.0/instructions-specs.json");
        assertTrue(MultiFileArtifact.isGlob(specs.outputPath()), specs.outputPath());

        String prompt = specs.buildDirectApiPrompt("add a billing capability");

        String noted = specs.buildPrompt("add a billing capability");
        assertTrue(prompt.startsWith(noted), "the note follows the dependencies");
        assertEquals(directApiPromptWithoutNotes(specs).substring(specs.buildPrompt().length()),
                prompt.substring(noted.length()), "the multi-file block is unchanged and last");
        assertFalse(prompt.contains(ArtifactInstruction.CURRENT_VERSION_HEADING),
                "a glob artifact's existing files are already in the multi-file block");
    }

    @Test
    void aNotedSingleFileDirectApiPromptCarriesTheCurrentFile(@TempDir Path changeDir) throws IOException {
        Files.writeString(changeDir.resolve("design.md"), "# Design\n\nUse a cache.\n");
        ArtifactInstruction design = new ArtifactInstruction("add-auth", "design", changeDir.toString(), "design.md",
                "Write the design.", "# Design", List.of(), List.of());

        String noted = design.buildDirectApiPrompt("drop the caching section");
        String unnoted = design.buildDirectApiPrompt();

        assertEquals(design.buildPrompt() + "\n\n---\n\n" + ArtifactInstruction.CURRENT_VERSION_HEADING
                + "\n\n```\n# Design\n\nUse a cache.\n\n```\n"
                + "\n\n---\n\n" + ArtifactInstruction.NOTE_HEADING + "\ndrop the caching section\n", noted);
        assertFalse(unnoted.contains("Use a cache."), "without a note the current file is not sent");
        assertFalse(design.buildPrompt("drop the caching section").contains("Use a cache."),
                "clipboard and editor-tab prompts never carry it; the agent reads the file");
    }

    @Test
    void codeBlocksInTheCurrentFileStayInsideItsFence(@TempDir Path changeDir) throws IOException {
        String current = "# Design\n\n```java\nclass Cache {}\n```\n\nAlso ````md\nnested\n````\n";
        Files.writeString(changeDir.resolve("design.md"), current);
        ArtifactInstruction design = new ArtifactInstruction("add-auth", "design", changeDir.toString(), "design.md",
                "Write the design.", "# Design", List.of(), List.of());

        String noted = design.buildDirectApiPrompt("drop the caching section");

        String start = ArtifactInstruction.CURRENT_VERSION_HEADING + "\n\n`````\n";
        assertTrue(noted.contains(start + current + "\n`````\n"),
                "a five-backtick fence wraps content whose longest run is four: " + noted);
        int body = noted.indexOf(start) + start.length();
        int close = noted.indexOf("\n`````\n", body);
        assertEquals(current, noted.substring(body, close), "nothing in the content closes the fence early");
        assertTrue(noted.indexOf(ArtifactInstruction.NOTE_HEADING) > close, "the note comes after the closed fence");
    }

    @Test
    void theFenceIsThreeBackticksUnlessTheContentNeedsMore() {
        assertEquals("```", ArtifactInstruction.fenceFor("no backticks"));
        assertEquals("```", ArtifactInstruction.fenceFor("an `inline` span and ``two``"));
        assertEquals("````", ArtifactInstruction.fenceFor("```\ncode\n```"));
        assertEquals("``````", ArtifactInstruction.fenceFor("`````"));
    }

    @Test
    void theCapturedDesignInstructionOnDiskInlinesItsDependencyAndCurrentFile(@TempDir Path changeDir) throws IOException {
        ArtifactInstruction captured = captured("1.15.0/instructions/instructions-design.json");
        Files.writeString(changeDir.resolve("proposal.md"), "# Proposal\n\n## Why\n\nDemo.\n");
        Files.writeString(changeDir.resolve(captured.outputPath()), "# Design\n\nUse a cache.\n");
        ArtifactInstruction design = new ArtifactInstruction(captured.changeName(), captured.artifactId(),
                changeDir.toString(), captured.outputPath(), captured.instruction(), captured.template(),
                captured.dependencies(), captured.unlocks());

        assertTrue(design.buildPrompt().contains("Demo."), "the done proposal is inlined");
        assertEquals(design.buildPrompt(), design.buildDirectApiPrompt(" "), "blank note: byte-identical");
        String noted = design.buildDirectApiPrompt("drop the caching section");
        assertTrue(noted.startsWith(design.buildPrompt()), "the plain prompt comes first");
        int current = noted.indexOf(ArtifactInstruction.CURRENT_VERSION_HEADING);
        int note = noted.indexOf(ArtifactInstruction.NOTE_HEADING);
        assertTrue(current > noted.indexOf("Demo.") && note > current, "dependencies, then current file, then note");
        assertTrue(noted.substring(current).contains("Use a cache."));
    }

    @Test
    void aMissingCurrentFileIsSkipped(@TempDir Path changeDir) {
        ArtifactInstruction design = new ArtifactInstruction("add-auth", "design", changeDir.toString(), "design.md",
                "Write the design.", null, List.of(), List.of());

        assertEquals(design.buildPrompt("x"), design.buildDirectApiPrompt("x"));
    }

    // --- Fast-Forward request (a separate section from the regeneration note) ---

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", " \n\t "})
    void aBlankRequestLeavesEveryCapturedPromptByteIdentical(String blank) {
        for (String name : FIXTURES) {
            ArtifactInstruction inst = captured(name);
            assertEquals(inst.buildPrompt(), inst.buildPrompt(blank, null), name);
            assertEquals(inst.buildPrompt(), inst.buildPrompt(null, null), name);
            assertEquals(directApiPromptWithoutNotes(inst), inst.buildDirectApiPrompt(blank, null), name);
            assertEquals(directApiPromptWithoutNotes(inst), inst.buildDirectApiPrompt(null, null), name);
        }
    }

    @Test
    void aRequestIsAppendedOnceAfterTheDependenciesUnderItsOwnHeading() {
        for (String name : FIXTURES) {
            ArtifactInstruction inst = captured(name);
            String prompt = inst.buildPrompt("  add login with email and password\n", null);

            assertEquals(inst.buildPrompt() + "\n\n---\n\n" + ArtifactInstruction.REQUEST_HEADING
                    + "\nadd login with email and password\n", prompt, name);
            assertEquals(1, count(prompt, ArtifactInstruction.REQUEST_HEADING), name);
            assertFalse(prompt.contains(ArtifactInstruction.NOTE_HEADING), name + ": a request is not a regeneration note");
        }
    }

    @Test
    void theRequestComesBeforeTheNoteAndAMultiLineRequestIsKeptVerbatim() {
        ArtifactInstruction proposal = captured("instructions-proposal.json");
        String request = "Add login.\n\n- email and password\n- ${not a placeholder} {braces} %s";

        String prompt = proposal.buildPrompt(request, "keep it short");

        assertTrue(prompt.contains(ArtifactInstruction.REQUEST_HEADING + "\n" + request + "\n"), prompt);
        assertTrue(prompt.indexOf(ArtifactInstruction.REQUEST_HEADING) < prompt.indexOf(ArtifactInstruction.NOTE_HEADING));
        assertNotEquals(ArtifactInstruction.REQUEST_HEADING, ArtifactInstruction.NOTE_HEADING);
    }

    @Test
    void aGlobArtifactsRequestComesBeforeTheMultiFileBlock() {
        ArtifactInstruction specs = captured("1.7.0/instructions-specs.json");
        assertTrue(MultiFileArtifact.isGlob(specs.outputPath()), specs.outputPath());

        String prompt = specs.buildDirectApiPrompt("add login", null);

        String requested = specs.buildPrompt("add login", null);
        assertTrue(prompt.startsWith(requested), "the request follows the dependencies");
        assertEquals(directApiPromptWithoutNotes(specs).substring(specs.buildPrompt().length()),
                prompt.substring(requested.length()), "the multi-file block is unchanged and last");
    }

    @Test
    void aRequestAloneNeverSendsTheCurrentFile(@TempDir Path changeDir) throws IOException {
        Files.writeString(changeDir.resolve("design.md"), "# Design\n\nUse a cache.\n");
        ArtifactInstruction design = new ArtifactInstruction("add-auth", "design", changeDir.toString(), "design.md",
                "Write the design.", "# Design", List.of(), List.of());

        assertEquals(design.buildPrompt("add login", null), design.buildDirectApiPrompt("add login", null));
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
        return n;
    }
}

package com.johnnyblabs.openspec.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The multi-file format's rules: glob matching, path validation, all-or-nothing parsing, and the Direct API prompt
 * section's context. The format against a real model response is pinned separately by the live contract test.
 */
class MultiFileArtifactTest {

    private static final String SPECS = "specs/**/*.md";

    @ParameterizedTest
    @CsvSource({
            "specs/**/*.md, specs/user-auth/spec.md, true",
            "specs/**/*.md, specs/identity/user-auth/spec.md, true",
            "specs/**/*.md, specs/spec.md, false",
            "specs/**/*.md, specs/user-auth/spec.txt, false",
            "specs/**/*.md, other/user-auth/spec.md, false",
            "*.md, design.md, true",
            "*.md, docs/design.md, false",
            "spec?.md, spec1.md, true",
    })
    void globMatching(String glob, String path, boolean matches) {
        assertEquals(matches, MultiFileArtifact.matchesGlob(glob, path), glob + " vs " + path);
    }

    @Nested
    class Parse {
        @Test
        void splitsOnMarkersInOrder() throws IOException {
            String response = "<!-- file: specs/a/spec.md -->\n# A\n\nbody a\n<!-- file: specs/b/spec.md -->\n# B\n";
            List<MultiFileArtifact.FileEntry> files = MultiFileArtifact.parse(response, SPECS);
            assertEquals(List.of(new MultiFileArtifact.FileEntry("specs/a/spec.md", "# A\n\nbody a\n"),
                    new MultiFileArtifact.FileEntry("specs/b/spec.md", "# B\n")), files);
        }

        @Test
        void dropsAWrappingFenceAndPreamble() throws IOException {
            String response = "Here you go.\n\n<!--file:specs/a/spec.md-->\n```markdown\n# A\n```\n";
            assertEquals(List.of(new MultiFileArtifact.FileEntry("specs/a/spec.md", "# A\n")),
                    MultiFileArtifact.parse(response, SPECS));
        }

        @Test
        void keepsFencesInsideTheContent() throws IOException {
            String body = "# A\n\n```\nexample\n```\n\nmore";
            List<MultiFileArtifact.FileEntry> files = MultiFileArtifact.parse("<!-- file: specs/a/spec.md -->\n" + body, SPECS);
            assertEquals(body + "\n", files.getFirst().content());
        }

        @Test
        void noMarkersIsAnError() {
            IOException ex = assertThrows(IOException.class, () -> MultiFileArtifact.parse("# Just a spec", SPECS));
            assertTrue(ex.getMessage().contains("didn't name any files"), ex.getMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"/etc/spec.md", "C:/x/spec.md", "specs/../../evil.md", "specs/./a/spec.md",
                "specs\\a\\spec.md", "specs/a/spec.txt", "design.md", "specs//spec.md", "specs/*/spec.md"})
        void anyBadPathRejectsTheWholeResponse(String bad) {
            String response = "<!-- file: specs/good/spec.md -->\n# Good\n<!-- file: " + bad + " -->\n# Bad\n";
            IOException ex = assertThrows(IOException.class, () -> MultiFileArtifact.parse(response, SPECS));
            assertTrue(ex.getMessage().contains(bad) && ex.getMessage().contains("nothing was written"), ex.getMessage());
        }

        @Test
        void duplicateAndEmptyFilesAreRejected() {
            assertThrows(IOException.class, () -> MultiFileArtifact.parse(
                    "<!-- file: specs/a/spec.md -->\nx\n<!-- file: specs/a/spec.md -->\ny\n", SPECS));
            assertThrows(IOException.class, () -> MultiFileArtifact.parse(
                    "<!-- file: specs/a/spec.md -->\n\n<!-- file: specs/b/spec.md -->\ny\n", SPECS));
        }
    }

    @Nested
    class PromptSection {
        @Test
        void suppliesCapabilitiesNamedSpecsAndExistingFiles(@TempDir Path root) throws IOException {
            Path openspec = root.resolve("openspec");
            Files.createDirectories(openspec.resolve("specs/user-auth"));
            Files.createDirectories(openspec.resolve("specs/identity/sso"));
            Files.createDirectories(openspec.resolve("specs/billing"));
            Files.writeString(openspec.resolve("specs/user-auth/spec.md"), "USER-AUTH-MAIN");
            Files.writeString(openspec.resolve("specs/identity/sso/spec.md"), "SSO-MAIN");
            Files.writeString(openspec.resolve("specs/billing/spec.md"), "BILLING-MAIN");
            Path change = openspec.resolve("changes/add-x");
            Files.createDirectories(change.resolve("specs/identity/sso"));
            Files.writeString(change.resolve("proposal.md"),
                    "## Capabilities\n- `user-auth`: modified\n- `identity/sso`: modified\n- `user-auth-v2`: new\n");
            Files.writeString(change.resolve("specs/identity/sso/spec.md"), "draft");

            String section = MultiFileArtifact.directApiPromptSection(change, SPECS);

            assertTrue(section.contains("<!-- file: <path> -->"));
            assertTrue(section.contains("- `billing`\n- `identity/sso`\n- `user-auth`\n"), section);
            assertTrue(section.contains("#### openspec/specs/user-auth/spec.md\n\n```\nUSER-AUTH-MAIN\n```"), section);
            assertTrue(section.contains("SSO-MAIN"));
            assertFalse(section.contains("BILLING-MAIN"), "a main spec the proposal doesn't name is not inlined");
            assertTrue(section.contains("### Files this change already has\n\n- `specs/identity/sso/spec.md`"), section);
            assertTrue(section.contains("Write only the capabilities that have no file yet."));
        }

        @Test
        void newProjectSaysEverythingIsNew(@TempDir Path root) throws IOException {
            Path change = root.resolve("openspec/changes/first");
            Files.createDirectories(change);
            String section = MultiFileArtifact.directApiPromptSection(change, SPECS);
            assertTrue(section.contains("None yet: every capability in the proposal is new."));
            assertTrue(section.contains("### Files this change already has\n\nNone."));
        }

        @Test
        void capabilityTokenMatchIsWholeWord() {
            assertTrue(MultiFileArtifact.namesCapability("- `user-auth`: x", "user-auth"));
            assertFalse(MultiFileArtifact.namesCapability("- `user-auth-v2`: x", "user-auth"));
            assertFalse(MultiFileArtifact.namesCapability("see identity/user-auth", "user-auth"));
        }
    }
}

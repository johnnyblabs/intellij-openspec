package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.SkillSpot.Shape;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;
import com.johnnyblabs.openspec.util.SymlinkProbe;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SkillSpot#classify} reaches upstream's verdict on every captured spot shape ({@code fixtures/cli/<version>/
 * skill-spots/}), through the mapping {@link SkillSpot} states. The shapes Windows hosts actually have (copies and link
 * text) need no link support and run on every host; the link shapes run where a directory link can be made, are
 * skipped with the reason on Windows without link support, and fail on any other host that can't make one.
 */
class SkillFolderShapeTest {

    static final Set<String> REQUIRED = Set.of("2.0.0", "2.1.1", "2.2.0");
    static final String SPOT = ".claude/skills/openspec-explore";
    static final String SHARED = ".agents/skills/openspec-explore";

    @TempDir
    Path tmp;

    /** Upstream's verdict to the plugin's shape. A link splits on whether its target exists. */
    static Set<Shape> expected(String verdict, Path project) {
        return switch (verdict) {
            case "link" -> Set.of(Files.isDirectory(project.resolve(SHARED)) ? Shape.LINK : Shape.BROKEN);
            case "released" -> Set.of(Files.isRegularFile(project.resolve(SPOT), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    ? Shape.RELEASED : Shape.COPY);
            case "copy" -> Set.of(Shape.COPY);
            // A folder holding a SKILL.md is a copy here (detection and lookup ignore content); any other "user" stays.
            case "user" -> Set.of(Files.isRegularFile(project.resolve(SPOT).resolve("SKILL.md")) ? Shape.COPY : Shape.USER);
            case "missing" -> Set.of(Shape.MISSING);
            default -> throw new IllegalArgumentException("unknown upstream verdict " + verdict);
        };
    }

    record Case(String version, String name, JsonObject layout, String verdict) {
        boolean hasLinks() {
            return layout.has("links");
        }
    }

    static List<Case> cases() throws IOException {
        List<Case> out = new ArrayList<>();
        for (String v : FixtureVersions.versionsWith("skill-spots", REQUIRED)) {
            try (Stream<Path> dirs = Files.list(FixtureVersions.CLI.resolve(v).resolve("skill-spots"))) {
                for (Path c : dirs.sorted().toList()) {
                    out.add(new Case(v, c.getFileName().toString(),
                            JsonParser.parseString(Files.readString(c.resolve("layout.json"))).getAsJsonObject(),
                            Files.readString(c.resolve("verdict.txt"), StandardCharsets.UTF_8).strip()));
                }
            }
        }
        return out;
    }

    private void check(Case c, Path project) throws IOException {
        FixtureLayout.materialize(c.layout(), project, tmp.resolve("home-" + c.version() + "-" + c.name()),
                FixtureLayout.LinkMode.SYMLINK);
        Shape actual = SkillSpot.classify(project.resolve(SPOT), project.resolve(SHARED));
        assertEquals(expected(c.verdict(), project), Set.of(actual), "upstream says " + c.verdict());
    }

    @Test
    void everyCapturedShapeIsReplayed() throws IOException {
        List<Case> all = cases();
        assertEquals(17 * REQUIRED.size(), all.size(), "17 shapes per captured release");
        assertTrue(all.stream().anyMatch(Case::hasLinks));
        assertTrue(all.stream().anyMatch(c -> !c.hasLinks()));
    }

    /** Copies, link text and empty spots: what Windows hosts have, so never skipped. */
    @TestFactory
    Stream<DynamicTest> shapesWithoutLinksOnEveryHost() throws IOException {
        return cases().stream().filter(c -> !c.hasLinks()).map(c -> DynamicTest.dynamicTest(
                c.version() + "/" + c.name() + " [no links]", () -> check(c, tmp.resolve(c.version()).resolve(c.name()))));
    }

    @Nested
    class Links {
        @TestFactory
        Stream<DynamicTest> shapesWithLinks(TestReporter reporter) throws IOException {
            String why = SymlinkProbe.probe(tmp);
            reporter.publishEntry("symlink-probe", why == null ? "links supported" : why);
            return cases().stream().filter(Case::hasLinks).map(c -> DynamicTest.dynamicTest(
                    c.version() + "/" + c.name() + " [symlink]", () -> {
                        SymlinkProbe.requireLinks(tmp);
                        check(c, tmp.resolve("links").resolve(c.version()).resolve(c.name()));
                    }));
        }

        @Test
        void aLinkReachedThroughALinkedProjectRootStillPointsHome() throws IOException {
            SymlinkProbe.requireLinks(tmp);
            Path real = tmp.resolve("real-project");
            Files.createDirectories(real.resolve(SHARED));
            Files.writeString(real.resolve(SHARED).resolve("SKILL.md"), "shared\n");
            Files.createDirectories(real.resolve(SPOT).getParent());
            Files.createSymbolicLink(real.resolve(SPOT), Path.of("../../" + SHARED));
            Path linkedRoot = tmp.resolve("linked-project");
            Files.createSymbolicLink(linkedRoot, real);
            // The spot seen through the linked root, the target spelled through the real root: real paths agree.
            assertEquals(Shape.LINK, SkillSpot.classify(linkedRoot.resolve(SPOT), real.resolve(SHARED)));
        }
    }

    @Test
    void linkTextWithBackslashesResolvesOnThisHost() throws IOException {
        Path project = tmp.resolve("backslash");
        Files.createDirectories(project.resolve(SHARED));
        Files.writeString(project.resolve(SHARED).resolve("SKILL.md"), "shared\n");
        Files.createDirectories(project.resolve(SPOT).getParent());
        Files.writeString(project.resolve(SPOT), "..\\..\\.agents\\skills\\openspec-explore");
        assertEquals(Shape.RELEASED, SkillSpot.classify(project.resolve(SPOT), project.resolve(SHARED)));
    }

    /** macOS: the temporary directory is under /var, whose real path is /private/var. */
    @Test
    void linkTextMatchesATargetSpelledThroughItsRealPath() throws IOException {
        Path project = tmp.resolve("realpath");
        Files.createDirectories(project.resolve(SHARED));
        Files.createDirectories(project.resolve(SPOT).getParent());
        Files.writeString(project.resolve(SPOT), "../../" + SHARED);
        Path realTarget = project.resolve(SHARED).toRealPath();
        assertEquals(Shape.RELEASED, SkillSpot.classify(project.resolve(SPOT), realTarget));
        assertEquals(Shape.RELEASED, SkillSpot.classify(project.toRealPath().resolve(SPOT), project.resolve(SHARED)));
    }

    @Test
    void aLargeFileIsNeverLinkText() throws IOException {
        Path project = tmp.resolve("large");
        Files.createDirectories(project.resolve(SHARED));
        Files.createDirectories(project.resolve(SPOT).getParent());
        Files.writeString(project.resolve(SPOT), "../../" + SHARED + " ".repeat(1100));
        assertEquals(Shape.USER, SkillSpot.classify(project.resolve(SPOT), project.resolve(SHARED)));
    }

    @Test
    void osJunkIsUpstreamsList() {
        for (String junk : List.of(".DS_Store", "Thumbs.db", "desktop.ini", "._SKILL.md", "._")) {
            assertTrue(SkillSpot.isOsJunk(junk), junk);
        }
        for (String real : List.of("SKILL.md", "notes.md", ".hidden", "_x")) assertFalse(SkillSpot.isOsJunk(real), real);
    }

    @Test
    void openSpecLinksAreLinksBrokenLinksAndLinkText() {
        assertTrue(SkillSpot.isOpenSpecLink(Shape.LINK));
        assertTrue(SkillSpot.isOpenSpecLink(Shape.BROKEN));
        assertTrue(SkillSpot.isOpenSpecLink(Shape.RELEASED));
        assertFalse(SkillSpot.isOpenSpecLink(Shape.COPY));
        assertFalse(SkillSpot.isOpenSpecLink(Shape.USER));
        assertFalse(SkillSpot.isOpenSpecLink(Shape.MISSING));
    }
}

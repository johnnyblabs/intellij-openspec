package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.CliFixtureSuite;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;
import com.johnnyblabs.openspec.util.UpdatePlanParser.Plan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link UpdateMigration#atRiskFiles} against what the real CLIs did. Each capture records the project before and after
 * the applied update; the expected answer is worked out from those, not typed in: the files the case's seed made the
 * user's own that the update then removed or changed. On 2.0.0, which keeps them, that is nothing.
 */
@CliFixtureSuite
class UpdateMigrationAtRiskContractTest {

    /** What each case's seed made the user's own (see the capture script). */
    static final Map<String, Set<String>> SEEDED = Map.of(
            "edited", Set.of(".claude/skills/openspec-explore/SKILL.md", ".cursor/skills/openspec-explore/SKILL.md"),
            "after-2.0.0-kept", Set.of(".claude/skills/openspec-explore/SKILL.md",
                    ".cursor/skills/openspec-explore/SKILL.md"),
            "notes-beside-skill", Set.of(".claude/skills/openspec-explore/notes.md",
                    ".cursor/skills/openspec-explore/notes.md"),
            "shared-folder", Set.of(".agents/skills/openspec-explore/SKILL.md"),
            "eve-edited", Set.of("agent/skills/openspec-explore/SKILL.md"),
            "shared-own-skill", Set.of(".agents/skills/openspec-explore/SKILL.md"),
            "router-reference-edited", Set.of(".agents/skills/openspec/references/answers.md"),
            "router-copy-edited", Set.of("agent/skills/openspec/references/answers.md"));

    /**
     * The known false warning (design Risks): 1.17 writes a narrow profile's skills in text the released-file set
     * doesn't hold, so OpenSpec can't confirm them. Only the skills that refer to other workflows differ. This entry
     * pins the plugin's current answer, not the user's intent: the files are unedited.
     */
    static final Set<String> NARROW_PROFILE_UNCONFIRMED = Set.of(".claude/skills/openspec-apply-change/SKILL.md");

    @TempDir
    Path tmp;

    record Case(String version, String name, Path dir) {
        String id() {
            return version + "/" + name;
        }
    }

    static List<Case> cases() throws IOException {
        List<Case> all = new java.util.ArrayList<>();
        for (String v : UpdateMigrationTest.RELEASES_2X) {
            try (Stream<Path> dirs = Files.list(FixtureVersions.CLI.resolve(v).resolve("update-migration"))) {
                dirs.filter(Files::isDirectory).sorted().forEach(d -> all.add(new Case(v, d.getFileName().toString(), d)));
            }
        }
        return all;
    }

    static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    /** The files of {@code before} that are gone from, or changed in, {@code after} (by their stored text key). */
    static Set<String> removedOrChanged(JsonObject before, JsonObject after) {
        JsonObject b = before.getAsJsonObject("project");
        JsonObject a = after.getAsJsonObject("project");
        return b.keySet().stream().filter(k -> !a.has(k) || !a.get(k).equals(b.get(k)))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    Set<String> atRisk(Case c) throws IOException {
        Path project = tmp.resolve(c.id().replace('/', '_')).resolve("project");
        Path home = project.resolveSibling("home");
        JsonObject texts = json(c.dir().getParent().resolve("texts.json"));
        FixtureLayout.materialize(json(c.dir().resolve("before.json")), project, home,
                FixtureLayout.LinkMode.SYMLINK_OR_COPY, texts);
        Plan plan = UpdateMigrationTest.plan(c.dir().resolve("plan.json"), c.dir().resolve("plan.exit"));
        return UpdateMigration.atRiskFiles(project, plan).stream().map(UpdateMigration.AtRisk::path)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void theFilesAtRiskAreTheUsersOwnFilesTheUpdateRemovedOrChanged() throws IOException {
        List<String> wrong = new java.util.ArrayList<>();
        int withRisk = 0;
        for (Case c : cases()) {
            Set<String> lost = removedOrChanged(json(c.dir().resolve("before.json")), json(c.dir().resolve("after.json")));
            Set<String> expected = new TreeSet<>(SEEDED.getOrDefault(c.name(), Set.of()));
            if (c.name().equals("narrow-profile")) expected.addAll(NARROW_PROFILE_UNCONFIRMED);
            expected.retainAll(lost);
            Set<String> actual = atRisk(c);
            if (!actual.equals(expected)) wrong.add(c.id() + ": expected " + expected + ", got " + actual);
            if (!expected.isEmpty()) withRisk++;
        }
        assertTrue(withRisk >= 10, "sanity: cases with files at risk " + withRisk);
        assertEquals(List.of(), wrong, String.join("\n", wrong));
    }

    /** 2.0.0 keeps edited copies and leaves them out of its plan: nothing to warn about, though the files are edited. */
    @Test
    void twoZeroZeroKeepsTheEditedCopiesSoNothingIsAtRisk() throws IOException {
        Case edited = cases().stream().filter(c -> c.id().equals("2.0.0/edited")).findFirst().orElseThrow();
        assertEquals(Set.of(), atRisk(edited));
        assertEquals(Set.of(), SEEDED.get("edited").stream()
                .filter(removedOrChanged(json(edited.dir().resolve("before.json")),
                        json(edited.dir().resolve("after.json")))::contains)
                .collect(Collectors.toSet()), "sanity: 2.0.0 kept both copies");
    }

    /** The precondition the released check rests on: every copy an untouched 1.17.0 init wrote is a released file. */
    @Test
    void everyUneditedSeedSkillIsReleased() throws IOException {
        int checked = 0;
        for (Case c : cases()) {
            if (!c.name().equals("unedited")) continue;
            JsonObject before = json(c.dir().resolve("before.json")).getAsJsonObject("project");
            JsonObject texts = json(c.dir().getParent().resolve("texts.json"));
            for (String path : before.keySet()) {
                if (!path.endsWith("/SKILL.md")) continue;
                String text = texts.get(before.get(path).getAsString().substring(1)).getAsString();
                assertTrue(ReleasedSkillFingerprints.isReleased(text), c.id() + " " + path);
                checked++;
            }
        }
        assertTrue(checked >= 36, "sanity: skills checked " + checked);
    }

    /** Eve's unedited copies, refreshed in place by 2.1.1, differ from the shared copy only by upstream's rewrite. */
    @Test
    void anUneditedEveCopyIsNotAtRiskThoughItIsRewritten() throws IOException {
        Case eve = cases().stream().filter(c -> c.id().equals("2.1.1/eve-from-2.1.0")).findFirst().orElseThrow();
        Plan plan = UpdateMigrationTest.plan(eve.dir().resolve("plan.json"), eve.dir().resolve("plan.exit"));
        assertTrue(plan.changes().stream().anyMatch(ch -> ch.tool().equals("eve") && ch.action().equals("update")),
                "sanity: the plan rewrites an Eve copy");
        assertEquals(Set.of(), atRisk(eve));
    }

    /** CLI 2.2's router: an edited reference, in the shared folder or a tool's own copy, is overwritten and named. */
    @Test
    void anEditedRouterReferenceIsAtRisk() throws IOException {
        for (String name : List.of("router-reference-edited", "router-copy-edited")) {
            Case c = cases().stream().filter(x -> x.id().equals("2.2.0/" + name)).findFirst().orElseThrow();
            assertEquals(SEEDED.get(name), atRisk(c), c.id());
        }
    }

    /** The router as 2.2.0 wrote it, or added to a 2.1.1 project, is never at risk. */
    @Test
    void anUneditedOrNewRouterIsNotAtRisk() throws IOException {
        for (String name : List.of("router-unedited", "router-from-2.1.1")) {
            Case c = cases().stream().filter(x -> x.id().equals("2.2.0/" + name)).findFirst().orElseThrow();
            assertEquals(Set.of(), atRisk(c), c.id());
        }
    }

    /**
     * The shipped router-reference fingerprints hold every reference file 2.2.0 wrote, in the shared folder and in an
     * Eve copy, and not an edited one. Without them an emptied profile, whose update deletes the router, would warn
     * about its untouched references.
     */
    @Test
    void everyRouterReferenceAReleaseWroteIsKnownAndAnEditedOneIsNot() throws IOException {
        int checked = 0;
        for (String name : List.of("router-unedited", "router-copy-edited")) {
            Case c = cases().stream().filter(x -> x.id().equals("2.2.0/" + name)).findFirst().orElseThrow();
            JsonObject before = json(c.dir().resolve("before.json")).getAsJsonObject("project");
            JsonObject texts = json(c.dir().getParent().resolve("texts.json"));
            for (String path : before.keySet()) {
                if (!path.contains("/skills/openspec/references/")) continue;
                String text = texts.get(before.get(path).getAsString().substring(1)).getAsString();
                boolean edited = SEEDED.getOrDefault(name, Set.of()).contains(path);
                assertEquals(!edited, ReleasedSkillFingerprints.isReleasedRouterReference(text), c.id() + " " + path);
                checked++;
            }
        }
        assertTrue(checked >= 6, "sanity: router references checked " + checked);
    }

    @Test
    void anUnreadableFileIsAtRisk() throws IOException {
        Path project = tmp.resolve("unreadable");
        Path skill = project.resolve(".claude/skills/openspec-explore/SKILL.md");
        Files.createDirectories(skill);  // a folder where the file should be: reading it fails
        var plan = new Plan(List.of(new com.johnnyblabs.openspec.util.UpdatePlanParser.Change(
                ".claude/skills/openspec-explore/SKILL.md", "delete", "claude")), List.of(), List.of());
        assertEquals(List.of(new UpdateMigration.AtRisk(".claude/skills/openspec-explore/SKILL.md", "delete")),
                UpdateMigration.atRiskFiles(project, plan));
    }
}

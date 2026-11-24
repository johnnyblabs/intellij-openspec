package com.johnnyblabs.openspec.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.FixtureLayout;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The scope verdict over layouts the real CLI left: each captured init, recreated on disk with its home folder, reads
 * as in the project, only on the machine, or neither, as the CLI put the skills.
 */
class ProjectSkillsScopeContractTest {

    @TempDir
    Path tmp;

    /** Layout (relative to a release's fixtures) → where the CLI put the skills. */
    static final Map<String, ProjectSkillsScope> CASES = new LinkedHashMap<>();

    static {
        CASES.put("2.1.0/detect/scope-project", ProjectSkillsScope.IN_PROJECT);
        CASES.put("2.1.1/detect/scope-project", ProjectSkillsScope.IN_PROJECT);
        CASES.put("2.1.0/detect/scope-default-global", ProjectSkillsScope.ON_MACHINE);
        CASES.put("2.1.1/detect/scope-default-global", ProjectSkillsScope.ON_MACHINE);
        for (String v : new String[] {"2.1.0", "2.1.1"}) {
            CASES.put(v + "/skills-scope/env-project", ProjectSkillsScope.IN_PROJECT);
            CASES.put(v + "/skills-scope/machine-only", ProjectSkillsScope.ON_MACHINE);
            CASES.put(v + "/skills-scope/machine-add-keep", ProjectSkillsScope.ON_MACHINE);
            CASES.put(v + "/skills-scope/machine-add-project", ProjectSkillsScope.IN_PROJECT);
            CASES.put(v + "/skills-scope/project-add-keep", ProjectSkillsScope.IN_PROJECT);
        }
        CASES.put("2.0.0/skills-scope/env-ignored", ProjectSkillsScope.IN_PROJECT);
    }

    private static JsonObject layout(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(FixtureVersions.CLI.resolve(rel).resolve("layout.json")))
                .getAsJsonObject();
    }

    @TestFactory
    Stream<DynamicTest> eachCapturedLayoutReadsWhereTheCliPutTheSkills() {
        return CASES.entrySet().stream().map(c -> DynamicTest.dynamicTest(c.getKey(), () -> {
            Path project = tmp.resolve(c.getKey()).resolve("project");
            Path home = tmp.resolve(c.getKey()).resolve("home");
            FixtureLayout.materialize(layout(c.getKey()), project, home, FixtureLayout.LinkMode.SYMLINK_OR_COPY);
            assertEquals(c.getValue(), ProjectSkillsScope.of(project, HomeSkills.scan(home)));
        }));
    }

    /** A project that is the home folder holds the machine's skills as its own. */
    @TestFactory
    Stream<DynamicTest> theHomeFolderAsAProjectIsInTheProject() {
        return Stream.of("2.1.0", "2.1.1").map(v -> DynamicTest.dynamicTest(v, () -> {
            Path home = tmp.resolve(v).resolve("home");
            FixtureLayout.materialize(layout(v + "/skills-scope/home-project"), home, home,
                    FixtureLayout.LinkMode.SYMLINK_OR_COPY);
            assertEquals(ProjectSkillsScope.IN_PROJECT, ProjectSkillsScope.of(home, HomeSkills.scan(home)));
        }));
    }

    /** The machine-scoped layouts again, with the home read left out: nothing anywhere. */
    @Test
    void withoutTheHomeFolderAMachineScopedProjectHasNone() throws IOException {
        Path project = tmp.resolve("p");
        Path home = tmp.resolve("h");
        FixtureLayout.materialize(layout("2.1.1/skills-scope/machine-only"), project, home,
                FixtureLayout.LinkMode.SYMLINK_OR_COPY);
        assertEquals(ProjectSkillsScope.NONE, ProjectSkillsScope.of(project, HomeSkills.NONE));
        assertEquals(ProjectSkillsScope.NONE, ProjectSkillsScope.of(tmp.resolve("empty"), HomeSkills.scan(tmp.resolve("e2"))));
    }
}

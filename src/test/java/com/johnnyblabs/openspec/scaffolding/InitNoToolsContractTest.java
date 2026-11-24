package com.johnnyblabs.openspec.scaffolding;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The setup wizard's init with no tool detected, {@code init --tools none --scope project}, as the real 2.1 CLI ran
 * it: accepted, with no skills written anywhere (none in the project, none for the machine).
 */
class InitNoToolsContractTest {

    @TestFactory
    Stream<DynamicTest> theWizardsNoToolsInitIsAcceptedAndWritesNoSkills() {
        return Stream.of("2.1.0", "2.1.1").map(v -> DynamicTest.dynamicTest(v, () -> {
            assertEquals(List.of("init", "--tools", "none", "--scope", "project"),
                    ScaffoldingService.initArgs(List.of()).resolve(v).argv(), "the command the capture ran");
            Path dir = FixtureVersions.CLI.resolve(v).resolve("skills-scope/tools-none");
            assertEquals("0", Files.readString(dir.resolve("exit")).strip());
            JsonObject layout = JsonParser.parseString(Files.readString(dir.resolve("layout.json"))).getAsJsonObject();
            assertEquals(0, layout.getAsJsonObject("project").size(), "no skills in the project");
            assertEquals(0, layout.getAsJsonObject("home").size(), "and none for the machine");
        }));
    }
}

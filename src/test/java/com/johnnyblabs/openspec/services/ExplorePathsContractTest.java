package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The registry's explore skill and command paths equal what {@code openspec init --tools <id>} writes, for every tool
 * and delivery mode, as captured by {@code scripts/capture-explore-fixtures.py}.
 */
class ExplorePathsContractTest {

    private static final Path DIR = AiToolRegistryContractTest.FIXTURES.resolve(AiToolRegistryContractTest.TARGET)
            .resolve("explore");

    /** Tool id to the project files ("P") and HOME files ("H") init wrote for one delivery mode. */
    private record Listing(Map<String, Set<String>> project, Map<String, Set<String>> home) {
        Set<String> project(String tool) {
            return project.getOrDefault(tool, Set.of());
        }
    }

    private static Listing listing(String delivery) throws IOException {
        Map<String, Set<String>> project = new HashMap<>(), home = new HashMap<>();
        for (String line : Files.readAllLines(DIR.resolve("listing-" + delivery + ".txt"))) {
            if (line.isBlank()) continue;
            String[] parts = line.split(" ", 3);
            (parts[1].equals("P") ? project : home).computeIfAbsent(parts[0], k -> new TreeSet<>()).add(parts[2]);
        }
        return new Listing(project, home);
    }

    @Test
    void everyProjectExploreFileIsTheToolsSkillOrCommandPath() throws IOException {
        for (String delivery : List.of("both", "skills", "commands")) {
            Listing listing = listing(delivery);
            for (Map.Entry<String, Set<String>> e : listing.project().entrySet()) {
                AiTool tool = AiToolRegistry.byId(e.getKey());
                assertNotNull(tool, "unknown tool in the " + delivery + " listing: " + e.getKey());
                Set<String> known = new HashSet<>();
                if (AiToolRegistry.exploreSkillPath(tool) != null) known.add(AiToolRegistry.exploreSkillPath(tool));
                if (AiToolRegistry.exploreCommandPath(tool) != null) known.add(AiToolRegistry.exploreCommandPath(tool));
                for (String path : e.getValue()) {
                    assertTrue(known.contains(path), delivery + ": " + tool.id() + " wrote " + path
                            + ", which the registry doesn't know (known: " + known + ")");
                }
            }
        }
    }

    @Test
    void theSkillIsWrittenWhereTheRegistrySaysUnlessDeliveryIsCommands() throws IOException {
        for (String delivery : List.of("both", "skills")) {
            Listing listing = listing(delivery);
            for (AiTool tool : AiToolRegistry.all()) {
                String skill = AiToolRegistry.exploreSkillPath(tool);
                if (skill == null) continue;
                assertTrue(listing.project(tool.id()).contains(skill),
                        delivery + ": no " + skill + " for " + tool.id() + "; init wrote " + listing.project(tool.id()));
            }
        }
    }

    @Test
    void theCommandIsWrittenWhereTheRegistrySaysUnlessDeliveryIsSkills() throws IOException {
        for (String delivery : List.of("both", "commands")) {
            Listing listing = listing(delivery);
            for (AiTool tool : AiToolRegistry.all()) {
                String command = AiToolRegistry.exploreCommandPath(tool);
                Set<String> written = listing.project(tool.id());
                if (command != null) {
                    assertTrue(written.contains(command),
                            delivery + ": no " + command + " for " + tool.id() + "; init wrote " + written);
                } else {
                    assertTrue(written.stream().allMatch(p -> p.endsWith("/SKILL.md")),
                            delivery + ": " + tool.id() + " has no command path but init wrote " + written);
                }
            }
        }
    }

    @Test
    void skillsDeliveryWritesNoCommands() throws IOException {
        Listing listing = listing("skills");
        listing.project().forEach((tool, paths) -> assertTrue(paths.stream().allMatch(p -> p.endsWith("/SKILL.md")),
                tool + " wrote a command under delivery: skills: " + paths));
    }

    @Test
    void aGlobalOnlyToolWritesItsSkillUnderHomeAndNothingInTheProject() throws IOException {
        Listing listing = listing("both");
        List<AiTool> globalOnly = AiToolRegistry.all().stream().filter(t -> t.skillsDir() == null).toList();
        assertFalse(globalOnly.isEmpty(), "the registry has a global-only tool to check");
        for (AiTool tool : globalOnly) {
            assertTrue(listing.project(tool.id()).isEmpty(), tool.id() + " wrote into the project");
            assertEquals(Set.of(tool.globalSkillsDir() + "/skills/openspec-explore/SKILL.md"),
                    listing.home().get(tool.id()));
        }
    }

    @Test
    void everyRegistryToolWasCaptured() throws IOException {
        Listing listing = listing("both");
        for (AiTool tool : AiToolRegistry.all()) {
            assertTrue(listing.project().containsKey(tool.id()) || listing.home().containsKey(tool.id()),
                    tool.id() + " is missing from the capture; re-run scripts/capture-explore-fixtures.py");
        }
    }
}

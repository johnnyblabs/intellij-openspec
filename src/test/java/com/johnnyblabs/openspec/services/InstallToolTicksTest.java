package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The install list's ticks: the project's tools, the machine's tools, and the preferred tool; ticked ones first. */
class InstallToolTicksTest {

    @TempDir
    Path tmp;
    Path project;
    Path home;

    @BeforeEach
    void dirs() throws IOException {
        project = Files.createDirectories(tmp.resolve("project"));
        home = Files.createDirectories(tmp.resolve("home"));
    }

    static List<String> ticked(List<InstallToolTicks.Row> rows) {
        return rows.stream().filter(InstallToolTicks.Row::ticked).map(r -> r.tool().id()).toList();
    }

    @Test
    void nothingAnywhereTicksNothing() {
        List<InstallToolTicks.Row> rows = InstallToolTicks.rows(project, home, null, null);
        assertEquals(List.of(), ticked(rows));
        assertFalse(rows.isEmpty(), "every tool is still listed");
    }

    @Test
    void aToolInstalledOnTheMachineIsTicked() throws IOException {
        Files.createDirectories(home.resolve(".claude"));
        Files.createDirectories(home.resolve(".cursor"));
        assertEquals(List.of("claude", "cursor"), ticked(InstallToolTicks.rows(project, home, null, null)).stream()
                .filter(id -> id.equals("claude") || id.equals("cursor")).toList());
    }

    @Test
    void aToolFoundInTheProjectIsTicked() throws IOException {
        Files.createDirectories(project.resolve(".cursor"));
        assertTrue(ticked(InstallToolTicks.rows(project, home, null, null)).contains("cursor"));
    }

    @Test
    void thePreferredToolIsTicked() {
        AiTool codex = AiToolRegistry.byId("codex");
        assertEquals(List.of("codex"), ticked(InstallToolTicks.rows(project, home, null, codex.name())));
    }

    @Test
    void theUniversalEntryIsNeverTickedFromTheHomeFolder() throws IOException {
        Files.createDirectories(home.resolve(".agents/skills/openspec-explore"));
        Files.writeString(home.resolve(".agents/skills/openspec-explore/SKILL.md"), "x\n");
        assertFalse(ticked(InstallToolTicks.rows(project, home, null, null)).contains("agents"));
    }

    @Test
    void tickedToolsComeFirstThenTheRestInRegistryOrder() throws IOException {
        Files.createDirectories(home.resolve(".cursor"));
        List<InstallToolTicks.Row> rows = InstallToolTicks.rows(project, home, null, AiToolRegistry.byId("claude").name());
        List<String> order = rows.stream().map(r -> r.tool().id()).toList();
        int lastTicked = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).ticked()) lastTicked = i;
        assertEquals(ticked(rows).size() - 1, lastTicked, "no unticked row before a ticked one");
        List<String> registry = AiToolRegistry.all().stream().map(AiTool::id).filter(order::contains).toList();
        List<String> rest = order.subList(lastTicked + 1, order.size());
        assertEquals(registry.stream().filter(rest::contains).toList(), rest);
    }

    @Test
    void theToolsArgumentIsInRegistryOrderWithCliIds() {
        AiTool claude = AiToolRegistry.byId("claude");
        AiTool cursor = AiToolRegistry.byId("cursor");
        int ci = AiToolRegistry.all().indexOf(claude), ui = AiToolRegistry.all().indexOf(cursor);
        String expected = ci < ui ? claude.cliId() + "," + cursor.cliId() : cursor.cliId() + "," + claude.cliId();
        assertEquals(expected, InstallToolTicks.toolsArg(List.of(cursor, claude)));
    }
}

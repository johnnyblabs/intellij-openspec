package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link CliHelp} reads real captured help: the flag record the CLI flag table is checked against. */
class CliHelpTest {

    @Test
    void archiveJsonArrivesIn150() {
        assertFalse(CliHelp.of("1.4.1").hasFlag("archive", "--json"));
        assertTrue(CliHelp.of("1.5.0").hasFlag("archive", "--json"));
        assertTrue(CliHelp.of("1.4.1").hasFlag("archive", "--yes"), "a short form before the long one is read");
    }

    @Test
    void aFlagADescriptionMentionsIsNotAFlag() {
        // The top-level help's command list describes `list` with "--specs"; the root command doesn't take it.
        CliHelp help = CliHelp.of("1.17.0");
        assertFalse(help.hasFlag("openspec", "--specs"));
        assertTrue(help.hasFlag("list", "--specs"));
    }

    @Test
    void negatedFlagsAreReadAsWritten() {
        Map<String, Boolean> flags = CliHelp.of("1.3.1").flags("schema init");
        assertTrue(flags.containsKey("no-default"));
        assertTrue(flags.containsKey("default"));
        // `--no-validate` alone doesn't make `--validate` an accepted flag.
        assertTrue(CliHelp.of("1.4.1").hasFlag("archive", "--no-validate"));
        assertFalse(CliHelp.of("1.4.1").hasFlag("archive", "--validate"));
    }

    @Test
    void valueShapeIsRead() {
        CliHelp help = CliHelp.of("2.1.1");
        assertTrue(help.takesValue("new change", "--schema"));
        assertFalse(help.takesValue("validate", "--json"));
        assertTrue(help.takesValue("init", "--scope"));
    }

    @Test
    void theEpilogAfterTheOptionsIsNotRead() {
        // 2.1's init help ends with a "Use ... comma-separated list of: ..." line after a blank line.
        Map<String, Boolean> flags = CliHelp.of("2.1.1").flags("init");
        assertTrue(flags.containsKey("tools"));
        assertFalse(flags.keySet().stream().anyMatch(f -> f.contains("windsurf")));
    }

    @Test
    void positionalArityComesFromTheUsageLine() {
        assertEquals(2, CliHelp.of("2.1.1").maxPositionals("schema fork"));
        assertEquals(1, CliHelp.of("2.1.1").maxPositionals("archive"));
        assertEquals(0, CliHelp.of("2.1.1").maxPositionals("status"));
        assertEquals(0, CliHelp.of("1.4.1").maxPositionals("workspace setup"));
        assertEquals(1, CliHelp.parseMaxPositionals("Usage: openspec x [options] [command] <id>\n"));
    }

    @Test
    void aWrappedDescriptionStartingWithAFlagIsNotAFlag() {
        String help = "Usage: openspec x [options]\n\nOptions:\n  --json        Output JSON; see\n"
                + "                --specs for more\n  -h, --help    display help\n";
        assertEquals(java.util.Set.of("json", "help"), CliHelp.parseOptions(help).keySet());
    }

    @Test
    void commandsComeFromTheIndex() {
        assertTrue(CliHelp.of("1.4.1").hasCommand("workspace setup"));
        assertFalse(CliHelp.of("1.5.0").hasCommand("workspace"));
        assertTrue(CliHelp.of("1.5.0").records("workspace"));
        assertFalse(CliHelp.of("1.3.1").hasCommand("store"));
        assertThrows(IllegalArgumentException.class, () -> CliHelp.of("1.3.1").flags("store"));
    }
}

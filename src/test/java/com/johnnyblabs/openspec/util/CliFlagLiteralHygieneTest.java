package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every CLI command line is built in {@link CliCommands}, so the version matrix sees all of them. A flag written
 * anywhere else, as a bare {@code "--json"} or at the end of a command string ({@code "init --tools " + id}), would
 * reach the CLI without being checked against the release that runs it.
 * <p>
 * Prose that mentions a flag ("the plugin never runs {@code openspec update --force}") is fine: only a literal that is
 * a flag, or ends with one, is how an argument gets built.
 */
class CliFlagLiteralHygieneTest {

    static final Path MAIN = Path.of("src/main/java");
    static final Path CATALOG = MAIN.resolve("com/johnnyblabs/openspec/util/CliCommands.java");
    static final Path TABLE = MAIN.resolve("com/johnnyblabs/openspec/util/CliFlags.java");

    /** Literals allowed outside the catalog, by file, with the reason. */
    static final Map<String, String> EXEMPT = Map.of(
            "com/johnnyblabs/openspec/services/CliDetectionService.java:\"--version\"",
            "detection runs --version before any release is known, and every release has it");

    /** A literal that is a flag, or ends with one: how a command line gets built. */
    static final Pattern BUILDS_A_FLAG = Pattern.compile("(^|\\s)--[a-z][a-z0-9-]*\\s?$");

    record Literal(Path file, int line, String text) {
        String key() {
            return MAIN.relativize(file).toString().replace('\\', '/') + ":\"" + text + "\"";
        }
    }

    @Test
    void noFlagIsWrittenOutsideTheCatalog() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path f : javaFiles()) {
            if (f.equals(CATALOG) || f.equals(TABLE)) continue;
            for (Literal l : literals(f)) {
                if (BUILDS_A_FLAG.matcher(l.text()).find() && !EXEMPT.containsKey(l.key())) {
                    found.add(MAIN.relativize(f) + ":" + l.line() + " \"" + l.text() + "\"");
                }
            }
        }
        assertEquals(List.of(), found, "build CLI arguments with a CliCommands builder");
    }

    @Test
    void everyExemptionStillExists() throws IOException {
        List<String> keys = new ArrayList<>();
        for (Path f : javaFiles()) for (Literal l : literals(f)) keys.add(l.key());
        for (String exempt : EXEMPT.keySet()) assertTrue(keys.contains(exempt), "stale exemption " + exempt);
    }

    @Test
    void onlyTheCatalogBuildsArgumentsAndOnlyFromLiteralCommandWords() throws IOException {
        List<String> found = new ArrayList<>();
        Pattern command = Pattern.compile("CliArgs\\.command\\(([^)]*)\\)");
        for (Path f : javaFiles()) {
            String source = stripComments(Files.readString(f));
            Matcher m = command.matcher(source);
            while (m.find()) {
                int line = lineOf(source, m.start());
                if (!f.equals(CATALOG) && !f.getFileName().toString().equals("CliArgs.java")) {
                    found.add(MAIN.relativize(f) + ":" + line + " builds a CliArgs outside CliCommands");
                } else if (f.equals(CATALOG)
                        && !m.group(1).matches("\\s*\"[a-z-]+\"(\\s*,\\s*\"[a-z-]+\")*\\s*")) {
                    found.add("CliCommands:" + line + " command words must be literals: " + m.group(1));
                }
            }
        }
        assertEquals(List.of(), found);
    }

    @Test
    void theCatalogReadsNothingButItsParameters() throws IOException {
        String source = stripComments(Files.readString(CATALOG));
        List<String> found = new ArrayList<>();
        for (String hidden : List.of("getInstance", "getService", "CliVersion", "OpenSpecSettings", "System.")) {
            if (source.contains(hidden)) found.add(hidden);
        }
        assertEquals(List.of(), found, "a builder branching on hidden state is invisible to the version matrix");
    }

    @Test
    void theScanSeesRealFlagLiterals() throws IOException {
        // Sanity: the lexer finds the catalog's own flags, so an empty result above isn't a blind scan.
        long flags = literals(CATALOG).stream().filter(l -> BUILDS_A_FLAG.matcher(l.text()).find()).count();
        assertTrue(flags >= 30, "found only " + flags + " flag literals in CliCommands");
        assertTrue(BUILDS_A_FLAG.matcher("init --tools ").find(), "a command string ending in a flag");
        assertFalse(BUILDS_A_FLAG.matcher("openspec update --force for you.").find(), "prose");
    }

    static List<Path> javaFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** The string literals in a Java file (text blocks included), with comments skipped. */
    static List<Literal> literals(Path file) throws IOException {
        String s = Files.readString(file);
        List<Literal> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (s.startsWith("//", i)) {
                i = s.indexOf('\n', i);
                if (i < 0) break;
            } else if (s.startsWith("/*", i)) {
                i = s.indexOf("*/", i + 2) + 2;
                if (i < 2) break;
            } else if (s.startsWith("\"\"\"", i)) {
                int end = s.indexOf("\"\"\"", i + 3);
                out.add(new Literal(file, lineOf(s, i), s.substring(i + 3, end)));
                i = end + 3;
            } else if (c == '"') {
                StringBuilder text = new StringBuilder();
                int j = i + 1;
                while (s.charAt(j) != '"') {
                    if (s.charAt(j) == '\\') text.append(s.charAt(j++));
                    text.append(s.charAt(j++));
                }
                out.add(new Literal(file, lineOf(s, i), text.toString()));
                i = j + 1;
            } else if (c == '\'') {
                i = s.indexOf('\'', s.charAt(i + 1) == '\\' ? i + 3 : i + 2) + 1;
            } else {
                i++;
            }
        }
        return out;
    }

    static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "");
    }

    private static int lineOf(String s, int index) {
        int line = 1;
        for (int k = 0; k < index; k++) if (s.charAt(k) == '\n') line++;
        return line;
    }
}

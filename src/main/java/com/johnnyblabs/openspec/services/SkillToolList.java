package com.johnnyblabs.openspec.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The tools listed as set up in a project, from {@code .agents/skills/.openspec-target}, read as upstream reads it
 * ({@code readListFileTools} and {@code readListedToolsWithOwnFiles} in {@code dist/core/shared-skill-target.js}).
 * <p>
 * One tool per line: the first whitespace-separated token is the id, resolved through upstream's aliases; an
 * {@code own-files} token after it marks a tool OpenSpec gave files of its own (commands or links). Ids no tool has are
 * skipped, which also drops 2.0's {@code keep <skill>} lines. A 1.x file holds one id and reads as that one tool.
 * Pure nio, so it is safe in plain unit tests; callers run off the EDT.
 */
public final class SkillToolList {

    public static final String FILE = ".openspec-target";

    /** Upstream's {@code TOOL_ID_ALIASES}: ids an older release wrote, mapped to the current one. */
    static final Map<String, String> ALIASES = Map.of("windsurf", "devin");

    /** One listed tool. */
    public record Entry(String id, boolean ownFiles) {
    }

    private SkillToolList() {
    }

    /** The list file's path in {@code project}. */
    public static Path path(Path project) {
        return project.resolve(".agents").resolve("skills").resolve(FILE);
    }

    /** The listed tools in file order, without duplicates, or an empty list when there is no readable file. */
    public static List<Entry> read(Path project) {
        return read(project, true, id -> true);
    }

    /**
     * {@link #read}, with 2.0's reading: {@code resolveAliases} is false there, and {@code known} drops ids the
     * release's registry lacks (Amazon Q on 2.x).
     */
    static List<Entry> read(Path project, boolean resolveAliases, java.util.function.Predicate<String> known) {
        try {
            Path file = path(project);
            if (!Files.isRegularFile(file)) return List.of();
            return parse(Files.readString(file, StandardCharsets.UTF_8), resolveAliases, known);
        } catch (IOException | RuntimeException e) {
            // Unreadable or out of reach says nothing, as upstream's catch-all does.
            return List.of();
        }
    }

    /** The entries {@code content} lists, read as 2.1 reads them. */
    public static List<Entry> parse(String content) {
        return parse(content, true, id -> true);
    }

    static List<Entry> parse(String content, boolean resolveAliases, java.util.function.Predicate<String> known) {
        Map<String, Boolean> byId = new LinkedHashMap<>();
        for (String line : content.split("\\r?\\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;
            String[] tokens = trimmed.split("\\s+");
            String id = resolveAliases ? ALIASES.getOrDefault(tokens[0], tokens[0]) : tokens[0];
            if (AiToolRegistry.byId(id) == null || !known.test(id)) continue;
            boolean own = tokens.length > 1 && "own-files".equals(tokens[1]);
            byId.merge(id, own, Boolean::logicalOr);
        }
        List<Entry> out = new ArrayList<>();
        byId.forEach((id, own) -> out.add(new Entry(id, own)));
        return List.copyOf(out);
    }

    /** The listed ids. */
    public static List<String> ids(List<Entry> entries) {
        return entries.stream().map(Entry::id).toList();
    }

    /** Whether the list says the project was set up by a 2.x release: more than one tool, or an {@code own-files} mark. */
    public static boolean looks2x(String content) {
        List<Entry> entries = parse(content);
        return entries.size() > 1 || entries.stream().anyMatch(Entry::ownFiles);
    }
}

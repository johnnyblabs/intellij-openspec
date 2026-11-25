package com.johnnyblabs.openspec.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A YAML front-matter block at the top of a spec file, as CLI 2.1.1 recognizes it: a port of upstream's
 * {@code findFrontmatterEnd} ({@code dist/core/parsers/requirement-blocks.js}). The block is opaque (its fields are never
 * read); two checks keep a {@code ---} thematic break from hiding authored text.
 * <ul>
 *   <li>Line 1 is exactly {@code ---} (after a byte-order mark and CRLF are normalized); an indented one is not.</li>
 *   <li>Its first line that is neither blank nor a {@code #} comment looks like a key ({@code owner: a},
 *       {@code "owner": a}, {@code owner:}); after the first key, every line is skipped up to the closing {@code ---}.</li>
 *   <li>A {@code ##}–{@code ######} heading or a requirement header before the close means it is not front matter, and
 *       so does a block that never closes. Comments with no key are not front matter; an empty block is.</li>
 * </ul>
 */
public final class FrontMatter {

    private static final Pattern KEY = Pattern.compile("^(?:\"[^\"]+\"|'[^']+'|[\\p{L}\\p{N}_.-]+)\\s*:(?:\\s|$)");
    private static final Pattern SECTION_HEADING = Pattern.compile("^#{2,6}(?:\\s|$)");
    private static final Pattern REQUIREMENT_HEADER = Pattern.compile("^###\\s*Requirement:\\s*(.+)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private FrontMatter() {
    }

    /** The index of the line after the block, or 0 when {@code lines} (already normalized) start with none. */
    public static int end(List<String> lines) {
        if (lines.isEmpty() || !lines.get(0).stripTrailing().equals("---")) return 0;
        boolean sawKey = false;
        boolean sawComment = false;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (SECTION_HEADING.matcher(line).find() || REQUIREMENT_HEADER.matcher(line).find()) return 0;
            if (line.stripTrailing().equals("---")) return sawKey || !sawComment ? i + 1 : 0;
            if (sawKey || line.isBlank()) continue;
            if (line.stripLeading().startsWith("#")) {
                sawComment = true;
                continue;
            }
            if (!KEY.matcher(line).find()) return 0;
            sawKey = true;
        }
        return 0;
    }

    /**
     * {@code content} with its byte-order mark removed, line endings normalized to {@code \n}, and the lines of a
     * leading front-matter block blanked (kept as empty lines, so line numbers hold). Content without one is returned
     * normalized and otherwise unchanged.
     */
    public static String blank(String content) {
        String normalized = (content.startsWith("﻿") ? content.substring(1) : content)
                .replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int end = end(Arrays.asList(lines));
        if (end == 0) return normalized;
        Arrays.fill(lines, 0, end, "");
        return String.join("\n", lines);
    }

    /** Whether {@code content} starts with a front-matter block. */
    public static boolean present(String content) {
        String normalized = (content.startsWith("﻿") ? content.substring(1) : content)
                .replace("\r\n", "\n").replace('\r', '\n');
        return end(Arrays.asList(normalized.split("\n", -1))) > 0;
    }
}

package com.johnnyblabs.openspec.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes the skills-migration "legacy files pending" block in {@code openspec update}
 * output. The CLI exits 0 in this state, so text recognition is the only signal.
 *
 * <p>Observed shape (byte-identical on CLI 1.4.1 and 1.5.0; contract fixtures under
 * {@code fixtures/cli/update-*.txt}):</p>
 *
 * <pre>
 * Files to remove
 * No user content to preserve:
 *   • .junie/commands/opsx-apply.md
 *   ...
 * ⚠ Run with --force to auto-cleanup legacy files, or run interactively.
 * </pre>
 *
 * <p>Recognition keys on the structural markers — the "Files to remove" header and the
 * bulleted path lines — not the full prose. Degradation is one-directional: output that
 * doesn't match yields an empty list (the Update flow behaves as before), and a partially
 * recognizable block yields only the lines that parse. The parser never expands the set.</p>
 */
public final class UpdateOutputParser {

    private static final Pattern FILES_TO_REMOVE_HEADER =
            Pattern.compile("^\\s*Files to remove\\s*$", Pattern.MULTILINE);
    /** A bulleted file entry; the CLI uses "•" but common bullet variants are tolerated. */
    private static final Pattern BULLET_ENTRY =
            Pattern.compile("^\\s*[•·▪‣*-]\\s+(\\S.*?)\\s*$");

    private UpdateOutputParser() {
    }

    /**
     * The pending legacy-file paths reported by the update output, in CLI order,
     * or an empty list when no migration block is present.
     */
    public static List<String> parseLegacyCleanup(String stdout) {
        if (stdout == null || stdout.isEmpty()) {
            return Collections.emptyList();
        }
        Matcher header = FILES_TO_REMOVE_HEADER.matcher(stdout);
        if (!header.find()) {
            return Collections.emptyList();
        }

        List<String> files = new ArrayList<>();
        String[] lines = stdout.substring(header.end()).split("\r?\n");
        boolean sawBullets = false;
        for (String line : lines) {
            Matcher bullet = BULLET_ENTRY.matcher(line);
            if (bullet.matches()) {
                sawBullets = true;
                files.add(bullet.group(1));
                continue;
            }
            // The bullet run is contiguous; once it has started, the first
            // non-bullet line ends the block ("⚠ Run with --force …" or blank).
            if (sawBullets) {
                break;
            }
            // Before the bullets, tolerate only the certification line and blanks;
            // anything else means this isn't the block we know.
            if (!line.isBlank() && !line.trim().startsWith("No user content to preserve")) {
                break;
            }
        }
        return files;
    }
}

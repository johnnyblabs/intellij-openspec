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
 * <p>Observed shape (contract fixtures under {@code fixtures/cli/<version>/update-*.txt}):</p>
 *
 * <pre>
 * Files to remove
 * &lt;guidance line&gt;
 *   • .cursor/commands/openspec-apply.md
 *   ...
 * ⚠ Run with --force to auto-cleanup legacy files, or run interactively.
 * </pre>
 *
 * <p>The guidance line is {@code No user content to preserve:} up to CLI 1.13 and
 * {@code These files will be deleted entirely. Back up any custom content before proceeding:} from 1.14.0.
 * Recognition keys on the structural markers — the "Files to remove" header and the bulleted path lines —
 * and accepts whatever guidance the CLI prints between them (at most {@link #MAX_GUIDANCE_LINES} lines),
 * returning it so the UI can quote the CLI rather than assume what it said. Degradation is
 * one-directional: output that doesn't match yields nothing (the Update flow behaves as before), and a
 * partially recognizable block yields only the lines that parse. The parser never expands the set.</p>
 */
public final class UpdateOutputParser {

    private static final Pattern FILES_TO_REMOVE_HEADER =
            Pattern.compile("^\\s*Files to remove\\s*$", Pattern.MULTILINE);
    /** A bulleted file entry; the CLI uses "•" but common bullet variants are tolerated. */
    private static final Pattern BULLET_ENTRY =
            Pattern.compile("^\\s*[•·▪‣*-]\\s+(\\S.*?)\\s*$");
    /** Non-blank lines tolerated between the header and the first bullet (the CLI prints one). */
    static final int MAX_GUIDANCE_LINES = 2;
    /** The pre-1.14 certification that the listed files hold no user content. */
    private static final String NO_USER_CONTENT = "No user content to preserve";

    /**
     * A recognized block: the files in CLI order, and the guidance line the CLI printed before them
     * ({@code null} when there was none).
     */
    public record LegacyCleanup(List<String> files, String guidance) {
        public LegacyCleanup {
            files = List.copyOf(files);
        }

        static final LegacyCleanup NONE = new LegacyCleanup(List.of(), null);

        public boolean isEmpty() {
            return files.isEmpty();
        }

        /** Whether the CLI certified the files hold no user content (CLIs before 1.14). */
        public boolean certifiesNoUserContent() {
            return guidance != null && guidance.startsWith(NO_USER_CONTENT);
        }
    }

    private UpdateOutputParser() {
    }

    /**
     * The pending legacy-file paths reported by the update output, in CLI order,
     * or an empty list when no migration block is present.
     */
    public static List<String> parseLegacyCleanup(String stdout) {
        return parseLegacyCleanupBlock(stdout).files();
    }

    /** The pending legacy-file block (files plus the CLI's guidance line), or an empty block. */
    public static LegacyCleanup parseLegacyCleanupBlock(String stdout) {
        if (stdout == null || stdout.isEmpty()) {
            return LegacyCleanup.NONE;
        }
        Matcher header = FILES_TO_REMOVE_HEADER.matcher(stdout);
        if (!header.find()) {
            return LegacyCleanup.NONE;
        }

        List<String> files = new ArrayList<>();
        String guidance = null;
        int guidanceLines = 0;
        String[] lines = stdout.substring(header.end()).split("\r?\n");
        for (String line : lines) {
            Matcher bullet = BULLET_ENTRY.matcher(line);
            if (bullet.matches()) {
                files.add(bullet.group(1));
                continue;
            }
            // The bullet run is contiguous; once it has started, the first
            // non-bullet line ends the block ("⚠ Run with --force …" or blank).
            if (!files.isEmpty()) {
                break;
            }
            if (line.isBlank()) {
                continue;
            }
            // Before the bullets: the CLI's guidance line. Tolerate a little, never an arbitrary amount —
            // a long run of prose after the header means this isn't the block we know.
            if (++guidanceLines > MAX_GUIDANCE_LINES) {
                return LegacyCleanup.NONE;
            }
            if (guidance == null) {
                guidance = line.trim();
            }
        }
        return files.isEmpty() ? LegacyCleanup.NONE : new LegacyCleanup(files, guidance);
    }
}

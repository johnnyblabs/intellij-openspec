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
        // The CLI is asked for plain output, but colour forced by a wrapper would break the anchored patterns.
        stdout = CliText.stripAnsi(stdout);
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

    // ---------- What an applied update kept, left or replaced (CLI 2.x) ----------

    /** What a report says happened to its files. */
    public enum KeptKind {
        /** Kept because the files differ from what OpenSpec generated. */
        KEPT,
        /** Left in place because the CLI couldn't read or remove them, or because other projects may use them. */
        LEFT,
        /** Overwritten: the user's edits were replaced, and no copy was kept. */
        REPLACED
    }

    /**
     * One report from an applied update: its kind, the paths it names (in CLI order, possibly none), the header line as
     * printed, and the guidance the CLI printed with it ({@code null} when it printed none).
     */
    public record KeptReport(KeptKind kind, List<String> paths, String header, String guidance) {
        public KeptReport {
            paths = List.copyOf(paths);
        }
    }

    /** A report whose paths follow on indented lines, and whether the CLI prints a guidance line after them. */
    private record BlockReport(Pattern header, KeptKind kind, boolean guidanceFollows) {
    }

    /**
     * The block reports, from the 2.x sources: {@code shared/command-reconcile.js} (kept and left command files),
     * {@code update.js} {@code reportEditedGlobalPrompts} (kept home prompts) and {@code retired-tools.js} (a retired
     * tool's kept files, itself indented, its paths four spaces in, with no guidance line after).
     */
    private static final List<BlockReport> BLOCKS = List.of(
            new BlockReport(Pattern.compile("^Kept \\d+ command files? that (?:does|do) not match what OpenSpec generated "
                    + "\\(likely edited\\):$"), KeptKind.KEPT, true),
            new BlockReport(Pattern.compile("^Kept \\d+ prompt files? in your home folder that differs? from what OpenSpec "
                    + "generated \\(likely edited\\):$"), KeptKind.KEPT, true),
            new BlockReport(Pattern.compile("^Left \\d+ command files? in place because (?:it|they) could not be read or "
                    + "removed:$"), KeptKind.LEFT, true),
            new BlockReport(Pattern.compile("^ +Kept \\d+ files? in \\S+/ that differs? from what OpenSpec generated "
                    + "\\(likely edited\\); delete (?:it|them) when you're done:$"), KeptKind.KEPT, false));

    /** A one-line report: the paths are a comma-separated list ended by {@code ". "}; the rest is guidance. */
    private record LineReport(Pattern line, KeptKind kind) {
    }

    /**
     * The one-line reports: 2.0.0's {@code formatKeptSkills} ({@code shared/shared-skill-install.js}) and 2.1.1's
     * {@code update.js} line for home prompts left for other projects, which names no paths.
     */
    private static final List<LineReport> LINES = List.of(
            new LineReport(Pattern.compile("^Kept \\d+ skill folders? with your own changes: (.+?)\\. (Nothing was overwritten\\..*)$"),
                    KeptKind.KEPT),
            new LineReport(Pattern.compile("^Kept \\d+ skills? in \\.agents/skills as your own: (.+?)\\. (OpenSpec won't .*)$"),
                    KeptKind.KEPT),
            new LineReport(Pattern.compile("^Replaced your edits in \\d+ skills? OpenSpec installs: (.+?)\\. (OpenSpec refreshes .*)$"),
                    KeptKind.REPLACED),
            new LineReport(Pattern.compile("^Left \\d+ OpenSpec prompt file(?:s|\\(s\\))? in your home folder: ()(.+)$"), KeptKind.LEFT));

    /**
     * The kept, left and replaced reports in an applied {@code openspec update}'s output, in the order printed. Output
     * it doesn't recognize yields nothing; a path is only ever one the CLI printed.
     */
    public static List<KeptReport> parseKept(String stdout) {
        if (stdout == null || stdout.isEmpty()) return List.of();
        String[] lines = CliText.stripAnsi(stdout).split("\r?\n", -1);
        List<KeptReport> reports = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            BlockReport block = BLOCKS.stream().filter(b -> b.header().matcher(line).matches()).findFirst().orElse(null);
            if (block != null) {
                int indent = indentOf(line);
                List<String> paths = new ArrayList<>();
                int j = i + 1;
                while (j < lines.length && !lines[j].isBlank() && indentOf(lines[j]) > indent) {
                    paths.add(lines[j].trim());
                    j++;
                }
                if (paths.isEmpty()) continue;
                String guidance = null;
                if (block.guidanceFollows() && j < lines.length && !lines[j].isBlank() && indentOf(lines[j]) == 0) {
                    guidance = lines[j].trim();
                }
                reports.add(new KeptReport(block.kind(), paths, line.trim(), guidance));
                i = j - 1;
                continue;
            }
            for (LineReport one : LINES) {
                Matcher m = one.line().matcher(line);
                if (!m.matches()) continue;
                List<String> paths = m.group(1).isEmpty() ? List.of() : List.of(m.group(1).split(", "));
                reports.add(new KeptReport(one.kind(), paths, line.trim(), m.group(2)));
                break;
            }
        }
        return List.copyOf(reports);
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') n++;
        return n;
    }
}

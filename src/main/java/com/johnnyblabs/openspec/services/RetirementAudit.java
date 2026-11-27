package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.util.CliVersion;
import com.johnnyblabs.openspec.util.CodeFences;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Upstream's {@code contentTheMergeCannotName} ({@code dist/core/specs-apply.js}): the lines of a main spec a
 * capability retirement could not account for, so deleting the file would take them unmentioned. A retirement is
 * refused while there are any. Three releases' rules, ported line for line and held to captured archive runs:
 * <ul>
 *   <li>{@link Variant#A}, 1.8.0 to 1.12.x: bullets are {@code -}, {@code *} and ordered markers; no list continuation.</li>
 *   <li>{@link Variant#B}, 1.13.0 to 2.1.0: CommonMark list items ({@code +} too), continuation by content column or a
 *       lazy line, and what interrupts a paragraph.</li>
 *   <li>{@link Variant#C}, 2.1.1 on: as B, but a line starting with {@code <} is raw HTML only when it opens an HTML
 *       block or is a tag alone on its line (upstream #2211).</li>
 * </ul>
 */
public final class RetirementAudit {

    /** The release lines whose rules differ. */
    public enum Variant { A, B, C }

    /** The first release that retires a capability at all. */
    public static final String RETIRES_FROM = "1.8.0";

    private RetirementAudit() {
    }

    /** The variant {@code release} checks with; {@code null} (no CLI) follows the newest. Below 1.8 there is none. */
    public static @Nullable Variant variantFor(@Nullable String release) {
        if (release == null) return Variant.C;
        if (!CliVersion.atLeast(release, RETIRES_FROM)) return null;
        if (!CliVersion.atLeast(release, "1.13.0")) return Variant.A;
        if (!CliVersion.atLeast(release, "2.1.1")) return Variant.B;
        return Variant.C;
    }

    private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern FOREIGN_HEADING = Pattern.compile("^ {0,3}#{1,3}(?:[ \\t]|$)");
    private static final Pattern SECTION = Pattern.compile("^ {0,3}##\\s+(.+?)\\s*$", FLAGS);
    private static final Pattern PURPOSE = Pattern.compile("^purpose$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SETEXT = Pattern.compile("^ {0,3}(=+|-+)\\s*$", FLAGS);
    private static final Pattern SETEXT_UNDERLINE = Pattern.compile("^ {0,3}(?:=+|-+)\\s*$", FLAGS);
    private static final Pattern HTML_HEADING = Pattern.compile("^ {0,3}<h[1-6]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("^ {0,3}#\\s+.+$", FLAGS);
    private static final Pattern SCENARIO = Pattern.compile("^ {0,3}####\\s+Scenario:", Pattern.CASE_INSENSITIVE | FLAGS);
    private static final Pattern BULLET_A = Pattern.compile("^\\s*(?:[-*]|\\d+[.)])\\s", FLAGS);
    private static final Pattern STATEMENT_BREAK_A = Pattern.compile("^\\s*[|<]", FLAGS);
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*(?:[-*+]|\\d{1,9}[.)])\\s+)", FLAGS);
    private static final Pattern LEADING_WS = Pattern.compile("^[ \\t]*");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|", FLAGS);
    private static final Pattern HEADING_LINE = Pattern.compile("^ {0,3}#{1,6}(?:[ \\t]|$)");
    private static final Pattern HEADING_HTML = Pattern.compile("^\\s*<h[1-6]\\b", Pattern.CASE_INSENSITIVE | FLAGS);
    /** 1.13.0 to 2.1.0's {@code INTERRUPTS_PARAGRAPH}: any line starting with {@code <} interrupts. */
    private static final Pattern INTERRUPTS_PARAGRAPH_B =
            Pattern.compile("^ {0,3}(?:>|(?:[-*_][ \\t]*){3,}$|(?:[-*+]|\\d{1,9}[.)])(?:[ \\t]|$)|[<|])");
    /** 2.1.1's {@code interruptsParagraph}, without its HTML half. */
    private static final Pattern INTERRUPTS_PARAGRAPH_C =
            Pattern.compile("^ {0,3}(?:>|(?:[-*_][ \\t]*){3,}$|(?:[-*+]|\\d{1,9}[.)])(?:[ \\t]|$)|\\|)");
    private static final Pattern LT_INDENTED = Pattern.compile("^ {0,3}<");
    /** 2.1.1's {@code OPENS_HTML_BLOCK}: CommonMark HTML blocks of kinds 1 to 6. */
    private static final Pattern OPENS_HTML_BLOCK = Pattern.compile(
            "^<(?:(?:script|pre|style|textarea)(?:[ \\t>]|$)|!--|\\?|![a-z]|!\\[CDATA\\[|\\/?(?:address|article|aside|base"
                    + "|basefont|blockquote|body|caption|center|col|colgroup|dd|details|dialog|dir|div|dl|dt|fieldset"
                    + "|figcaption|figure|footer|form|frame|frameset|h[1-6]|head|header|hr|html|iframe|legend|li|link|main"
                    + "|menu|menuitem|nav|noframes|ol|optgroup|option|p|param|search|section|summary|table|tbody|td|tfoot"
                    + "|th|thead|title|tr|track|ul)(?:[ \\t>]|\\/>|$))", Pattern.CASE_INSENSITIVE);
    /** 2.1.1's {@code HTML_TAG_ALONE}: a complete open or closing tag alone on its line (kind 7). */
    private static final Pattern HTML_TAG_ALONE = Pattern.compile(
            "^(?:<[A-Za-z][A-Za-z0-9-]*(?:\\s+[A-Za-z_:][\\w.:-]*(?:\\s*=\\s*(?:[^\\s\"'=<>`]+|'[^']*'|\"[^\"]*\"))?)*"
                    + "\\s*\\/?>|<\\/[A-Za-z][A-Za-z0-9-]*\\s*>)\\s*$", FLAGS);

    /** The lines {@code parts} holds that a retirement can't account for, first occurrence first, each once. */
    static List<String> leftovers(SpecMerge.Parts parts, Variant variant) {
        LinkedHashSet<String> leftovers = new LinkedHashSet<>();
        before(parts.before(), leftovers);
        for (String slice : new String[] {parts.preamble(), parts.after()}) {
            for (String line : slice.split("\n", -1)) {
                if (!line.isBlank()) leftovers.add(line.strip());
            }
        }
        for (SpecMerge.Block block : parts.blocks()) {
            String tail = firstForeignTail(block.raw());
            if (tail != null) leftovers.add(tail);
            List<String> lines = Arrays.asList(normalize(block.raw()).split("\n", -1));
            boolean[] mask = CodeFences.mask(lines);
            if (variant == Variant.A) {
                blockA(lines, mask, leftovers);
            } else {
                blockBC(lines, mask, variant, leftovers);
            }
        }
        return List.copyOf(leftovers);
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void before(String before, LinkedHashSet<String> leftovers) {
        String text = before.startsWith("﻿") ? before.substring(1) : before;
        List<String> lines = Arrays.asList(normalize(text).split("\n", -1));
        boolean[] mask = CodeFences.mask(lines);
        boolean inPurpose = false;
        boolean titleSeen = false;
        String previous = "";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                previous = "";
                continue;
            }
            if (!mask[i]) {
                Matcher section = SECTION.matcher(line);
                if (section.find()) {
                    inPurpose = PURPOSE.matcher(section.group(1).strip()).find();
                    if (!inPurpose) leftovers.add(line.strip());
                    previous = line;
                    continue;
                }
                boolean setext = inPurpose && !previous.isBlank() && SETEXT.matcher(line).find();
                boolean htmlHeading = HTML_HEADING.matcher(line).find();
                if (setext || htmlHeading) {
                    leftovers.add((setext ? previous : line).strip());
                    inPurpose = false;
                    previous = line;
                    continue;
                }
                if (TITLE.matcher(line).find()) {
                    if (!titleSeen && !inPurpose) {
                        titleSeen = true;
                    } else {
                        leftovers.add(line.strip());
                        inPurpose = false;
                    }
                    previous = line;
                    continue;
                }
            }
            previous = line;
            if (inPurpose) continue;
            leftovers.add(line.strip());
        }
    }

    /** Upstream's {@code firstForeignTail}: the first unfenced {@code #}/{@code ##}/{@code ###} line after the header. */
    private static @Nullable String firstForeignTail(String raw) {
        List<String> lines = Arrays.asList(normalize(raw).split("\n", -1));
        boolean[] mask = CodeFences.mask(lines);
        for (int i = 1; i < lines.size(); i++) {
            if (mask[i]) continue;
            if (FOREIGN_HEADING.matcher(lines.get(i)).find()) return lines.get(i).strip();
        }
        return null;
    }

    private static void blockA(List<String> lines, boolean[] mask, LinkedHashSet<String> leftovers) {
        boolean seenScenario = false;
        boolean inScenarioBullets = false;
        boolean bulletsSeen = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                if (bulletsSeen) inScenarioBullets = false;
                continue;
            }
            if (i == 0) continue;
            if (mask[i]) continue;
            if (i > 1 && SETEXT_UNDERLINE.matcher(line).find() && !lines.get(i - 1).isBlank()) {
                leftovers.add(lines.get(i - 1).strip());
                continue;
            }
            if (SCENARIO.matcher(line).find()) {
                seenScenario = true;
                inScenarioBullets = true;
                bulletsSeen = false;
                continue;
            }
            if (BULLET_A.matcher(line).find()) {
                if (inScenarioBullets) {
                    bulletsSeen = true;
                    continue;
                }
                if (!seenScenario) continue;
                leftovers.add(line.strip());
                continue;
            }
            if (!seenScenario && !STATEMENT_BREAK_A.matcher(line).find()) continue;
            leftovers.add(line.strip());
        }
    }

    private static void blockBC(List<String> lines, boolean[] mask, Variant variant, LinkedHashSet<String> leftovers) {
        boolean seenScenario = false;
        boolean inScenarioBullets = false;
        boolean bulletsSeen = false;
        Integer listContentIndent = null;
        boolean paragraphOpen = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                if (bulletsSeen) inScenarioBullets = false;
                listContentIndent = null;
                paragraphOpen = false;
                continue;
            }
            if (i == 0) continue;
            Matcher ws = LEADING_WS.matcher(line);
            ws.find();
            int indent = contentColumn(ws.group());
            boolean insideItem = listContentIndent != null && indent >= listContentIndent;
            String withinItem = insideItem ? dropIndent(line, listContentIndent) : line;
            boolean lazilyContinuesBullet = paragraphOpen && inScenarioBullets && !interruptsParagraph(withinItem, variant);
            boolean continuesListItem = (insideItem || lazilyContinuesBullet) && !isHeadingLine(withinItem);
            if (mask[i]) {
                if (!insideItem) listContentIndent = null;
                paragraphOpen = false;
                continue;
            }
            if (i > 1 && SETEXT_UNDERLINE.matcher(withinItem).find() && !lines.get(i - 1).isBlank()) {
                leftovers.add(lines.get(i - 1).strip());
                listContentIndent = null;
                paragraphOpen = false;
                continue;
            }
            if (continuesListItem) {
                paragraphOpen = !interruptsParagraph(withinItem, variant);
                continue;
            }
            Matcher bullet = LIST_ITEM.matcher(line);
            boolean isBullet = bullet.find();
            listContentIndent = isBullet ? contentColumn(bullet.group(1)) : null;
            paragraphOpen = isBullet;
            if (SCENARIO.matcher(line).find()) {
                seenScenario = true;
                inScenarioBullets = true;
                bulletsSeen = false;
                continue;
            }
            if (isBullet) {
                if (inScenarioBullets) {
                    bulletsSeen = true;
                    continue;
                }
                if (!seenScenario) continue;
                leftovers.add(line.strip());
                continue;
            }
            boolean statementBreak = variant == Variant.C
                    ? TABLE_ROW.matcher(line).find() || mayBeRawHtml(line)
                    : STATEMENT_BREAK_A.matcher(line).find();
            if (!seenScenario && !statementBreak) continue;
            leftovers.add(line.strip());
        }
    }

    /** The visual column a prefix's content starts at, tabs to a four-column stop. */
    static int contentColumn(String prefix) {
        int column = 0;
        for (char c : prefix.toCharArray()) column += c == '\t' ? 4 - (column % 4) : 1;
        return column;
    }

    /** Drops up to {@code columns} visual columns of leading whitespace; a straddling tab goes whole. */
    static String dropIndent(String line, int columns) {
        int column = 0;
        int index = 0;
        while (index < line.length() && column < columns) {
            char c = line.charAt(index);
            if (c == ' ') column += 1;
            else if (c == '\t') column += 4 - (column % 4);
            else break;
            index++;
        }
        return line.substring(index);
    }

    private static boolean isHeadingLine(String line) {
        return HEADING_LINE.matcher(line).find() || HEADING_HTML.matcher(line).find();
    }

    private static boolean interruptsParagraph(String line, Variant variant) {
        if (variant != Variant.C) return INTERRUPTS_PARAGRAPH_B.matcher(line).find();
        return INTERRUPTS_PARAGRAPH_C.matcher(line).find() || (LT_INDENTED.matcher(line).find() && mayBeRawHtml(line));
    }

    /** 2.1.1's {@code mayBeRawHtml}. */
    static boolean mayBeRawHtml(String line) {
        String text = line.strip();
        return OPENS_HTML_BLOCK.matcher(text).find() || HTML_TAG_ALONE.matcher(text).find();
    }

    /**
     * A leftovers list as the release words it: the first three, quoted, then how many more. From 1.11 each line's
     * control characters become {@code ?} and a line over 200 characters is cut with an ellipsis.
     */
    static String describe(List<String> leftovers, @Nullable String release) {
        boolean sanitized = release == null || CliVersion.atLeast(release, "1.11.0");
        List<String> quoted = new ArrayList<>();
        for (String l : leftovers.subList(0, Math.min(3, leftovers.size()))) {
            String shown = l;
            if (sanitized) {
                shown = l.replaceAll("[\\u0000-\\u001f\\u007f]", "?");
                int[] cps = shown.codePoints().toArray();
                if (cps.length > 200) shown = new String(cps, 0, 200) + "\u2026";
            }
            quoted.add("\"" + shown + "\"");
        }
        String list = String.join(", ", quoted);
        if (leftovers.size() > 3) list += ", and " + (leftovers.size() - 3) + " more line(s)";
        return list;
    }
}

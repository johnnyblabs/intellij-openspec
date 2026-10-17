package com.johnnyblabs.openspec.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fenced-code-block detection matching OpenSpec's archive ({@code parsers/code-fence.js}): a fence opens with three or
 * more backticks or tildes at any indentation, and closes on a line holding only the same character, at least as many.
 * An unclosed fence runs to the end of the text. Spec structure inside a fence (headers, requirement and scenario lines)
 * is example text, never structure.
 */
public final class CodeFences {

    private static final Pattern OPENER = Pattern.compile("^\\s*(`{3,}|~{3,})");
    private static final Pattern CLOSER = Pattern.compile("^\\s*(`{3,}|~{3,})\\s*$");

    private CodeFences() {
    }

    /** Per line: {@code true} when the line is part of a fence, the opening and closing fence lines included. */
    public static boolean[] mask(List<String> lines) {
        boolean[] mask = new boolean[lines.size()];
        char marker = 0;
        int length = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (marker == 0) {
                Matcher open = OPENER.matcher(line);
                if (open.find()) {
                    marker = open.group(1).charAt(0);
                    length = open.group(1).length();
                    mask[i] = true;
                }
                continue;
            }
            mask[i] = true;
            Matcher close = CLOSER.matcher(line);
            if (close.matches() && close.group(1).charAt(0) == marker && close.group(1).length() >= length) {
                marker = 0;
            }
        }
        return mask;
    }
}

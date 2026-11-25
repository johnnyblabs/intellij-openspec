package com.johnnyblabs.openspec.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A requirement's text as OpenSpec measures it, a port of upstream's {@code extractRequirementBody}
 * ({@code dist/core/parsers/requirement-text.js}). Every length rule reads it: the too-long warning (1.14.1) and the
 * MODIFIED growth warning (2.1.1).
 * <ul>
 *   <li>the lines after the {@code ### Requirement:} header, up to the first {@code #}–{@code ######} heading on a
 *       line outside a fence (usually the first scenario);</li>
 *   <li>blank and fenced lines dropped, each line trimmed, joined with {@code \n};</li>
 *   <li>{@code **Key**:} metadata lines dropped, unless they are the whole body;</li>
 *   <li>HTML comments are ordinary text and count.</li>
 * </ul>
 */
public final class RequirementText {

    /** Upstream's {@code MAX_REQUIREMENT_TEXT_LENGTH}: a text longer than this is "very long". */
    public static final int MAX_LENGTH = 500;

    private static final Pattern METADATA_LINE = Pattern.compile("^\\*\\*[^*]+\\*\\*:");
    private static final Pattern HEADER_LINE = Pattern.compile("^#{1,6}\\s");

    private RequirementText() {
    }

    /** The text of a requirement block whose first line is its header. */
    public static String ofBlock(String blockRaw) {
        List<String> lines = List.of(blockRaw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
        return body(lines.subList(Math.min(1, lines.size()), lines.size()));
    }

    /** Upstream's {@code extractRequirementBody} over the lines after a requirement header. */
    public static String body(List<String> bodyLines) {
        boolean[] mask = CodeFences.mask(bodyLines);
        List<String> captured = new ArrayList<>();
        List<String> metadata = new ArrayList<>();
        for (int i = 0; i < bodyLines.size(); i++) {
            if (mask[i]) continue;
            String line = bodyLines.get(i);
            if (HEADER_LINE.matcher(line).find()) break;
            String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;
            if (METADATA_LINE.matcher(trimmed).find()) {
                metadata.add(trimmed);
                continue;
            }
            captured.add(trimmed);
        }
        return String.join("\n", captured.isEmpty() ? metadata : captured);
    }

    /** Whether a requirement's text is over the limit. */
    public static boolean tooLong(String text) {
        return text.length() > MAX_LENGTH;
    }
}

package com.johnnyblabs.openspec.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes task lines in a tasks file exactly as the OpenSpec client does (1.13.1+), so every plugin
 * surface that counts tasks — the change node's {@code X/Y}, the workflow panel, Apply, Verify — reports
 * the same completed/total numbers as {@code openspec list --json}.
 *
 * <p>Port of the client's {@code TASK_LINE_PATTERN} with the JavaScript semantics preserved:
 * <ul>
 *   <li>JS {@code \s} is wider than Java's: it also covers the BOM, NBSP and the Unicode space separators,
 *       so it is spelled out as an explicit class (a BOM-prefixed first line still counts).</li>
 *   <li>JS {@code \d} is ASCII, hence {@code [0-9]} (not Unicode digits).</li>
 *   <li>Lines split on {@code \n} only — a lone-{@code \r} file is one line to the client — and matching
 *       uses {@code lookingAt}, never {@code matches}, so a trailing {@code \r} (CRLF) is tolerated.</li>
 *   <li>Like the client, checkboxes inside code fences or indented blocks still count.</li>
 * </ul>
 * A task is done only when its mark is {@code x}/{@code X}; any other mark (none, {@code ~}, …) is not done.
 */
public final class TaskLines {

    private static final String WS =
            "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF";

    private static final Pattern TASK_LINE = Pattern.compile(
            "^[" + WS + "]*(?:[-*+]|[0-9]{1,9}[.)])[" + WS + "]*\\[(?:[" + WS + "]*([^\\]" + WS + "]?)["
                    + WS + "]*\\](?![(\\[])|[" + WS + "]+\\])[" + WS + "]*(.*)");

    /** One recognized task line; {@code mark} is the box's single character, or empty for {@code []}/{@code [ ]}. */
    public record Task(String mark, boolean done) {
        public boolean inProgress() {
            return "~".equals(mark);
        }
    }

    private TaskLines() {
    }

    /** Every task line in {@code content}, in document order. */
    public static List<Task> parse(String content) {
        List<Task> tasks = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return tasks;
        }
        for (String line : content.split("\n", -1)) {
            Matcher m = TASK_LINE.matcher(line);
            if (m.lookingAt()) {
                String mark = m.group(1) == null ? "" : m.group(1);
                tasks.add(new Task(mark, "x".equalsIgnoreCase(mark)));
            }
        }
        return tasks;
    }

    /** {@code [completed, total]}, matching the client's {@code completedTasks}/{@code totalTasks}. */
    public static int[] count(String content) {
        List<Task> tasks = parse(content);
        int done = (int) tasks.stream().filter(Task::done).count();
        return new int[]{done, tasks.size()};
    }
}

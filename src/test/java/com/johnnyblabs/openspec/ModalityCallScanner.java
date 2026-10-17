package com.johnnyblabs.openspec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source scan used by the modality guards: finds {@code invokeLater(...)} / {@code invokeAndWait(...)} calls and
 * whether each passes a second argument (a modality). A one-argument call from a background thread is NON_MODAL, so
 * inside a modal dialog the platform holds it until the dialog closes. Comments, string and char literals and text
 * blocks are blanked out first, so documentation that names these methods is never counted.
 */
public final class ModalityCallScanner {

    private static final Pattern CALL = Pattern.compile("\\b(invokeLater|invokeAndWait)\\s*\\(");

    /** One call site: the method name, its 1-based line, and its argument list (comments/literals blanked). */
    public record Call(String method, int line, String args) {
        public boolean hasSecondArgument() {
            int depth = 0;
            for (int i = 0; i < args.length(); i++) {
                char c = args.charAt(i);
                if (c == '(' || c == '{' || c == '[') {
                    depth++;
                } else if (c == ')' || c == '}' || c == ']') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private ModalityCallScanner() {
    }

    public static Path mainSource(String relative) {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/java/com/johnnyblabs/openspec"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("could not locate the repository root from " + Paths.get("").toAbsolutePath());
        }
        return dir.resolve("src/main/java/com/johnnyblabs/openspec").resolve(relative);
    }

    public static List<Call> calls(Path source) throws IOException {
        return calls(Files.readString(source));
    }

    /** Every invokeLater/invokeAndWait call in {@code source}, ignoring comments, literals and method references. */
    public static List<Call> calls(String source) {
        String code = strip(source);
        List<Call> calls = new ArrayList<>();
        Matcher m = CALL.matcher(code);
        while (m.find()) {
            int open = m.end() - 1;
            int close = matchingParen(code, open);
            int line = 1 + (int) code.substring(0, m.start()).chars().filter(ch -> ch == '\n').count();
            calls.add(new Call(m.group(1), line, code.substring(open + 1, close)));
            m.region(close, code.length());
        }
        return calls;
    }

    /** Whether {@code token} appears in the code itself (not in a comment or literal). */
    public static boolean codeContains(String source, String token) {
        return strip(source).contains(token);
    }

    private static final Pattern NEW_LATCH = Pattern.compile("\\bnew\\s+(?:[\\w.]+\\.)?CountDownLatch\\b");

    /** Whether the code (not comments or literals) constructs a {@code CountDownLatch}, simple or fully qualified. */
    public static boolean constructsLatch(String source) {
        return NEW_LATCH.matcher(strip(source)).find();
    }

    private static int matchingParen(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("unbalanced call at offset " + open);
    }

    /** Blanks comments, string/char literals and text blocks to spaces, keeping newlines so line numbers survive. */
    static String strip(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (s.startsWith("//", i)) {
                while (i < s.length() && s.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (s.startsWith("/*", i)) {
                int end = s.indexOf("*/", i + 2);
                end = end < 0 ? s.length() : end + 2;
                blank(s, i, end, out);
                i = end;
            } else if (s.startsWith("\"\"\"", i)) {
                int end = s.indexOf("\"\"\"", i + 3);
                end = end < 0 ? s.length() : end + 3;
                blank(s, i, end, out);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) != c && s.charAt(j) != '\n') {
                    j += s.charAt(j) == '\\' ? 2 : 1;
                }
                int end = Math.min(j + 1, s.length());
                blank(s, i, end, out);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void blank(String s, int from, int to, StringBuilder out) {
        for (int k = from; k < to; k++) {
            out.append(s.charAt(k) == '\n' ? '\n' : ' ');
        }
    }
}

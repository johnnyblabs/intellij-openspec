package com.johnnyblabs.openspec.settings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every {@code invokeLater} in {@link OpenSpecSettingsPanel} must pass the Settings dialog's modality. The panel always
 * lives in the modal Settings dialog; a plain {@code invokeLater} from a background thread is NON_MODAL, so the platform
 * holds it until the dialog closes and the update lands on a panel nobody can see (the empty schema list). The headless
 * dialog test proves the behaviour for the schema list; this scan keeps every other site from regressing.
 */
class SettingsPanelModalityHygieneTest {

    private static Path panelSource() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/java/com/johnnyblabs/openspec"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "could not locate the repository root from " + Paths.get("").toAbsolutePath());
        return dir.resolve("src/main/java/com/johnnyblabs/openspec/settings/OpenSpecSettingsPanel.java");
    }

    /**
     * The argument lists of every {@code invokeLater(...)} call in {@code source}, each as the text between its
     * parentheses. Brackets inside string and char literals are ignored, and nesting is tracked, so a lambda body full
     * of calls and commas stays one argument.
     */
    static List<String> invokeLaterArguments(String source) {
        List<String> calls = new ArrayList<>();
        int from = 0;
        while (true) {
            int at = source.indexOf("invokeLater(", from);
            if (at < 0) {
                return calls;
            }
            int open = at + "invokeLater".length();
            int close = matchingParen(source, open);
            calls.add(source.substring(open + 1, close));
            from = close;
        }
    }

    /** Whether an argument list has a comma at nesting depth zero, i.e. a second argument. */
    static boolean hasSecondArgument(String args) {
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(args, i);
            } else if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return true;
            }
        }
        return false;
    }

    private static int matchingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(s, i);
            } else if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("unbalanced invokeLater call at offset " + open);
    }

    /** Index of the closing quote of the literal starting at {@code start} (handles escapes). */
    private static int skipLiteral(String s, int start) {
        char quote = s.charAt(start);
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return s.length() - 1;
    }

    @Test
    void everyInvokeLaterInTheSettingsPanelPassesAModality() throws IOException {
        String source = Files.readString(panelSource());
        List<String> calls = invokeLaterArguments(source);
        // Anti-vacuity: the panel has many background-result sites; the scan must actually find them.
        assertTrue(calls.size() >= 9, "expected the settings panel's invokeLater calls, found " + calls.size());

        List<String> missing = calls.stream().filter(args -> !hasSecondArgument(args))
                .map(args -> args.strip().lines().findFirst().orElse("")).toList();
        assertTrue(missing.isEmpty(),
                "invokeLater without the dialog's modality (capture dialogModality() on the EDT and pass it): " + missing);
    }

    @Test
    void theScanTellsOneArgumentFromTwo() {
        List<String> one = invokeLaterArguments("app.invokeLater(() -> applyView(view));");
        assertEquals(1, one.size());
        assertFalse(hasSecondArgument(one.get(0)), "a one-argument call is flagged");

        assertTrue(hasSecondArgument(invokeLaterArguments("app.invokeLater(() -> applyView(view), modality);").get(0)));

        String lambdaWithCommas = """
                app.invokeLater(() -> {
                    show(a, b);
                    String s = "x, y)";
                    char c = ',';
                    int[] xs = {1, 2};
                });""";
        assertFalse(hasSecondArgument(invokeLaterArguments(lambdaWithCommas).get(0)),
                "commas inside the lambda body, a string, a char and an array initialiser are not a second argument");

        assertTrue(hasSecondArgument(invokeLaterArguments(lambdaWithCommas.replace("});", "}, modality);")).get(0)));
    }
}

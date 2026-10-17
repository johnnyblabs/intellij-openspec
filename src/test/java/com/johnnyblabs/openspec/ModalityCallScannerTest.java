package com.johnnyblabs.openspec;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Locks the modality guards' scanner: argument counting, nesting, literals and comments. */
class ModalityCallScannerTest {

    private static ModalityCallScanner.Call only(String source) {
        List<ModalityCallScanner.Call> calls = ModalityCallScanner.calls(source);
        assertEquals(1, calls.size(), "calls in: " + source);
        return calls.get(0);
    }

    @Test
    void oneArgumentIsFlaggedAndTwoAreNot() {
        assertFalse(only("app.invokeLater(() -> apply(view));").hasSecondArgument());
        assertTrue(only("app.invokeLater(() -> apply(view), modality);").hasSecondArgument());
        assertFalse(only("app.invokeAndWait(r);").hasSecondArgument());
        assertTrue(only("app.invokeAndWait(r, ModalityState.defaultModalityState());").hasSecondArgument());
    }

    @Test
    void commasInsideTheLambdaOrLiteralsAreNotASecondArgument() {
        String body = """
                app.invokeLater(() -> {
                    show(a, b);
                    String s = "x, y)";
                    char c = ',';
                    int[] xs = {1, 2};
                    s.split(",");
                });""";
        assertFalse(only(body).hasSecondArgument());
        assertTrue(only(body.replace("});", "}, modality);")).hasSecondArgument());
    }

    @Test
    void commentsJavadocAndStringsAreNotCalls() {
        String source = """
                // invokeLater(r) in a line comment
                /** {@code invokeAndWait(runnable)} in javadoc */
                String note = "invokeLater(r)";
                app.invokeLater(r, modality);
                """;
        List<ModalityCallScanner.Call> calls = ModalityCallScanner.calls(source);
        assertEquals(1, calls.size());
        assertEquals(4, calls.get(0).line(), "line numbers survive the stripping");
        assertTrue(ModalityCallScanner.constructsLatch("var l = new CountDownLatch(1);"));
        assertTrue(ModalityCallScanner.constructsLatch("var l = new java.util.concurrent.CountDownLatch(1);"));
        assertFalse(ModalityCallScanner.constructsLatch("// replaced the old new CountDownLatch(1) hand-off"));
    }
}

package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The fence rules OpenSpec's archive uses (parsers/code-fence.js). */
class CodeFencesTest {

    private static String masked(String text) {
        List<String> lines = List.of(text.split("\n", -1));
        boolean[] mask = CodeFences.mask(lines);
        assertEquals(lines.size(), mask.length, "one flag per line");
        StringBuilder out = new StringBuilder();
        for (boolean m : mask) out.append(m ? 'F' : '.');
        return out.toString();
    }

    @Test
    void backtickFenceMasksItsLinesAndBothFenceLines() {
        assertEquals(".FFF.", masked("a\n```\n## X\n```\nb"));
    }

    @Test
    void tildeFenceAndIndentedFence() {
        assertEquals(".FFF.", masked("a\n~~~\n## X\n~~~\nb"));
        assertEquals(".FFF.", masked("a\n   ```md\n## X\n   ```\nb"), "any indentation opens and closes");
    }

    @Test
    void closerMustBeTheSameCharacterAndAtLeastAsLong() {
        assertEquals(".FFFFF.", masked("a\n~~~~\n```\n~~~\n## X\n~~~~\nb"), "a different char and a shorter run don't close");
        assertEquals(".FFFF.", masked("a\n```\n~~~\n## X\n```\nb"), "a long-enough run of the other char doesn't close");
        assertEquals(".FFF.", masked("a\n```\nx\n`````\nb"), "a longer closer does close");
    }

    @Test
    void closerHoldsOnlyTheFence() {
        assertEquals(".FFFF", masked("a\n```\nx\n``` not a closer\n## X"));
    }

    @Test
    void unclosedFenceRunsToTheEnd() {
        assertEquals(".FFF", masked("a\n```text\n## Trailing\n### Requirement: Fake"));
    }
}

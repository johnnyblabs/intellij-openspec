package com.johnnyblabs.openspec.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract: the plugin's task counts equal the OpenSpec client's for the same {@code tasks.md}.
 *
 * <p>Expected values come only from captured real 1.13.2 {@code openspec list --json} output
 * ({@code fixtures/cli/1.13.2/task-grammar/} and {@code task-markers/}), never from hand-written counts.
 * The task-grammar inputs keep their raw CRLF / BOM / lone-CR bytes ({@code .gitattributes} {@code -text}).
 */
class TaskLinesContractTest {

    private static final String BASE = "/fixtures/cli/1.13.2/";

    /** change name → {completed, total, tasks.md raw content}, for every captured control project. */
    private static Map<String, Object[]> capturedControls() throws IOException {
        Map<String, Object[]> controls = new LinkedHashMap<>();
        for (String project : List.of("task-grammar", "task-markers")) {
            JsonObject list = JsonParser.parseString(read(BASE + project + "/list.json")).getAsJsonObject();
            for (var c : list.getAsJsonArray("changes")) {
                JsonObject change = c.getAsJsonObject();
                String name = change.get("name").getAsString();
                String tasks = read(BASE + project + "/openspec/changes/" + name + "/tasks.md");
                controls.put(project + "/" + name, new Object[]{
                        change.get("completedTasks").getAsInt(), change.get("totalTasks").getAsInt(), tasks});
            }
        }
        return controls;
    }

    /** Raw bytes as UTF-8, deliberately NOT line-ending normalized: the line endings are the input. */
    private static String read(String path) throws IOException {
        try (InputStream in = TaskLinesContractTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing fixture " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void taskLinesCountsEqualTheClientsForEveryCapturedControl() throws IOException {
        Map<String, Object[]> controls = capturedControls();
        List<String> mismatches = new ArrayList<>();
        controls.forEach((name, c) -> {
            int[] counts = TaskLines.count((String) c[2]);
            if (counts[0] != (int) c[0] || counts[1] != (int) c[1]) {
                mismatches.add(name + ": plugin " + counts[0] + "/" + counts[1] + " vs CLI " + c[0] + "/" + c[1]);
            }
        });
        assertTrue(mismatches.isEmpty(), "task counts diverge from the client: " + mismatches);
    }

    @Test
    void applyPromptBuilderCountTasksEqualsTheClient() throws IOException {
        // countTasks feeds the change node X/Y, the workflow panel and Apply.
        capturedControls().forEach((name, c) -> {
            int[] counts = ApplyPromptBuilder.countTasks((String) c[2]);
            assertArrayEquals(new int[]{(int) c[0], (int) c[1]}, counts, name);
        });
    }

    @Test
    void lfControlsCountTheSameWhenConvertedToCrlf() throws IOException {
        capturedControls().forEach((name, c) -> {
            String tasks = (String) c[2];
            if (tasks.contains("\r")) {
                return; // already a line-ending control (t-crlf, t-cr-only)
            }
            assertArrayEquals(TaskLines.count(tasks), TaskLines.count(tasks.replace("\n", "\r\n")), name);
        });
    }

    @Test
    void controlSetIsNotVacuous() throws IOException {
        Map<String, Object[]> controls = capturedControls();
        for (String required : List.of("task-grammar/t-bom", "task-grammar/t-cr-only", "task-grammar/t-links",
                "task-grammar/t-crlf", "task-markers/c")) {
            assertTrue(controls.containsKey(required), "missing captured control " + required);
        }
        // At least one control the pre-1.13 "- [ ]"/"- [x]" prefix count gets wrong, so a regression to it fails.
        boolean exercisesTheWiderGrammar = controls.values().stream().anyMatch(c -> {
            long legacy = ((String) c[2]).lines().map(String::trim)
                    .filter(l -> l.startsWith("- [ ]") || l.startsWith("- [x]") || l.startsWith("- [X]")).count();
            return (int) c[1] > legacy;
        });
        assertTrue(exercisesTheWiderGrammar, "controls must include a total the legacy prefix count misses");
    }

    @Test
    void aNullMarkIsNotDone() {
        // `- [ ](x)` takes the client's whitespace-only-box branch, where the mark group is absent.
        List<TaskLines.Task> tasks = TaskLines.parse("- [ ](x)\n");
        assertEquals(1, tasks.size());
        assertFalse(tasks.getFirst().done());
        assertEquals("", tasks.getFirst().mark());
    }
}

package com.johnnyblabs.openspec.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.SpecMerge;
import com.johnnyblabs.openspec.util.FrontMatter;
import com.johnnyblabs.openspec.util.RequirementText;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ports behind the release-gated validator rules, each held to the real CLI's verdicts on the 2.x parity corpus
 * ({@code fixtures/cli/2.1.1/parity-corpus-2x/}, judged in {@code 2.1.1/validate-parity-corpus-2x.json}): the
 * requirement-text measure, the scenario step check and front matter. The expected answers come from the capture.
 */
class ValidatorRulePortsContractTest {

    static final Path CORPUS = Path.of("src/test/resources/fixtures/cli/2.1.1/parity-corpus-2x/openspec");

    static Map<String, JsonObject> captured() throws IOException {
        JsonObject j = JsonParser.parseString(Files.readString(
                Path.of("src/test/resources/fixtures/cli/2.1.1/validate-parity-corpus-2x.json"))).getAsJsonObject();
        Map<String, JsonObject> items = new TreeMap<>();
        for (JsonElement e : j.getAsJsonArray("items")) items.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject());
        return items;
    }

    static boolean hasMessage(JsonObject item, String text) {
        for (JsonElement e : item.getAsJsonArray("issues")) {
            if (e.getAsJsonObject().get("message").getAsString().contains(text)) return true;
        }
        return false;
    }

    /** The one requirement block of a corpus main spec (header line included, up to the next section). */
    static String block(String spec) throws IOException {
        String text = Files.readString(CORPUS.resolve("specs").resolve(spec).resolve("spec.md"));
        int at = text.indexOf("### Requirement:");
        // The block ends at the next `##` section, as OpenSpec reads it.
        java.util.regex.Matcher next = java.util.regex.Pattern.compile("(?m)^##(?!#)").matcher(text);
        int end = next.find(at) ? next.start() : text.length();
        return text.substring(at, end);
    }

    // --- the requirement-text measure ---

    @Test
    void theMeasureSitsOnTheCapturedThreshold() throws IOException {
        Map<String, JsonObject> items = captured();
        Matcher m = Pattern.compile(">(\\d+) characters").matcher(items.get("len-501").getAsJsonArray("issues")
                .get(0).getAsJsonObject().get("message").getAsString());
        assertTrue(m.find(), "the captured too-long message names its threshold");
        int threshold = Integer.parseInt(m.group(1));
        assertEquals(threshold, RequirementText.MAX_LENGTH);
        assertEquals(threshold, RequirementText.ofBlock(block("len-500")).length());
        assertEquals(threshold + 1, RequirementText.ofBlock(block("len-501")).length());
        assertEquals(threshold + 1, RequirementText.ofBlock(block("len-two-lines")).length(),
                "two 250-character lines joined by a newline");
        for (String spec : List.of("len-500", "len-501", "len-two-lines", "len-comment", "len-metadata-only",
                "len-metadata-plus-text", "len-h5-break")) {
            boolean warns = hasMessage(items.get(spec), "Requirement text is very long");
            assertEquals(warns, RequirementText.tooLong(RequirementText.ofBlock(block(spec))), spec);
        }
    }

    // --- the step check ---

    @Test
    void eachStepFormCountsAsTheCliCountsIt() throws IOException {
        Map<String, JsonObject> items = captured();
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (String id : items.keySet()) {
            if (!id.startsWith("step-") || !"spec".equals(items.get(id).get("type").getAsString())) continue;
            boolean cliTrigger = hasMessage(items.get(id), "a WHEN/GIVEN step");
            boolean cliOutcome = hasMessage(items.get(id), "a THEN step");
            List<ScenarioSteps.Missing> missing = ScenarioSteps.missing(block(id));
            boolean trigger = missing.stream().anyMatch(ScenarioSteps.Missing::trigger);
            boolean outcome = missing.stream().anyMatch(ScenarioSteps.Missing::outcome);
            if (trigger != cliTrigger || outcome != cliOutcome) {
                wrong.add(id + ": CLI trigger " + cliTrigger + " outcome " + cliOutcome + ", plugin " + missing);
            }
            checked++;
        }
        assertTrue(checked >= 14, "sanity: step specs " + checked);
        assertEquals(List.of(), wrong);
    }

    // --- front matter ---

    @Test
    void frontMatterIsWhatTwoOneOneAccepts() throws IOException {
        Map<String, JsonObject> items = captured();
        int checked = 0;
        for (String id : items.keySet()) {
            if (!id.startsWith("fm-")) continue;
            Path delta;
            try (var specs = Files.walk(CORPUS.resolve("changes").resolve(id).resolve("specs"))) {
                delta = specs.filter(p -> p.getFileName().toString().equals("spec.md")).findFirst().orElseThrow();
            }
            String raw = new String(Files.readAllBytes(delta), StandardCharsets.UTF_8);
            boolean accepted = !hasMessage(items.get(id), "Text above the first");
            assertEquals(accepted, FrontMatter.present(raw), id);
            checked++;
        }
        assertTrue(checked >= 10, "sanity: front-matter items " + checked);
    }

    @Test
    void theByteOrderMarkAndCrlfCaseReallyHasThem() throws IOException {
        Path delta = CORPUS.resolve("changes/fm-bom-crlf/specs/demo-bom/spec.md");
        byte[] bytes = Files.readAllBytes(delta);
        assertEquals((byte) 0xEF, bytes[0], "the fixture keeps its byte-order mark");
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("\r\n"), "the fixture keeps CRLF endings");
    }

    @Test
    void blankingKeepsLineNumbers() {
        String delta = "---\nowner: a\n---\n\n## ADDED Requirements\n";
        String blanked = FrontMatter.blank(delta);
        assertEquals(delta.split("\n", -1).length, blanked.split("\n", -1).length);
        assertTrue(blanked.startsWith("\n\n\n\n## ADDED"), blanked);
    }

    // --- MODIFIED growth (SpecMerge) ---

    static final String LONG = "The system SHALL " + "grow ".repeat(110) + "x.";
    static final String MAIN = "# demo Specification\n\n## Purpose\nA demo.\n\n## Requirements\n\n"
            + "### Requirement: Old name\nThe system SHALL be short.\n\n#### Scenario: ok\n- **WHEN** x\n- **THEN** y\n";

    @Test
    void growthFollowsRenamesAndSkipsRenamedAwayNames() {
        String renamed = "## RENAMED Requirements\n\n- FROM: `### Requirement: Old name`\n- TO: `### Requirement: New name`\n\n"
                + "## MODIFIED Requirements\n\n### Requirement: New name\n" + LONG + "\n\n#### Scenario: ok\n- **WHEN** x\n- **THEN** y\n";
        assertEquals(List.of("New name"), SpecMerge.modifiedGrowth(renamed, MAIN).stream().map(SpecMerge.Growth::name).toList());
        String away = renamed.replace("### Requirement: New name\n" + LONG, "### Requirement: Old name\n" + LONG);
        assertEquals(List.of(), SpecMerge.modifiedGrowth(away, MAIN), "a MODIFIED naming the renamed-away name is skipped");
        String unknown = "## MODIFIED Requirements\n\n### Requirement: Missing\n" + LONG + "\n\n#### Scenario: ok\n- **WHEN** x\n- **THEN** y\n";
        assertEquals(List.of(), SpecMerge.modifiedGrowth(unknown, MAIN), "no current requirement: no check");
    }

    @Test
    void mergingNeverCarriesADeltasFrontMatter() {
        String delta = "---\nowner: team-a\n---\n\n## ADDED Requirements\n\n### Requirement: Added\nThe system SHALL add.\n\n"
                + "#### Scenario: ok\n- **WHEN** x\n- **THEN** y\n";
        SpecMerge.Result result = SpecMerge.merge("demo", "c", delta, MAIN);
        assertNull(result.refusal(), String.valueOf(result.refusal()));
        assertFalse(result.projected().contains("owner: team-a"), result.projected());
        assertTrue(result.projected().contains("### Requirement: Added"));
    }

    @Test
    void aMainSpecsPurposeIsReadPastItsFrontMatter() {
        String main = "---\nowner: team-a\n---\n\n" + MAIN;
        String delta = "## ADDED Requirements\n\n### Requirement: Added\nThe system SHALL add.\n\n#### Scenario: ok\n- **WHEN** x\n- **THEN** y\n";
        SpecMerge.Result result = SpecMerge.merge("demo", "c", delta, main);
        assertNull(result.refusal());
        assertTrue(result.projected().contains("## Purpose\nA demo."), result.projected());
        assertTrue(result.projected().startsWith("---\nowner: team-a\n---"), "the main spec keeps its own front matter");
    }
}

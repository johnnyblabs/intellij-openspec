package com.johnnyblabs.openspec.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contract: on rename-only and unpaired-rename deltas, the built-in validator raises the same errors as real
 * `openspec validate` 1.14.3 ({@code fixtures/cli/1.14.3/validate-renamed/}), and is never stricter than the 1.3.0
 * floor ({@code fixtures/cli/1.3.0/validate-renamed/}) except for an unpaired rename beside other entries, which CLIs
 * refuse from 1.13.1 and the built-in validator (targeting 1.14.3) refuses too.
 */
public class RenamedVerdictContractTest extends OpenSpecIntegrationTestBase {

    private static final Path FIXTURES = Paths.get("src/test/resources/fixtures/cli");
    private static final List<String> CASES = List.of(
            "bare-only", "bare-beside-added", "unpaired-only", "unpaired-beside-added", "bare-file-beside-valid-file");
    /** Upstream message → the built-in rule that reports it. */
    private static final Map<String, String> RULE_FOR = Map.of(
            "no requirement entries parsed", "delta-no-entries",
            "has no matching", "delta-renamed-fields",
            "at least one delta", "delta-none-found");

    private static JsonObject item(String version, String c) throws Exception {
        return JsonParser.parseString(Files.readString(FIXTURES.resolve(version + "/validate-renamed/" + c + "/validate.json")))
                .getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject();
    }

    private static List<String> cliErrorRules(String version, String c) throws Exception {
        List<String> rules = new ArrayList<>();
        for (JsonElement e : item(version, c).getAsJsonArray("issues")) {
            JsonObject issue = e.getAsJsonObject();
            if (!"ERROR".equals(issue.get("level").getAsString())) continue;
            String message = issue.get("message").getAsString();
            rules.add(RULE_FOR.entrySet().stream().filter(r -> message.contains(r.getKey())).map(Map.Entry::getValue)
                    .findFirst().orElse("unmapped: " + message));
        }
        rules.sort(null);
        return rules;
    }

    private ValidationResult builtIn(String c) throws Exception {
        Path dir = FIXTURES.resolve("1.14.3/validate-renamed/" + c);
        if (myFixture.getTempDirFixture().getFile("openspec/specs/demo/spec.md") == null) {
            myFixture.addFileToProject("openspec/specs/demo/spec.md", Files.readString(dir.resolve("main-spec.md")));
        }
        String name = "ren-" + c;
        myFixture.addFileToProject("openspec/changes/" + name + "/proposal.md",
                "## Why\n\nA probe change used to capture how validate treats renames.\n\n## What Changes\n\n- Probe.\n");
        myFixture.addFileToProject("openspec/changes/" + name + "/specs/demo/spec.md", Files.readString(dir.resolve("delta.md")));
        if (Files.exists(dir.resolve("other-delta.md"))) {
            myFixture.addFileToProject("openspec/changes/" + name + "/specs/other/spec.md", Files.readString(dir.resolve("other-delta.md")));
        }
        return getProject().getService(BuiltInValidator.class).validateChange(name);
    }

    private static List<String> errorRules(ValidationResult result) {
        List<String> rules = new ArrayList<>(result.issues().stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR).map(ValidationIssue::rule).toList());
        rules.sort(null);
        return rules;
    }

    public void testBothVersionsHoldEveryCase() {
        for (String v : List.of("1.14.3", "1.3.0")) {
            for (String c : CASES) assertTrue(v + "/" + c, Files.exists(FIXTURES.resolve(v + "/validate-renamed/" + c + "/validate.json")));
        }
    }

    public void testBuiltInErrorsMatchCli1143() throws Exception {
        for (String c : CASES) {
            ValidationResult result = builtIn(c);
            assertEquals(c + ": " + result.issues(), cliErrorRules("1.14.3", c), errorRules(result));
            assertEquals(c, item("1.14.3", c).get("valid").getAsBoolean(), result.passed());
        }
    }

    public void testNeverStricterThanTheFloorExceptTheDocumentedUnpairedCase() throws Exception {
        for (String c : CASES) {
            boolean floorValid = item("1.3.0", c).get("valid").getAsBoolean();
            boolean builtInValid = builtIn(c).passed();
            if (c.equals("unpaired-beside-added")) {
                assertTrue("capture: 1.3.0 accepts it", floorValid);
                assertFalse("refused, as 1.13.1+ refuses it", builtInValid);
            } else if (floorValid) {
                assertTrue(c + " is valid on 1.3.0, so the built-in validator must accept it", builtInValid);
            }
        }
    }

    public void testIssuesCarryOpenSpecsWording() throws Exception {
        String noEntries = "Delta sections ## RENAMED Requirements were found, but no requirement entries parsed. "
                + "Ensure each section includes at least one \"### Requirement:\" block (REMOVED may use bullet list syntax).";
        ValidationResult alone = builtIn("bare-file-beside-valid-file");
        assertTrue(alone.issues().toString(), alone.issues().stream().anyMatch(i -> "delta-no-entries".equals(i.rule()) && i.message().equals(noEntries)));
        ValidationResult unpaired = builtIn("unpaired-beside-added");
        assertTrue(unpaired.issues().toString(), unpaired.issues().stream().anyMatch(i -> "delta-renamed-fields".equals(i.rule())
                && i.message().equals("RENAMED FROM: \"Works\" has no matching TO: line. Write each rename as a FROM: line followed immediately by its TO: line.")
                && i.line() == 12));
    }
}

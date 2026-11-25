package com.johnnyblabs.openspec.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.actions.OpenSpecValidateAction;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Release parity: the built-in validator, following each captured CLI release, gives that release's verdict on every
 * item of the 2.x parity corpus ({@code fixtures/cli/2.1.1/parity-corpus-2x/}), in default and in strict mode. The
 * captures are {@code <release>/validate-parity-corpus-2x{,-strict}.json} from {@code capture-validator-parity-2x.py}.
 * <p>
 * The one allowed difference is the warning family the plugin leaves to the CLI, text that archive would not carry
 * into the main spec ("Text above the first "## " section …", "Section "## Notes" …", 1.14.2 and later): an item the
 * release fails under {@code --strict} only on those warnings must be strict-valid in the plugin (laxer, never
 * stricter). Such items are listed exactly, so the list can't
 * grow or shrink silently.
 */
public class ValidatorVerdictReleaseParityTest extends OpenSpecIntegrationTestBase {

    static final List<String> RELEASES = List.of("1.14.0", "1.14.1", "1.14.3", "1.15.0", "1.16.0", "1.16.1", "1.17.0",
            "2.0.0", "2.1.0", "2.1.1");
    static final Path CORPUS = Path.of("src/test/resources/fixtures/cli/2.1.1/parity-corpus-2x");
    /** The CLI's "not carried into the main spec by archive" warnings (1.14.2+), which the plugin leaves to the CLI. */
    static final String NOT_CARRIED = "is not carried into the main spec by archive";

    /** Real front matter, accepted from 2.1.1; the lookalikes are never front matter. */
    static final Set<String> FRONT_MATTER = Set.of("fm-delta", "fm-bom-crlf", "fm-comment-title", "fm-quoted-key",
            "fm-empty");
    static final Set<String> LOOKALIKES = Set.of("fm-indented", "fm-thematic", "fm-heading-before-close",
            "fm-unclosed", "fm-comment-only");

    @Override
    protected void tearDown() throws Exception {
        try {
            getProject().getService(BuiltInValidator.class).clearReleaseForTest();
        } finally {
            super.tearDown();
        }
    }

    private static JsonObject capture(String release, String name) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of("src/test/resources/fixtures/cli", release, name)))
                .getAsJsonObject();
    }

    /** id → the capture's item. */
    private static Map<String, JsonObject> items(JsonObject capture) {
        Map<String, JsonObject> items = new LinkedHashMap<>();
        for (JsonElement e : capture.getAsJsonArray("items")) {
            items.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject());
        }
        return items;
    }

    private void materialize() throws IOException {
        try (Stream<Path> files = Files.walk(CORPUS)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                String rel = CORPUS.relativize(f).toString().replace('\\', '/');
                myFixture.addFileToProject(rel, new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
            }
        }
        VirtualFile root = myFixture.findFileInTempDir("openspec");
        assertNotNull(root);
        VfsUtil.markDirtyAndRefresh(false, true, true, root);
    }

    /** The issues on item {@code id}: a main spec's file, or anything under a change's folder. */
    private static List<ValidationIssue> issuesOf(List<ValidationIssue> all, String id, String type) {
        String marker = "spec".equals(type) ? "/openspec/specs/" + id + "/" : "/openspec/changes/" + id;
        return all.stream().filter(i -> i.filePath().contains(marker + ("spec".equals(type) ? "" : "/"))
                || ("change".equals(type) && i.filePath().endsWith(marker))).toList();
    }

    /** Whether the release's issues on an item are only not-carried warnings (what the plugin leaves out). */
    private static boolean onlyTextAbove(JsonObject item) {
        boolean any = false;
        for (JsonElement e : item.getAsJsonArray("issues")) {
            JsonObject issue = e.getAsJsonObject();
            if (!"WARNING".equals(issue.get("level").getAsString())) return false;
            if (!issue.get("message").getAsString().contains(NOT_CARRIED)) return false;
            any = true;
        }
        return any;
    }

    public void testEveryItemGetsEachReleasesVerdict() throws IOException {
        materialize();
        BuiltInValidator validator = getProject().getService(BuiltInValidator.class);
        List<String> wrong = new ArrayList<>();
        Set<String> laxer = new TreeSet<>();
        int checked = 0;
        for (String release : RELEASES) {
            validator.followReleaseForTest(release);
            List<ValidationIssue> all = new ArrayList<>(validator.validateSpecs().issues());
            all.addAll(validator.validateChanges().issues());
            Map<String, JsonObject> plain = items(capture(release, "validate-parity-corpus-2x.json"));
            Map<String, JsonObject> strict = items(capture(release, "validate-parity-corpus-2x-strict.json"));
            assertEquals(release + " sanity: both captures hold the same items", plain.keySet(), strict.keySet());
            for (String id : plain.keySet()) {
                String type = plain.get(id).get("type").getAsString();
                List<ValidationIssue> mine = issuesOf(all, id, type);
                boolean pluginValid = mine.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
                boolean pluginStrict = OpenSpecValidateAction.applyStrictFallbackVerdict(
                        new ValidationResult(pluginValid, mine, "built-in"), true, release).passed();
                boolean cliValid = plain.get(id).get("valid").getAsBoolean();
                boolean cliStrict = strict.get(id).get("valid").getAsBoolean();
                if (pluginValid != cliValid) wrong.add(release + " " + id + " default: CLI " + cliValid + ", plugin "
                        + pluginValid + " " + rules(mine));
                if (pluginStrict != cliStrict) {
                    if (!cliStrict && pluginStrict && onlyTextAbove(strict.get(id))) {
                        laxer.add(release + " " + id);
                    } else {
                        wrong.add(release + " " + id + " strict: CLI " + cliStrict + ", plugin " + pluginStrict + " "
                                + rules(mine));
                    }
                }
                checked++;
            }
        }
        assertEquals("verdicts that differ from the release's:\n" + String.join("\n", wrong), List.of(), wrong);
        assertEquals("the items the plugin is laxer on, only through the not-carried warnings it leaves to the CLI",
                expectedLaxer(), laxer);
        assertTrue("sanity: items checked " + checked, checked >= 40 * RELEASES.size());
    }

    /** Front matter fails strict through "Text above" from 1.14.3 until 2.1.0; the lookalikes on every release since. */
    private static Set<String> expectedLaxer() {
        Set<String> expected = new TreeSet<>();
        for (String release : RELEASES) {
            if (com.johnnyblabs.openspec.util.CliVersion.atLeast(release, "1.14.2")) {
                for (String id : LOOKALIKES) expected.add(release + " " + id);
                if (!com.johnnyblabs.openspec.util.CliVersion.atLeast(release, "2.1.1")) {
                    for (String id : FRONT_MATTER) expected.add(release + " " + id);
                }
            }
        }
        return expected;
    }

    private static String rules(List<ValidationIssue> issues) {
        return issues.stream().map(i -> i.severity().name().charAt(0) + ":" + i.rule()).toList().toString();
    }
}

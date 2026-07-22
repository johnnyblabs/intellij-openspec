package com.johnnyblabs.openspec.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationIssue;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verdict-parity contract: the plugin's built-in validator must agree with the real
 * OpenSpec CLI 1.6.0 on valid/invalid for a corpus exercising the rule classes both
 * implement (keyword family, fence masking, header-only keyword, scenario counting,
 * skipped-header INFO). The CLI side is the captured fixture
 * {@code fixtures/cli/1.6.0/validate-parity-corpus.json}; the corpus itself is committed
 * under {@code fixtures/cli/1.6.0/parity-corpus/} and materialized into the test project,
 * so both validators judge byte-identical content. Parity is semantic (same verdict),
 * not textual (messages/paths deliberately differ). Re-capture recipe: see the fixtures
 * README manifest.
 */
public class ValidatorVerdictParityTest extends OpenSpecIntegrationTestBase {

    private static final String CORPUS_PREFIX = "/fixtures/cli/1.6.0/parity-corpus/";

    private static final List<String> CORPUS_FILES = List.of(
            "openspec/specs/clean-spec/spec.md",
            "openspec/specs/should-only/spec.md",
            "openspec/specs/fenced-keyword/spec.md",
            "openspec/specs/second-line-keyword/spec.md",
            "openspec/specs/header-only-keyword/spec.md",
            "openspec/specs/fenced-scenario/spec.md",
            // align-spec-parser-with-cli: adversarial specs added to the parity corpus.
            "openspec/specs/indented-code/spec.md",
            "openspec/specs/setext-header/spec.md",
            "openspec/specs/table-keyword/spec.md",
            "openspec/specs/html-comment-req/spec.md",
            "openspec/specs/nested-list-scenario/spec.md",
            "openspec/changes/info-change/proposal.md",
            "openspec/changes/info-change/specs/demo/spec.md",
            "openspec/changes/nameless-change/proposal.md",
            "openspec/changes/nameless-change/specs/demo/spec.md");

    private static String resource(String path) {
        try (InputStream is = ValidatorVerdictParityTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Missing test resource: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void testPluginVerdictsMatchCapturedCliVerdicts() {
        for (String file : CORPUS_FILES) {
            myFixture.addFileToProject(file, resource(CORPUS_PREFIX + file));
        }
        VirtualFile root = myFixture.findFileInTempDir("openspec");
        assertNotNull(root);
        VfsUtil.markDirtyAndRefresh(false, true, true, root);

        BuiltInValidator validator = getProject().getService(BuiltInValidator.class);
        List<ValidationIssue> issues = new ArrayList<>();
        issues.addAll(validator.validateSpecs().issues());
        ValidationResult changes = validator.validateChanges();
        issues.addAll(changes.issues());

        Map<String, Boolean> cliVerdicts = new LinkedHashMap<>();
        JsonObject fixture = JsonParser.parseString(
                resource("/fixtures/cli/1.6.0/validate-parity-corpus.json")).getAsJsonObject();
        for (JsonElement el : fixture.getAsJsonArray("items")) {
            JsonObject item = el.getAsJsonObject();
            cliVerdicts.put(item.get("id").getAsString(), item.get("valid").getAsBoolean());
        }
        assertTrue("corpus drift: expected 13 fixture items, got " + cliVerdicts.size(),
                cliVerdicts.size() == 13);

        for (Map.Entry<String, Boolean> e : cliVerdicts.entrySet()) {
            String id = e.getKey();
            // Corpus-drift guard: every fixture item must have a materialized file.
            assertTrue("fixture item '" + id + "' has no corpus file — CORPUS_FILES is stale",
                    CORPUS_FILES.stream().anyMatch(f -> f.contains("/" + id + "/")));
            // Change-level issues carry the change DIRECTORY as filePath (no trailing
            // slash), so match both forms — a dir-level ERROR must break parity too.
            String marker = "/" + id + "/";
            boolean pluginValid = issues.stream().noneMatch(i ->
                    i.severity() == ValidationIssue.Severity.ERROR
                            && (i.filePath().contains(marker) || i.filePath().endsWith("/" + id)));
            assertEquals("verdict parity for '" + id + "' (CLI says " + e.getValue() + ")",
                    (boolean) e.getValue(), pluginValid);
        }

        // The INFO tier itself is part of parity: both valid changes carry the advisory.
        assertTrue("info-change must carry the skipped-header INFO",
                issues.stream().anyMatch(i -> "delta-skipped-header".equals(i.rule())
                        && i.filePath().contains("/info-change/")
                        && i.severity() == ValidationIssue.Severity.INFO));
        assertTrue("nameless-change must carry the add-a-name INFO",
                issues.stream().anyMatch(i -> "delta-skipped-header".equals(i.rule())
                        && i.filePath().contains("/nameless-change/")
                        && i.message().contains("missing a requirement name")));
    }
}

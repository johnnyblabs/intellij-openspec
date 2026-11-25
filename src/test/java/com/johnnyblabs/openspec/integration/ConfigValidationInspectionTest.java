package com.johnnyblabs.openspec.integration;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.johnnyblabs.openspec.validation.ConfigValidationInspection;

import java.nio.charset.StandardCharsets;

/**
 * Integration tests for ConfigValidationInspection.
 * Verifies that config.yaml validation issues are detected.
 */
public class ConfigValidationInspectionTest extends OpenSpecIntegrationTestBase {

    public void testValidConfigHasNoErrors() {
        // The inspection checks parent dir name is "openspec", so we use the fixture file
        PsiFile file = myFixture.configureByFile("openspec/config.yaml");

        ConfigValidationInspection inspection = new ConfigValidationInspection();
        ProblemDescriptor[] problems = inspection.checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        assertEquals("Valid config should have no problems", 0, problems.length);
    }

    public void testNonOpenspecConfigIsSkipped() {
        // A config.yaml not parented under openspec/ short-circuits the field-level checks.
        PsiFile file = myFixture.configureByText("config.yaml",
                "profile:\n  name: Test\n");

        ConfigValidationInspection inspection = new ConfigValidationInspection();
        ProblemDescriptor[] problems = inspection.checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        assertEquals("Non-openspec config.yaml should be skipped", 0, problems.length);
    }

    public void testNonConfigFileIsSkipped() {
        PsiFile file = myFixture.configureByText("other.yaml",
                "some: value\n");

        ConfigValidationInspection inspection = new ConfigValidationInspection();
        ProblemDescriptor[] problems = inspection.checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        assertEquals("Non-config files should be skipped", 0, problems.length);
    }

    public void testMissingProfileProducesNoProblem() throws Exception {
        // The profile nag is removed: an openspec/config.yaml with a schema but no `profile:` must
        // produce ZERO problems. `profile` is the global workflow profile, never a project config field.
        PsiFile file = writeOpenspecConfig("schema: spec-driven\n");

        ProblemDescriptor[] problems = new ConfigValidationInspection().checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        assertEquals("no profile nag; schema present -> zero problems", 0, problems.length);
    }

    public void testMissingSchemaIsInformationNudge() throws Exception {
        // The schema-absent nudge is demoted to INFORMATION (advisory), never a WARNING squiggle —
        // `openspec validate` is clean on a missing schema, so a warning would be stricter than the CLI.
        PsiFile file = writeOpenspecConfig("context: An IntelliJ plugin.\n");

        ProblemDescriptor[] problems = new ConfigValidationInspection().checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        assertEquals("exactly one problem: the schema nudge", 1, problems.length);
        assertEquals("schema nudge must be INFORMATION, not WARNING",
                ProblemHighlightType.INFORMATION, problems[0].getHighlightType());
    }

    public void testAPointerOnlyConfigIsNotNudgedForASchema() throws Exception {
        // The project's planning lives in the store it names; the store's own config sets the schema.
        PsiFile file = writeOpenspecConfig("store: demo\n");

        ProblemDescriptor[] problems = new ConfigValidationInspection().checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        assertEquals("no schema nudge on a pointer-only config", 0, problems.length);
    }

    public void testOperationsAndRulesConfigProducesNoProblems() throws Exception {
        // A real 1.7 config.yaml with `operations` (apply/archive guidance) and list-shaped `rules`
        // — optional keys the CLI accepts (validate clean) and the plugin's reader ignores. The
        // inspection must not false-flag them: the plugin is never stricter than the client.
        PsiFile file = writeOpenspecConfig(fixture("1.7.0/config-validation/operations-and-rules.config.yaml"));

        ProblemDescriptor[] problems = new ConfigValidationInspection().checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        assertEquals("1.7 operations/rules config must raise no inspection problems", 0, problems.length);
    }

    private static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (java.io.InputStream is = ConfigValidationInspectionTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Overwrite the fixture's openspec/config.yaml and return its PsiFile (parented under openspec/). */
    // ---- skillsScope (CLI 2.1+), against the captured configs and warning ----

    private ProblemDescriptor[] check(String content) throws Exception {
        return new ConfigValidationInspection().checkFile(writeOpenspecConfig(content),
                InspectionManager.getInstance(getProject()), false);
    }

    public void testAnInvalidSkillsScopeIsWarnedAboutWithTheCliText() throws Exception {
        for (String v : new String[] {"2.1.0", "2.1.1"}) {
            String warning = fixture(v + "/skills-scope/team-warning.stderr").strip();
            ProblemDescriptor[] problems = check(fixture(v + "/skills-scope/config-team.yaml"));
            assertEquals(v, 1, problems.length);
            assertEquals(v, warning, problems[0].getDescriptionTemplate());
            assertEquals(v, ProblemHighlightType.WARNING, problems[0].getHighlightType());
            assertEquals(v + ": highlighted on the skillsScope line (the third)", 2, problems[0].getLineNumber());
        }
    }

    public void testAnEmptySkillsScopeIsWarnedAboutAsUpstreamDoes() throws Exception {
        for (String v : new String[] {"2.1.0", "2.1.1"}) {
            ProblemDescriptor[] problems = check(fixture(v + "/skills-scope/config-empty.yaml"));
            assertEquals(v, 1, problems.length);
            assertEquals(v, fixture(v + "/skills-scope/empty-warning.stderr").strip(), problems[0].getDescriptionTemplate());
        }
    }

    public void testValidOrMissingSkillsScopeIsClean() throws Exception {
        for (String v : new String[] {"2.1.0", "2.1.1"}) {
            assertEquals(v, 0, check(fixture(v + "/skills-scope/remembered-config.yaml")).length);
        }
        assertEquals(0, check("schema: spec-driven\nskillsScope: global\n").length);
        assertEquals(0, check("schema: spec-driven\n").length);
    }

    private PsiFile writeOpenspecConfig(String content) throws Exception {
        VirtualFile vf = myFixture.findFileInTempDir("openspec/config.yaml");
        assertNotNull("openspec/config.yaml should exist in the test project", vf);
        WriteAction.run(() -> vf.setBinaryContent(content.getBytes(StandardCharsets.UTF_8)));
        PsiFile file = PsiManager.getInstance(getProject()).findFile(vf);
        assertNotNull(file);
        return file;
    }
}

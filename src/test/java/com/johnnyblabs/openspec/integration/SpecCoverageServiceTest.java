package com.johnnyblabs.openspec.integration;

import com.johnnyblabs.openspec.model.CoverageResult;
import com.johnnyblabs.openspec.model.CoverageResult.DomainCoverage;
import com.johnnyblabs.openspec.model.CoverageResult.RequirementCoverage;
import com.johnnyblabs.openspec.services.SpecCoverageService;

import java.io.IOException;

/**
 * Full-coverage tests for {@link SpecCoverageService}.
 *
 * <p>The {@code @spec domain:requirement} scan must be language-agnostic (Java, Kotlin, Go,
 * Python, …), skip recognized binary files, and account for covered/uncovered requirements
 * correctly. The fixture spec {@code openspec/specs/actions/spec.md} defines two requirements:
 * "Init Action" and "Propose Action".
 */
public class SpecCoverageServiceTest extends OpenSpecIntegrationTestBase {

    private static final String DOMAIN = "actions";
    private static final String INIT = "Init Action";
    private static final String PROPOSE = "Propose Action";

    private CoverageResult compute() {
        return getProject().getService(SpecCoverageService.class).computeCoverage();
    }

    private RequirementCoverage requirement(CoverageResult result, String name) {
        DomainCoverage domain = result.domains().get(DOMAIN);
        assertNotNull("actions domain should be present in coverage result", domain);
        return domain.requirements().stream()
                .filter(r -> r.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("requirement not found: " + name));
    }

    public void testFixtureExposesTwoRequirements() {
        // Baseline: the spec parses regardless of source files, so total is stable.
        assertEquals(2, compute().totalRequirements());
    }

    public void testJavaReferenceIsCovered() {
        // Regression: the previously-only-supported language must keep working.
        myFixture.addFileToProject("Init.java", "class Init {\n    // @spec actions:Init Action\n}\n");
        assertTrue(requirement(compute(), INIT).covered());
    }

    public void testKotlinReferenceIsCovered() {
        myFixture.addFileToProject("Init.kt", "// @spec actions:Init Action\nfun init() {}\n");
        CoverageResult result = compute();
        assertTrue("Kotlin @spec ref should be covered", requirement(result, INIT).covered());
        assertTrue("overall coverage should be greater than 0", result.coveredRequirements() > 0);
    }

    public void testGoReferenceIsCovered() {
        myFixture.addFileToProject("init.go", "package main\n// @spec actions:Init Action\n");
        assertTrue(requirement(compute(), INIT).covered());
    }

    public void testPythonHashCommentReferenceIsCovered() {
        // Different comment syntax ('#') must still match — the scan reads raw text.
        myFixture.addFileToProject("init.py", "# @spec actions:Init Action\n");
        assertTrue(requirement(compute(), INIT).covered());
    }

    public void testMixedLanguageProjectCoversAllRequirements() {
        myFixture.addFileToProject("a.kt", "// @spec actions:Init Action");
        myFixture.addFileToProject("b.go", "// @spec actions:Propose Action");
        CoverageResult result = compute();
        assertTrue(requirement(result, INIT).covered());
        assertTrue(requirement(result, PROPOSE).covered());
        assertEquals("every requirement covered",
                result.totalRequirements(), result.coveredRequirements());
    }

    public void testUnreferencedRequirementIsUncovered() {
        myFixture.addFileToProject("a.kt", "// @spec actions:Init Action");
        CoverageResult result = compute();
        assertTrue(requirement(result, INIT).covered());
        assertFalse("Propose Action has no reference", requirement(result, PROPOSE).covered());
        assertEquals(1, result.coveredRequirements());
    }

    public void testReferenceOnlyInBinaryFileIsIgnored() throws IOException {
        // A '.zip' resolves to a recognized binary FileType and must be skipped, even though its
        // bytes contain a valid-looking @spec reference. (Control: .kt/.go refs ARE counted above.)
        myFixture.getTempDirFixture().createFile("blob.zip", "// @spec actions:Init Action");
        CoverageResult result = compute();
        assertFalse("ref inside a recognized binary must not count",
                requirement(result, INIT).covered());
        assertEquals(0, result.coveredRequirements());
    }

    public void testMultipleFilesReferencingSameRequirement() {
        myFixture.addFileToProject("a.kt", "// @spec actions:Init Action");
        myFixture.addFileToProject("b.go", "// @spec actions:Init Action");
        assertEquals("both referencing files recorded",
                2, requirement(compute(), INIT).referencingFiles().size());
    }

    public void testReferenceToUnknownRequirementIsHarmless() {
        myFixture.addFileToProject("a.kt", "// @spec actions:Does Not Exist");
        CoverageResult result = compute();
        assertEquals("no real requirement is covered", 0, result.coveredRequirements());
        assertEquals("requirement total is unchanged", 2, result.totalRequirements());
    }

    public void testNoReferencesAnywhereYieldsZeroCovered() {
        CoverageResult result = compute();
        assertEquals(0, result.coveredRequirements());
        assertEquals(2, result.totalRequirements());
    }

    public void testExtraWhitespaceAroundReferenceStillMatches() {
        myFixture.addFileToProject("a.kt", "//    @spec    actions:Init Action");
        assertTrue(requirement(compute(), INIT).covered());
    }

    public void testCachedResultMatchesLastCompute() {
        // Note: BasePlatformTestCase reuses one light project across methods, so we don't assert
        // the pre-compute state; we assert getCachedResult() reflects the most recent compute.
        SpecCoverageService service = getProject().getService(SpecCoverageService.class);
        CoverageResult result = service.computeCoverage();
        assertSame("getCachedResult returns the last computed result",
                result, service.getCachedResult());
    }
}
package com.johnnyblabs.openspec.toolwindow;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.validation.ValidationIssue.Severity;

import java.io.File;

/**
 * Platform tests for {@link OpenSpecConsolePanel}'s severity→content-type mapping and the pure
 * file-resolution/line-mapping wiring behind {@code printFileHyperlink}. The resolution branch
 * is exercised against a REAL temp file (so {@link LocalFileSystem} actually resolves it) and an
 * unresolvable CLI {@code type/id} pseudo-path, without ever driving a live editor — the design's
 * testability split (assert the wiring, leave click-navigation to the uiSmoke journey).
 */
public class OpenSpecConsolePanelTest extends BasePlatformTestCase {

    public void testSeverityContentTypeMappingCoversExactlyThreeSeverities() {
        assertSame(ConsoleViewContentType.ERROR_OUTPUT,
                OpenSpecConsolePanel.contentTypeForSeverity(Severity.ERROR));
        assertSame(ConsoleViewContentType.LOG_WARNING_OUTPUT,
                OpenSpecConsolePanel.contentTypeForSeverity(Severity.WARNING));
        assertSame(ConsoleViewContentType.LOG_INFO_OUTPUT,
                OpenSpecConsolePanel.contentTypeForSeverity(Severity.INFO));
        // The mapping's switch is exhaustive over the enum; there is no fourth severity to map.
        assertEquals("severity vocabulary is exactly ERROR/WARNING/INFO", 3, Severity.values().length);
    }

    public void testLineMappingIsOneBasedToZeroBasedGuardingNonPositive() {
        assertEquals(11, OpenSpecConsolePanel.toZeroBasedLine(12));
        assertEquals(0, OpenSpecConsolePanel.toZeroBasedLine(1));
        assertEquals(0, OpenSpecConsolePanel.toZeroBasedLine(0));
        assertEquals(0, OpenSpecConsolePanel.toZeroBasedLine(-5));
    }

    public void testResolvableAbsolutePathResolvesToFileAtLineMinusOne() throws Exception {
        File tmp = FileUtil.createTempFile("openspec-console-", ".md", true);
        FileUtil.writeToFile(tmp, "# Spec\nbody\n");
        String path = tmp.getCanonicalPath();
        // Prime the VFS cache the way production expects it (the validated file was just read);
        // production resolution itself uses the non-blocking findFileByPath.
        assertNotNull("temp file must enter VFS", LocalFileSystem.getInstance().refreshAndFindFileByPath(path));

        OpenSpecConsolePanel.ResolvedLocation loc =
                OpenSpecConsolePanel.resolveLocation(getProject(), path, 12);

        assertTrue("an absolute path to a real file must resolve", loc.isResolved());
        assertEquals("1-based line 12 maps to 0-based 11", 11, loc.zeroBasedLine());
    }

    public void testResolvableAbsolutePathGuardsLineZero() throws Exception {
        File tmp = FileUtil.createTempFile("openspec-console-zero-", ".md", true);
        FileUtil.writeToFile(tmp, "x\n");
        String path = tmp.getCanonicalPath();
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path);

        OpenSpecConsolePanel.ResolvedLocation loc =
                OpenSpecConsolePanel.resolveLocation(getProject(), path, 0);

        assertTrue(loc.isResolved());
        assertEquals("non-positive line clamps to 0", 0, loc.zeroBasedLine());
    }

    public void testOpenspecRelativePathResolvesAgainstBase() throws Exception {
        // An openspec/-relative path (the built-in validator's config-file fallback shape)
        // must resolve against the project base — the positive relative branch.
        File base = FileUtil.createTempDirectory("openspec-base-", null, true);
        File nested = new File(base, "openspec");
        assertTrue(nested.mkdirs());
        File config = new File(nested, "config.yaml");
        FileUtil.writeToFile(config, "schema: spec-driven\n");
        // Whitelist the temp root and prime the EXACT nested file into VFS (like the sibling
        // temp-file tests) so the non-refreshing findFileByPath hits the cache instead of walking
        // persistence and tripping the test VfsRootAccess guard (on CI the temp dir lands outside
        // the default allowed roots).
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), base.getCanonicalPath());
        LocalFileSystem.getInstance().refreshAndFindFileByPath(config.getCanonicalPath());

        VirtualFile resolved = OpenSpecConsolePanel.resolveFile("openspec/config.yaml", base.getCanonicalPath());
        // Non-vacuous: it must resolve to the base-relative file specifically — not a same-named
        // file under the JVM working directory (the earlier bare-relative resolution bug).
        assertNotNull("openspec/config.yaml must resolve against the project base", resolved);
        assertEquals("must resolve to the base-relative file, not a working-dir collision",
                FileUtil.toSystemIndependentName(config.getCanonicalPath()), resolved.getPath());
    }

    public void testCliPseudoPathDoesNotResolve() {
        OpenSpecConsolePanel.ResolvedLocation loc =
                OpenSpecConsolePanel.resolveLocation(getProject(), "spec/actions", 0);

        assertFalse("a CLI type/id pseudo-path must not resolve to a file", loc.isResolved());
        assertNull(loc.file());
    }

    public void testEmptyPathDoesNotResolve() {
        assertFalse(OpenSpecConsolePanel.resolveLocation(getProject(), "", 1).isResolved());
        assertFalse(OpenSpecConsolePanel.resolveLocation(getProject(), null, 1).isResolved());
    }
}

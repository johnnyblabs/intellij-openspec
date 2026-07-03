package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cross-platform root canonicalization parity: the plugin must match a store root reached by a
 * <b>non-canonical</b> path to its canonical registered root. Canonicalization is owned by
 * {@code add-store-workset-read-surface} (via {@link CoordinationService#canonicalize} +
 * {@link CoordinationService#storeMatchingRoot}); this change only verifies that surface behaves the
 * same across platforms — it adds no matching logic here (task 5.3).
 *
 * <ul>
 *   <li>Symlink parity runs on macOS/Linux CI ({@link Files#createSymbolicLink} needs no privilege
 *       there; on Windows it requires Developer Mode / SeCreateSymbolicLink, so it is gated off).</li>
 *   <li>Windows 8.3 short-path parity is {@code @EnabledOnOs(WINDOWS)} and runs on the GitHub Windows
 *       matrix leg. It self-skips when 8.3 short-name creation is disabled on the volume.</li>
 * </ul>
 */
class RootCanonicalizationParityTest {

    // ---- 5.1: symlinked root matches the canonical registered root ----------

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void symlinkedRootMatchesCanonicalRegisteredRoot(@TempDir Path tmp) throws Exception {
        // The canonical (registered) store root.
        Path canonical = Files.createDirectories(tmp.resolve("stores").resolve("gitstore"));
        // A symlink that reaches the same directory by a different path.
        Path link = tmp.resolve("gitstore-link");
        Files.createSymbolicLink(link, canonical);

        // Register the store under its symlinked path; the "project root" the plugin sees is the
        // canonical path. Canonicalization (toRealPath resolves the symlink) must make them match.
        StoreEntry match = CoordinationService.storeMatchingRoot(
                List.of(StoreEntry.basic("gitstore", link.toString())), canonical);
        assertNotNull(match, "a symlinked store root must match the canonical registered root");
        assertEquals("gitstore", match.id());

        // And the reverse: registering the canonical path matches a project root reached via the link.
        StoreEntry reverse = CoordinationService.storeMatchingRoot(
                List.of(StoreEntry.basic("gitstore", canonical.toString())), link);
        assertNotNull(reverse, "canonicalization must be symmetric across the symlink");
    }

    // ---- 5.2: Windows 8.3 short-path matches the canonical long-path root ----

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void shortPathMatchesCanonicalLongPathRootOnWindows(@TempDir Path tmp) throws Exception {
        // A store root whose directory name is longer than 8.3 so it has a distinct short form.
        Path longRoot = Files.createDirectories(tmp.resolve("LongStoreDirectoryName"));
        String shortForm = eightDotThree(longRoot);
        // Skip cleanly when the volume has 8.3 name creation disabled (common on modern Windows) —
        // there is then no distinct short path to exercise, so this is a skip, not a failure.
        assumeTrue(shortForm != null && !shortForm.equalsIgnoreCase(longRoot.toString()),
                "8.3 short-name creation appears disabled on this volume; skipping short-path parity");

        // Register under the canonical long path; the plugin sees the 8.3 short form as the incoming
        // root. toRealPath resolves the short form to its long form, so they must match.
        StoreEntry match = CoordinationService.storeMatchingRoot(
                List.of(StoreEntry.basic("longstore", longRoot.toString())), Path.of(shortForm));
        assertNotNull(match, "an 8.3 short-path form must match the canonical long-path registered root");
        assertEquals("longstore", match.id());
    }

    /** Resolves a path's 8.3 short-name form via {@code cmd.exe}, or null when unavailable. */
    private static String eightDotThree(Path longPath) {
        try {
            Process p = new ProcessBuilder("cmd.exe", "/c",
                    "for %I in (\"" + longPath.toAbsolutePath() + "\") do @echo %~sfI")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }
}

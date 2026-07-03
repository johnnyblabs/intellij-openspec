package com.johnnyblabs.openspec.coordination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Native path round-tripping for the store parser. Store roots that contain spaces, Windows drive
 * backslash paths, and UNC paths MUST be preserved <b>byte-for-byte</b> in the parsed model — the
 * parser must never normalize, split, or otherwise mangle a native path string.
 *
 * <p>The fixture reuses the real captured {@code store list --json} shape ({@code stores:[{id,root}]});
 * only the leaf {@code root} strings vary, which is exactly the payload under test. The raw
 * round-trip assertion is host-independent. Any assertion that resolves such a string through the
 * platform {@link Path} type is OS-gated to the OS where that path form is valid, because
 * {@code Path.of("C:\\...")} is meaningless on a non-Windows JVM (and vice-versa).
 */
class NativePathRoundTripTest {

    private static String fixture(String name) {
        String path = "/fixtures/cli/1.5.0/" + name;
        try (InputStream is = NativePathRoundTripTest.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, String> rootsById() {
        return CoordinationService.parseStores(fixture("store-list-native-paths.json")).stream()
                .collect(Collectors.toMap(StoreEntry::id, StoreEntry::root));
    }

    // ---- 4.1: raw round-trip (host-independent) -----------------------------

    @Test
    void spacedBackslashAndUncPathsSurviveParsingUnchanged() {
        Map<String, String> roots = rootsById();
        // Each raw native path string is preserved verbatim — no normalization, no separator swaps.
        assertEquals("C:\\Program Files\\my store", roots.get("spaced"),
                "a Windows path with spaces must round-trip unchanged");
        assertEquals("D:\\stores\\team-store", roots.get("drive"),
                "a Windows drive backslash path must round-trip unchanged");
        assertEquals("\\\\fileserver\\share\\team store", roots.get("unc"),
                "a UNC path must round-trip unchanged (leading double backslash intact)");
        assertEquals("/home/user/my store", roots.get("posix-spaced"),
                "a POSIX path with spaces must round-trip unchanged");
    }

    // ---- 4.2: Path resolution is OS-gated -----------------------------------

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsPathsResolveThroughPathOnWindows() {
        Map<String, String> roots = rootsById();
        // On a Windows JVM these strings are valid Path values: resolution keeps the final segment
        // (with its spaces) intact and treats the UNC form as an absolute path.
        Path spaced = Path.of(roots.get("spaced"));
        assertEquals("my store", spaced.getFileName().toString());
        Path unc = Path.of(roots.get("unc"));
        assertEquals("team store", unc.getFileName().toString());
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void posixPathResolvesThroughPathOnUnix() {
        Map<String, String> roots = rootsById();
        // On a POSIX JVM the spaced POSIX path is a valid Path; the Windows forms are intentionally
        // NOT resolved here (Path.of would treat the backslashes as literal filename characters).
        Path posix = Path.of(roots.get("posix-spaced"));
        assertEquals("my store", posix.getFileName().toString());
        assertEquals(List.of("home", "user", "my store"),
                java.util.stream.StreamSupport.stream(posix.spliterator(), false)
                        .map(Path::toString).collect(Collectors.toList()));
    }
}

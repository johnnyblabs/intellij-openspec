package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The fixtures tree holds no real links. CLI 2.x layouts contain directory links, and the capture records them as
 * data in {@code layout.json} ({@link FixtureLayout}), because a committed link checks out as a link on one platform
 * and as a text file on another, so the same test would see a different tree per platform.
 * <p>
 * Two checks, because each misses a case the other catches: the disk walk catches a link that exists in this checkout
 * but isn't committed yet, and the git index catches a committed link that a checkout with {@code core.symlinks} off
 * turned into a plain file.
 */
class FixtureNoSymlinkHygieneTest {

    static final Path FIXTURES = Paths.get("src/test/resources/fixtures");

    @Test
    void noFixtureOnDiskIsALink() throws IOException {
        List<String> links = new ArrayList<>();
        // Files.walk doesn't follow links unless asked to, so a linked folder is listed but never entered.
        try (Stream<Path> walk = Files.walk(FIXTURES)) {
            for (Path p : walk.toList()) {
                BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                // isOther: a Windows junction or another non-file, non-folder entry.
                if (a.isSymbolicLink() || a.isOther()) links.add(p.toString().replace('\\', '/'));
            }
        }
        assertEquals(List.of(), links, "fixtures must record links in layout.json, not contain them");
    }

    @Test
    void noFixtureIsCommittedAsALink() throws IOException, InterruptedException {
        Process git;
        try {
            git = new ProcessBuilder("git", "ls-files", "-s", "--", FIXTURES.toString())
                    .redirectErrorStream(true).start();
        } catch (IOException e) {
            Assumptions.abort("git is not available: " + e.getMessage());
            return;
        }
        String out = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Assumptions.assumeTrue(git.waitFor(60, TimeUnit.SECONDS) && git.exitValue() == 0,
                "not a git checkout: " + out);
        List<String> links = out.lines().filter(l -> l.startsWith("120000 ")).map(l -> l.substring(l.indexOf('\t') + 1))
                .toList();
        assertEquals(List.of(), links, "fixtures must record links in layout.json, not commit them");
    }
}

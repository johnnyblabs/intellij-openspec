package com.johnnyblabs.openspec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Install wording lives in {@code CliInstallHints} alone. Any other npm install command in {@code src/main/java} fails
 * this test, except the below-1.3.0 upgrade text, which ends in {@code @latest}. Each hit is checked on its own, so a
 * stray hint can't hide in a file that also holds an allowed one.
 */
class InstallHintDriftTest {

    private static final Pattern NPM_INSTALL = Pattern.compile("npm (?:i|install) -g @fission-ai/openspec(@latest)?");
    private static final String HINTS = "CliInstallHints.java";
    private static final List<String> BELOW_FLOOR_FILES = List.of("OpenSpecNotifier.java", "BuiltInValidator.java");

    private static Path mainJava() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/java/com/johnnyblabs/openspec"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "could not locate the repository root from " + Paths.get("").toAbsolutePath());
        return dir.resolve("src/main/java");
    }

    @Test
    void installCommandsAppearOnlyInTheSharedHint() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(mainJava())) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertTrue(files.size() > 100, "the scan must see the whole source tree, saw " + files.size());

        List<String> offenders = new ArrayList<>();
        int inHints = 0;
        int belowFloor = 0;
        for (Path file : files) {
            String name = file.getFileName().toString();
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = NPM_INSTALL.matcher(lines.get(i));
                while (m.find()) {
                    if (name.equals(HINTS)) {
                        inHints++;
                    } else if (BELOW_FLOOR_FILES.contains(name) && m.group().equals("npm i -g @fission-ai/openspec@latest")) {
                        belowFloor++;
                    } else {
                        offenders.add(name + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                }
            }
        }

        assertEquals(List.of(), offenders, "write install hints through CliInstallHints");
        assertTrue(inHints > 0, "the shared hint itself must carry the npm command");
        assertEquals(2, belowFloor, "exactly the two below-1.3.0 upgrade lines may name npm directly");
    }
}

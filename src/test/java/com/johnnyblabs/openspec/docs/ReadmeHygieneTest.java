package com.johnnyblabs.openspec.docs;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks on the README, the repository's public front page: the Node.js version it and the other install docs state
 * is the target CLI release's, the CLI range it states follows the target, and its relative links and anchors resolve.
 */
class ReadmeHygieneTest {

    /** The docs that tell a user which Node.js the npm route needs. */
    private static final List<String> INSTALL_DOCS =
            List.of("README.md", "CONTRIBUTING.md", "docs/marketplace/getting-started.html");

    /** A stated Node.js version, e.g. "Node.js 22.20+" or "Node 22.20 or newer". */
    private static final Pattern NODE_VERSION = Pattern.compile("\\bNode(?:\\.js)? (\\d+\\.\\d+)");

    /** A Markdown link target. */
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");

    @Test
    void theInstallDocsStateTheTargetReleasesNodeFloor() {
        String floor = nodeFloor();
        for (String doc : INSTALL_DOCS) {
            List<String> stated = statedNodeVersions(read(root().resolve(doc)));
            assertTrue(!stated.isEmpty() || !doc.equals("README.md"), "README.md no longer states the npm route's Node.js");
            for (String version : stated) {
                assertEquals(floor, version, doc + " states Node.js " + version + ", but CLI " + target()
                        + " needs " + floor + " (its engines.node)");
            }
        }
    }

    @Test
    void aStaleNodeVersionIsDetectable() {
        assertEquals(List.of("22.12"), statedNodeVersions("npm install -g x   # needs Node.js 22.12+"));
    }

    @Test
    void theReadmeStatesTheTargetCliLine() {
        String readme = read(root().resolve("README.md"));
        String target = target();
        String line = target.replaceFirst("^(\\d+\\.\\d+)\\..*$", "$1") + ".x";
        assertTrue(readme.contains("1.3.0 through " + line), "README should say the plugin supports 1.3.0 through " + line);
        assertTrue(readme.contains("targeting " + target), "README should name the target release " + target);
    }

    @Test
    void theReadmesRelativeLinksAndAnchorsResolve() {
        Path root = root();
        String readme = read(root.resolve("README.md"));
        List<String> broken = new ArrayList<>();
        Matcher m = LINK.matcher(readme);
        while (m.find()) {
            String target = m.group(1);
            if (target.matches("^[a-z]+:.*")) continue;
            String path = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
            String anchor = target.contains("#") ? target.substring(target.indexOf('#') + 1) : null;
            Path file = path.isEmpty() ? root.resolve("README.md") : root.resolve(path);
            if (!Files.exists(file)) {
                broken.add(target + " (no such file)");
            } else if (anchor != null && !Files.isDirectory(file) && !anchors(read(file)).contains(anchor)) {
                broken.add(target + " (no such heading)");
            }
        }
        assertTrue(broken.isEmpty(), "README links that don't resolve: " + broken);
    }

    static List<String> statedNodeVersions(String text) {
        List<String> versions = new ArrayList<>();
        Matcher m = NODE_VERSION.matcher(text);
        while (m.find()) versions.add(m.group(1));
        return versions;
    }

    /** GitHub's heading anchors: lower case, punctuation other than hyphens dropped, spaces as hyphens. */
    private static List<String> anchors(String markdown) {
        List<String> anchors = new ArrayList<>();
        for (String line : markdown.split("\n")) {
            if (!line.startsWith("#")) continue;
            String heading = line.replaceFirst("^#+\\s*", "").trim().toLowerCase(Locale.ROOT);
            anchors.add(heading.replaceAll("[^\\p{L}\\p{N} _-]", "").replace(' ', '-'));
        }
        return anchors;
    }

    /** The target release's Node.js floor from its captured engines.node, e.g. ">=22.20.0" → "22.20". */
    private static String nodeFloor() {
        String engines = read(root().resolve("src/test/resources/fixtures/cli/" + target() + "/engines.json"));
        String node = JsonParser.parseString(engines).getAsJsonObject().get("node").getAsString();
        return node.replaceFirst("^>=", "").replaceFirst("\\.0$", "");
    }

    private static String target() {
        Properties props = new Properties();
        try (var in = Files.newInputStream(root().resolve("gradle.properties"))) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return props.getProperty("openspecTargetVersion");
    }

    private static Path root() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("gradle.properties")) && Files.exists(dir.resolve("README.md"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate the repository root");
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

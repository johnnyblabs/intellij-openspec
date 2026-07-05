package com.johnnyblabs.openspec.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Hygiene checks for the surfaces that reach the JetBrains Marketplace listing and
 * users' IDEs. The listing description is extracted at build time from README.md's
 * plugin-description section (see {@code pluginConfiguration} in build.gradle.kts),
 * so these checks enforce, on every build:
 *
 * <ol>
 *   <li>The README markers exist, in order, with substantive content between them —
 *       a missing marker would fail the build only at artifact-assembly time; this
 *       fails it at test time with a clearer message.</li>
 *   <li>The description section uses only absolute links. Relative links resolve
 *       against plugins.jetbrains.com and break on the listing page.</li>
 *   <li>plugin.xml declares no {@code <description>} of its own. The build overwrites
 *       it, so a description there is a dead second source of truth — that is exactly
 *       how the Marketplace listing carried a stale one-liner until v0.3.x.</li>
 *   <li>No internal-infrastructure identifiers appear on any publish surface
 *       (description section, plugin.xml, CHANGELOG.md — the changelog becomes the
 *       Marketplace change notes and the public release notes).</li>
 *   <li>The IDE-compatibility claim in the description matches the {@code sinceBuild}
 *       the build actually enforces.</li>
 * </ol>
 */
class MarketplaceListingHygieneTest {

    private static final String START_MARKER = "<!-- Plugin description -->";
    private static final String END_MARKER = "<!-- Plugin description end -->";

    /** Floor for the extracted description; the Marketplace minimum is 40, ours is richer. */
    private static final int DESCRIPTION_MIN_CHARS = 400;

    /**
     * Internal identifiers that must never reach a published surface (same set the
     * pre-push guard and the release workflow's scrub gate use). Character-class
     * boundaries, not {@code \b} — the guard's lesson: word-boundary escapes silently
     * fail in some grep implementations, so the convention is explicit classes.
     */
    private static final List<Pattern> LEAK_PATTERNS = List.of(
            Pattern.compile("forgejo", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[^a-z])geek([^a-z]|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|[^a-z])plane([^a-z]|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("OSPEC-[0-9]"),
            Pattern.compile("OSP-[0-9]"),
            Pattern.compile("johnb/"));

    /** Markdown links: {@code [text](target)}. Images ({@code ![...]}) match too — same rule. */
    private static final Pattern MD_LINK = Pattern.compile("\\]\\(([^)]+)\\)");

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("src/main/resources/META-INF/plugin.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repository root not found from " + Paths.get("").toAbsolutePath());
    }

    private static String read(String relative) {
        try {
            return Files.readString(repoRoot().resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String descriptionSection() {
        String readme = read("README.md");
        int start = readme.indexOf(START_MARKER);
        int end = readme.indexOf(END_MARKER);
        assertTrue(start >= 0, "README.md is missing the '" + START_MARKER + "' marker");
        assertTrue(end >= 0, "README.md is missing the '" + END_MARKER + "' marker");
        assertTrue(start < end, "README.md plugin-description markers are out of order");
        return readme.substring(start + START_MARKER.length(), end);
    }

    @Test
    void readmeCarriesASubstantiveDescriptionSection() {
        String section = descriptionSection().trim();
        assertTrue(section.length() >= DESCRIPTION_MIN_CHARS,
                "README plugin-description section is too thin (" + section.length()
                        + " chars, floor " + DESCRIPTION_MIN_CHARS
                        + ") — this text IS the Marketplace listing description");
        assertTrue(section.contains("**Features:**"),
                "README plugin-description section lost its feature list");
    }

    @Test
    void descriptionSectionUsesOnlyAbsoluteLinks() {
        Matcher links = MD_LINK.matcher(descriptionSection());
        while (links.find()) {
            String target = links.group(1);
            assertTrue(target.startsWith("https://") || target.startsWith("http://"),
                    "Relative link '" + target + "' in the plugin-description section — "
                            + "relative links break on the Marketplace listing page; use an absolute URL");
        }
    }

    @Test
    void pluginXmlDeclaresNoDescriptionOfItsOwn() {
        String pluginXml = read("src/main/resources/META-INF/plugin.xml");
        assertFalse(pluginXml.contains("<description>"),
                "plugin.xml declares a <description> — the build overwrites it from README.md's "
                        + "plugin-description section, so this is a dead second source of truth. Remove it "
                        + "and edit the README section instead.");
    }

    @Test
    void publishSurfacesCarryNoInternalIdentifiers() {
        // relative path -> content actually published from it
        List<String[]> surfaces = List.of(
                new String[]{"README.md (plugin-description section)", descriptionSection()},
                new String[]{"src/main/resources/META-INF/plugin.xml", read("src/main/resources/META-INF/plugin.xml")},
                new String[]{"CHANGELOG.md", read("CHANGELOG.md")});
        for (String[] surface : surfaces) {
            for (Pattern leak : LEAK_PATTERNS) {
                Matcher m = leak.matcher(surface[1]);
                if (m.find()) {
                    fail("Internal identifier " + leak.pattern() + " found on publish surface "
                            + surface[0] + " (match: '" + m.group() + "') — this content reaches "
                            + "the JetBrains Marketplace / public release notes");
                }
            }
        }
    }

    @Test
    void compatibilityClaimMatchesSinceBuild() {
        Matcher sinceBuild = Pattern.compile("sinceBuild\\s*=\\s*\"(\\d)(\\d)(\\d)\"")
                .matcher(read("build.gradle.kts"));
        assertTrue(sinceBuild.find(), "sinceBuild not found in build.gradle.kts");
        // Branch numbers encode the year and major release: 242 -> 2024.2.
        String humanVersion = "20" + sinceBuild.group(1) + sinceBuild.group(2) + "." + sinceBuild.group(3);
        assertTrue(descriptionSection().contains(humanVersion),
                "The plugin-description section must state the real IDE floor '" + humanVersion
                        + "' (derived from sinceBuild " + sinceBuild.group(1) + sinceBuild.group(2)
                        + sinceBuild.group(3) + ") — update whichever side changed");
    }
}

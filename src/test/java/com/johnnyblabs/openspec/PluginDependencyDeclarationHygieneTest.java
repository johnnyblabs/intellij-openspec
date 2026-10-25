package com.johnnyblabs.openspec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every plugin the build compiles against must be declared in {@code plugin.xml}. A Gradle {@code bundledPlugin(...)}
 * only puts the plugin on the compile and test classpath; at runtime the plugin classloader exposes declared
 * dependencies only. So code that uses an undeclared plugin compiles, passes every unit test and then fails in the
 * IDE with {@code NoClassDefFoundError}. That's how the terminal launcher fell back to the clipboard on every IDE.
 */
class PluginDependencyDeclarationHygieneTest {

    private static final Pattern BUNDLED_PLUGIN = Pattern.compile("bundledPlugin\\(\"([^\"]+)\"\\)");
    private static final Pattern DEPENDS = Pattern.compile("<depends(?:\\s[^>]*)?>\\s*([^<\\s]+)\\s*</depends>");

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    static Set<String> matches(Pattern pattern, String text) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) ids.add(m.group(1));
        return ids;
    }

    @Test
    void everyBundledPluginTheBuildCompilesAgainstIsDeclaredInPluginXml() throws IOException {
        Set<String> bundled = matches(BUNDLED_PLUGIN, read("build.gradle.kts"));
        Set<String> declared = matches(DEPENDS, read("src/main/resources/META-INF/plugin.xml"));
        assertFalse(bundled.isEmpty(), "found no bundledPlugin(...) in build.gradle.kts; the pattern is stale");

        Set<String> undeclared = new LinkedHashSet<>(bundled);
        undeclared.removeAll(declared);

        assertTrue(undeclared.isEmpty(), "plugin.xml has no <depends> for " + undeclared
                + ": its classes compile and pass unit tests but aren't visible at runtime. Declare each one, with"
                + " optional=\"true\" and a config-file when the feature has a fallback.");
    }

    @Test
    void thePatternsReadBothFormsOfDepends() {
        String xml = "<depends>a.b</depends>\n<depends optional=\"true\" config-file=\"x.xml\">c.d</depends>";

        assertEquals(Set.of("a.b", "c.d"), matches(DEPENDS, xml));
        assertEquals(Set.of("p.q"), matches(BUNDLED_PLUGIN, "        bundledPlugin(\"p.q\")"));
    }
}

package com.johnnyblabs.openspec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every project-level service is registered as a {@code <projectService>} in the plugin's descriptors, as the project's
 * config rule requires. Resolving the service in a test would prove nothing: a class annotated {@code @Service} resolves
 * as a light service without any entry, which is how a new one went unregistered unnoticed.
 */
class ServiceRegistrationHygieneTest {

    private static final Pattern PROJECT_SERVICE =
            Pattern.compile("@Service\\(\\s*(?:Service\\.)?Level\\.PROJECT\\s*\\)");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");

    @Test
    void everyAnnotatedProjectServiceIsRegistered() throws IOException {
        StringBuilder descriptors = new StringBuilder();
        try (Stream<Path> xml = Files.list(Path.of("src/main/resources/META-INF"))) {
            for (Path p : xml.filter(f -> f.toString().endsWith(".xml")).toList()) descriptors.append(Files.readString(p));
        }
        List<String> annotated = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source);
                if (!PROJECT_SERVICE.matcher(text).find()) continue;
                Matcher pkg = PACKAGE.matcher(text);
                assertTrue(pkg.find(), source.toString());
                String name = source.getFileName().toString().replace(".java", "");
                String fqn = pkg.group(1) + "." + name;
                annotated.add(fqn);
                if (!descriptors.toString().contains("serviceImplementation=\"" + fqn + "\"")) missing.add(fqn);
            }
        }
        assertFalse(annotated.isEmpty(), "the scan found no project services, so it isn't reading the sources");
        assertEquals(List.of(), missing, "register these as <projectService> in plugin.xml");
    }
}

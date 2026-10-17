package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.project.DumbAware;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the migration off the deprecated {@code ActionUtil.invokeAction} overloads (deprecated on 2024.3+): in-panel
 * links and buttons must launch actions through {@link ActionLauncher}. {@code build}/{@code test} compile against the
 * 2024.2 floor, where those overloads are not yet deprecated, so only this scan (and {@code verifyPlugin}) can catch a
 * regression.
 */
class ActionInvocationHygieneTest {

    /** A call, not a mention: {@code ActionUtil.invokeAction(} — javadoc may name the API without calling it. */
    private static final Pattern DEPRECATED_CALL = Pattern.compile("ActionUtil\\s*\\.\\s*invokeAction\\s*\\(");

    private static Path mainSources() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/java/com/johnnyblabs/openspec"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "could not locate the repository root from " + Paths.get("").toAbsolutePath());
        return dir.resolve("src/main/java");
    }

    @Test
    void noMainSourceCallsTheDeprecatedInvokeAction() throws IOException {
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(mainSources())) {
            javaFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        // Anti-vacuity: the scan must actually see the plugin's sources, including the helper and a migrated site.
        assertTrue(javaFiles.size() > 50, "expected to scan the plugin's sources, found " + javaFiles.size());
        assertTrue(javaFiles.stream().anyMatch(p -> p.endsWith("actions/ActionLauncher.java")), "helper not scanned");
        assertTrue(javaFiles.stream().anyMatch(p -> p.endsWith("toolwindow/GettingStartedPanel.java")),
                "a migrated call site was not scanned");

        List<String> offenders = javaFiles.stream().filter(p -> {
            try {
                return DEPRECATED_CALL.matcher(Files.readString(p)).find();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }).map(Path::toString).toList();
        assertTrue(offenders.isEmpty(),
                "use ActionLauncher.launch instead of the deprecated ActionUtil.invokeAction: " + offenders);
    }

    @Test
    void thePatternMatchesACallButNotAMention() {
        // Locks the guard itself: it must catch the call forms that existed, and not trip on documentation.
        assertTrue(DEPRECATED_CALL.matcher("ActionUtil.invokeAction(action, context, \"P\", null, null);").find());
        assertTrue(DEPRECATED_CALL.matcher("com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction(\n a)").find());
        assertFalse(DEPRECATED_CALL.matcher("unlike the {@code ActionUtil.invokeAction} overloads").find());
    }

    @Test
    void actionsLaunchedFromPanelLinksAreDumbAware() {
        // ActionLauncher's update session silently skips non-DumbAware actions while the IDE is indexing.
        assertTrue(DumbAware.class.isAssignableFrom(OpenSpecProposeAction.class),
                "Propose must stay usable from in-panel links during indexing");
        assertTrue(DumbAware.class.isAssignableFrom(DeltaSpecDiffAction.class),
                "the deltas-preview diff link must stay usable during indexing");
    }
}

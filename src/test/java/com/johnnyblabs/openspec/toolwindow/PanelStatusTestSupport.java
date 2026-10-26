package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.util.CliRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Gives a workflow-panel test a real change status. In a light test the OpenSpec CLI can't start (the project
 * directory isn't on disk), so with no status the panel's refresh drops its active change, and a test only passes
 * if it happens to look before that. Feeding the captured 1.7.0 {@code status-complete.json}, renamed to the test's
 * change, keeps the change selected. The seam and the project's status cache are reset when {@code disposable} is
 * disposed, because the light project outlives the test.
 */
final class PanelStatusTestSupport {

    private PanelStatusTestSupport() {
    }

    /** Feeds the captured status for {@code changeName}. */
    static void feedCapturedStatus(Project project, String changeName, Disposable disposable) {
        feedCapturedStatus(project, changeName, disposable, artifactId -> null);
    }

    /**
     * Feeds the captured status for {@code changeName}, and {@code instructions.apply(artifactId)} for
     * {@code openspec instructions <artifactId>} (null fails the call).
     */
    static void feedCapturedStatus(Project project, String changeName, Disposable disposable,
                                   Function<String, String> instructions) {
        String status = fixture("1.7.0/status-complete.json").replace("\"demo-change\"", "\"" + changeName + "\"");
        ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
        orchestration.setStatusCliForTest((p, args) -> {
            if (args.length > 0 && args[0].equals("status")) return new CliRunner.CliResult(0, status, "");
            if (args.length > 1 && args[0].equals("instructions")) {
                String json = instructions.apply(args[1]);
                if (json != null) return new CliRunner.CliResult(0, json, "");
            }
            return new CliRunner.CliResult(1, "", "not fed: " + String.join(" ", args));
        });
        Disposer.register(disposable, () -> {
            orchestration.setStatusCliForTest(null);
            orchestration.invalidateAllCaches();
        });
    }

    static String fixture(String name) {
        String path = "/fixtures/cli/" + name;
        try (InputStream is = PanelStatusTestSupport.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Fixture not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

package com.johnnyblabs.openspec.services;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.util.CliRunner;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Context assembly runs one CLI call per active change plus one for the specs, under a progress indicator; a cancel
 * must stop it before the next call. A light test, because {@code checkCanceled} needs the application's
 * ProgressManager.
 */
public class ExploreContextCancelTest extends BasePlatformTestCase {

    private Path root;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        root = Files.createTempDirectory("explore-cancel").toRealPath();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            com.intellij.openapi.util.io.FileUtil.delete(root);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testACancelledAssemblyDoesNotListSpecs() throws Exception {
        Files.createDirectories(root.resolve("openspec/specs/billing"));
        Files.writeString(root.resolve("openspec/specs/billing/spec.md"), "# billing\n");
        Project project = mock(Project.class);
        ChangeService changes = mock(ChangeService.class);
        when(project.getBasePath()).thenReturn(root.toString());
        when(project.getService(ChangeService.class)).thenReturn(changes);
        ProgressIndicator indicator = new EmptyProgressIndicator();
        // runProcess starts the indicator, which clears an earlier cancel, so cancel while the changes are listed.
        when(changes.getActiveChanges()).thenAnswer(invocation -> {
            indicator.cancel();
            return List.of();
        });
        List<String> calls = new ArrayList<>();
        ExploreContextService service = new ExploreContextService(project);
        service.setCliForTest(new ExploreContextService.Cli() {
            @Override
            public @Nullable String version() {
                return "1.15.0";
            }

            @Override
            public CliRunner.CliResult run(String... args) {
                calls.add(String.join(" ", args));
                return new CliRunner.CliResult(1, "", "");
            }
        });

        assertThrows(ProcessCanceledException.class, () ->
                ProgressManager.getInstance().runProcess(service::assembleContext, indicator));
        assertEquals("list --specs is never started once cancelled", List.of(), calls);
    }

    public void testACancelStopsAssemblyBeforeTheNextCliCall() throws Exception {
        for (String change : List.of("first", "second")) {
            Files.createDirectories(root.resolve("openspec/changes/" + change));
            Files.writeString(root.resolve("openspec/changes/" + change + "/proposal.md"), change);
        }
        Project project = mock(Project.class);
        ChangeService changes = mock(ChangeService.class);
        when(project.getBasePath()).thenReturn(root.toString());
        when(project.getService(ChangeService.class)).thenReturn(changes);
        when(changes.getActiveChanges()).thenReturn(List.of(
                new Change("first", root.resolve("openspec/changes/first").toString()),
                new Change("second", root.resolve("openspec/changes/second").toString())));

        ProgressIndicator indicator = new EmptyProgressIndicator();
        List<String> calls = new ArrayList<>();
        ExploreContextService service = new ExploreContextService(project);
        service.setCliForTest(new ExploreContextService.Cli() {
            @Override
            public @Nullable String version() {
                return "1.15.0";
            }

            @Override
            public CliRunner.CliResult run(String... args) {
                calls.add(String.join(" ", args));
                indicator.cancel(); // the user cancels while the first call runs
                return new CliRunner.CliResult(1, "", "");
            }
        });

        assertThrows(ProcessCanceledException.class, () ->
                ProgressManager.getInstance().runProcess(service::assembleContext, indicator));
        assertEquals(List.of("status --change first --json"), calls);
    }
}

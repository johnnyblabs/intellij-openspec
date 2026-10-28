package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.DeltaSpecOperation;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import com.johnnyblabs.openspec.validation.BuiltInValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract test: Sync Specs against a real {@code openspec archive} of a change with nested capabilities, captured from
 * CLI 1.15.0 ({@code fixtures/cli/1.15.0/archive-nested/}, re-captured by {@code scripts/capture-nested-archive-fixture.py}).
 * The change adds to a flat capability, modifies an existing nested one, adds a new nested one and its parent folder's own
 * capability, and has a delta in a dot-folder the archive ignores. Syncing it must write exactly the main specs the
 * archive wrote, byte for byte, and no others. Uses a real temp directory, because Sync reads and writes with java.nio.
 */
public class NestedCapabilitySyncContractTest extends BasePlatformTestCase {

    private static final Path FIXTURE = Paths.get("src/test/resources/fixtures/cli/1.15.0/archive-nested");

    private Path base;
    private SpecSyncService service;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = FileUtil.createTempDirectory("nested-sync", null).toPath();
        copyTree(FIXTURE.resolve("before"), base);
        copyTree(FIXTURE.resolve("change"), base.resolve("openspec/changes/c1"));
        Files.createDirectories(base.resolve("openspec/specs"));
        refresh();
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(base.toString());
        when(project.getService(BuiltInValidator.class)).thenReturn(getProject().getService(BuiltInValidator.class));
        service = new SpecSyncService(project);
    }

    public void testCapabilitiesAreKeyedByRelativePathInArchiveOrder() {
        List<String> caps = service.parseDeltaSpecs("c1").stream()
                .map(DeltaSpecOperation::capabilityName).distinct().toList();
        // The order the archive printed under "Specs to update"; the dot-folder is skipped.
        assertEquals(List.of("flat", "identity", "identity/sessions", "identity/user-auth"), caps);
        assertEquals(new HashSet<>(caps), service.getCapabilities("c1"));
        assertTrue(archiveOutput().contains("identity/user-auth: create"));
    }

    public void testAChangeWhoseOnlyDeltaIsNestedHasDeltaSpecs() throws IOException {
        Path only = base.resolve("openspec/changes/c2/specs/identity/user-auth");
        Files.createDirectories(only);
        Files.copy(FIXTURE.resolve("change/specs/identity/user-auth/spec.md"), only.resolve("spec.md"));
        refresh();
        assertTrue(service.hasDeltaSpecs("c2"));
        assertEquals(Map.of("identity/user-auth", List.of("c1", "c2")), service.detectConflicts(List.of("c1", "c2")));
    }

    public void testSyncWritesExactlyWhatTheArchiveWrote() throws IOException {
        List<SpecSyncResult> results = service.computeSync("c1");
        for (SpecSyncResult r : results) {
            assertTrue(r.capabilityName() + " refused: " + r.refusals(), r.refusals().isEmpty());
            assertTrue(r.mainSpecPath(), r.mainSpecPath().replace('\\', '/')
                    .endsWith("/openspec/specs/" + r.capabilityName() + "/spec.md"));
        }

        service.applySync(results);

        Map<String, String> expected = specs(FIXTURE.resolve("after"));
        Map<String, String> actual = specs(base);
        assertEquals("the same main spec files, and no others", expected.keySet(), actual.keySet());
        for (Map.Entry<String, String> e : expected.entrySet()) {
            assertEquals(e.getKey(), e.getValue(), actual.get(e.getKey()));
        }
        assertTrue(actual.get("openspec/specs/identity/user-auth/spec.md")
                .startsWith("# identity/user-auth Specification\n"));
    }

    /** Every spec.md under {@code root/openspec/specs}, keyed by its {@code /}-separated path from root, as LF text. */
    private static Map<String, String> specs(Path root) throws IOException {
        Map<String, String> specs = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root.resolve("openspec/specs"))) {
            for (Path p : files.filter(f -> f.getFileName().toString().equals("spec.md")).toList()) {
                String key = root.relativize(p).toString().replace('\\', '/');
                specs.put(key, Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"));
            }
        }
        return specs;
    }

    /** Copies a fixture tree as LF text, whatever git did on checkout, as the CLI wrote it. */
    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                Files.createDirectories(target.getParent());
                Files.writeString(target, Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"),
                        StandardCharsets.UTF_8);
            }
        }
    }

    private void refresh() {
        VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(base.toString());
        assertNotNull(root);
        VfsUtil.markDirtyAndRefresh(false, true, true, root);
    }

    private static String archiveOutput() {
        try {
            return Files.readString(FIXTURE.resolve("archive-human.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

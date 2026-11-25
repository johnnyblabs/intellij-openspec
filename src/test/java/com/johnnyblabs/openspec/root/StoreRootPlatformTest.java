package com.johnnyblabs.openspec.root;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.johnnyblabs.openspec.coordination.CoordinationPaths;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.SpecSyncService;
import com.johnnyblabs.openspec.toolwindow.GettingStartedPanel;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.validation.BuiltInValidator;
import com.johnnyblabs.openspec.validation.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * A project whose {@code openspec/config.yaml} points at a store outside the project: every surface reads and writes
 * the store's tree. Real disk, so a heavy project; the machine-wide inputs (registry, global config) live in a temp
 * directory instead of the developer's own.
 */
public class StoreRootPlatformTest extends HeavyPlatformTestCase {

    private static final String SPEC = """
            # cap-a Specification

            ## Purpose
            Demo capability held in a store.

            ## Requirements
            ### Requirement: Demo behaviour
            The system SHALL do the demo thing.

            #### Scenario: Demo works
            - **WHEN** the demo runs
            - **THEN** it succeeds
            """;

    private Path machine;
    private Path base;
    private OpenSpecRootResolver resolver;
    private final AtomicInteger inputReads = new AtomicInteger();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Outside the test project, as a real store is; allowed for this test only.
        machine = Files.createTempDirectory("osp-store-root-test").toRealPath();
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), machine.toString());
        base = Path.of(getProject().getBasePath());
        resolver = OpenSpecRootResolver.getInstance(getProject());
        resolver.setInputsForTests(release -> {
            inputReads.incrementAndGet();
            return new RootResolver.Inputs(machine.resolve("config/openspec/config.json"),
                    CoordinationPaths.resolve(Map.of("XDG_DATA_HOME", machine.resolve("data").toString())::get,
                            false, null),
                    "2.1.1");
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            // Delete through the VFS while access is still allowed, so it forgets these files; a later test's refresh
            // would otherwise trip over them outside its allowed roots.
            VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(machine);
            if (dir != null) WriteAction.run(() -> dir.delete(this));
            FileUtil.delete(machine.toFile());
        } finally {
            super.tearDown();
        }
    }

    // ---- Surfaces read and write the store ----------------------------------------------------------------------

    public void testThePointerResolvesTheStoreAndTheSurfacesReadIt() throws Exception {
        Path store = store("demo", "demo");
        Files.createDirectories(store.resolve("openspec/changes/add-thing/specs/cap-a"));
        Files.writeString(store.resolve("openspec/changes/add-thing/proposal.md"), "## Why\n\nA store change.\n");
        Files.writeString(store.resolve("openspec/changes/add-thing/specs/cap-a/spec.md"),
                "## ADDED Requirements\n\n### Requirement: Extra\nThe system SHALL do more.\n\n"
                        + "#### Scenario: More\n- **WHEN** more runs\n- **THEN** it succeeds\n");
        pointAt("demo");

        resolveOffEdt();

        VirtualFile root = OpenSpecFileUtil.getOpenSpecRoot(getProject());
        assertNotNull(root);
        assertEquals(store.resolve("openspec").toString().replace('\\', '/'), root.getPath());
        assertEquals("demo", resolver.snapshot().resolution().storeId());
        assertEquals(store.resolve("openspec").toString().replace('\\', '/'), OpenSpecFileUtil.openspecPath(getProject()));
        assertEquals("cap-a", OpenSpecFileUtil.getSpecsDir(getProject()).getChildren()[0].getName());

        List<Change> changes = getProject().getService(ChangeService.class).getActiveChanges();
        assertTrue("the store's change is listed: " + changes,
                changes.stream().anyMatch(c -> c.getName().equals("add-thing")));

        List<SpecSyncResult> sync = getProject().getService(SpecSyncService.class).computeSync("add-thing");
        assertEquals(1, sync.size());
        assertEquals("Sync Specs writes into the store",
                store.resolve("openspec/specs/cap-a/spec.md").toString().replace('\\', '/'),
                sync.get(0).mainSpecPath().replace('\\', '/'));
        assertTrue(sync.get(0).projectedContent().contains("Requirement: Extra"));
    }

    public void testVerificationAndExploreReadTheStore() throws Exception {
        Path store = store("demo", "demo");
        Files.createDirectories(store.resolve("openspec/changes/add-thing"));
        Files.writeString(store.resolve("openspec/changes/add-thing/proposal.md"), "## Why\n\nA store change.\n");
        pointAt("demo");
        resolveOffEdt();

        var report = getProject().getService(com.johnnyblabs.openspec.services.VerificationService.class)
                .verify("add-thing");
        assertTrue("the store's change directory is found: " + report.getFindings(), report.getFindings().stream()
                .noneMatch(f -> f.description().startsWith("Change directory not found")));

        String context = getProject().getService(com.johnnyblabs.openspec.services.ExploreContextService.class)
                .assembleContext();
        assertTrue("Explore lists the store's capabilities", context.contains("cap-a"));
    }

    public void testTheStoreProblemReadsAsTheCliWroteIt() {
        // Upstream ends the identity message with the doctor hint and repeats it as the fix: show it once.
        var dup = new RootResolution.Problem("store_identity_mismatch",
                "Store 'x' metadata id 'y' does not match its registered id. Run openspec store doctor x to inspect it.",
                "Run openspec store doctor x to inspect it.");
        assertEquals(dup.message(), GettingStartedPanel.problemText(dup));
        var plain = new RootResolution.Problem("unknown_store", "Unknown store 'x'.", "Register it.");
        assertEquals("Unknown store 'x'. Register it.", GettingStartedPanel.problemText(plain));
        assertEquals("ENOTDIR", GettingStartedPanel.problemText(new RootResolution.Problem("context_failed", "ENOTDIR", null)));
    }

    public void testBuiltInValidationReadsTheStore() throws Exception {
        Path store = store("demo", "demo");
        Path delta = store.resolve("openspec/changes/broken/specs/cap-a/spec.md");
        Files.createDirectories(delta.getParent());
        Files.writeString(store.resolve("openspec/changes/broken/proposal.md"), "## Why\n\nBroken.\n");
        Files.writeString(delta, "## ADDED Requirements\n\n### Requirement: No scenario\nThe system SHALL x.\n");
        pointAt("demo");
        resolveOffEdt();

        ValidationResult result = new BuiltInValidator(getProject()).validateChanges();

        assertFalse("the store's broken change fails validation", result.passed());
        assertTrue(result.issues().stream().anyMatch(i -> i.filePath() != null
                && i.filePath().replace('\\', '/').startsWith(store.toString().replace('\\', '/'))));
    }

    public void testAProjectWithItsOwnPlanningTreeNeverAsksTheResolver() throws Exception {
        Files.createDirectories(base.resolve("openspec/changes"));
        Files.writeString(base.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        VfsUtil.markDirtyAndRefresh(false, true, true, LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base));

        VirtualFile root = OpenSpecFileUtil.getOpenSpecRoot(getProject());

        assertNotNull(root);
        assertEquals(base.resolve("openspec").toString().replace('\\', '/'), root.getPath());
        assertEquals("the fast local check needs no resolution", 0, inputReads.get());
    }

    public void testANewChangeWithoutTheCliLandsInTheStore() throws Exception {
        Path store = store("demo", "demo");
        pointAt("demo");
        resolveOffEdt();

        VirtualFile change = getProject().getService(com.johnnyblabs.openspec.scaffolding.ScaffoldingService.class)
                .createEmptyChange("add-thing", "spec-driven");

        assertEquals("as the CLI's new change does from the app",
                store.resolve("openspec/changes/add-thing").toString().replace('\\', '/'), change.getPath());
        assertFalse(Files.exists(base.resolve("openspec/changes")));
    }

    public void testInitWritesTheProjectsOwnOpenspecEvenWithADefaultStore() throws Exception {
        store("demo", "demo");
        Files.createDirectories(machine.resolve("config/openspec"));
        Files.writeString(machine.resolve("config/openspec/config.json"), "{\"defaultStore\": \"demo\"}");
        resolveOffEdt();
        assertEquals(RootResolution.Source.GLOBAL_DEFAULT, resolver.snapshot().resolution().source());
        com.johnnyblabs.openspec.services.CliDetectionTestSupport.fakeUnavailable(
                getProject().getService(com.johnnyblabs.openspec.services.CliDetectionService.class));

        Files.createDirectories(base);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base);
        var done = new java.util.concurrent.atomic.AtomicReference<VirtualFile>();
        getProject().getService(com.johnnyblabs.openspec.scaffolding.ScaffoldingService.class).initOpenSpec(
                com.intellij.openapi.application.ModalityState.nonModal(), done::set, e -> fail(e.toString()));
        waitFor(() -> done.get() != null);

        assertEquals(base.resolve("openspec").toString().replace('\\', '/'), done.get().getPath());
        assertEquals("the new local tree wins over the default store",
                base.resolve("openspec").toString().replace('\\', '/'),
                OpenSpecFileUtil.getOpenSpecRoot(getProject()).getPath());
    }

    // ---- Problems ------------------------------------------------------------------------------------------------

    public void testAnUnknownStoreIsAProblemNotAnEmptyProject() throws Exception {
        store("demo", "demo");
        pointAt("ghost");

        resolveOffEdt();

        assertNull(OpenSpecFileUtil.getOpenSpecRoot(getProject()));
        RootResolution.Problem problem = resolver.snapshot().problem();
        assertNotNull(problem);
        assertEquals("unknown_store", problem.code());
        assertTrue(problem.message(), problem.message().startsWith("Declared in "));
        assertEquals(GettingStartedPanel.State.NOT_INITIALIZED, GettingStartedPanel.detectState(getProject()));
    }

    public void testUntilTheFirstResolutionTheToolWindowIsResolving() throws Exception {
        store("demo", "demo");
        pointAt("demo");
        // As before the first resolution finishes (the startup activity has already resolved this project).
        resolver.resetToPendingForTests();
        assertEquals(GettingStartedPanel.State.RESOLVING, GettingStartedPanel.detectState(getProject()));
    }

    // ---- The cache --------------------------------------------------------------------------------------------------

    public void testReadingTheRootDoesNoWork() throws Exception {
        store("demo", "demo");
        pointAt("demo");
        resolveOffEdt();
        int reads = inputReads.get();

        for (int i = 0; i < 100; i++) {
            assertNotNull(OpenSpecFileUtil.getOpenSpecRoot(getProject()));
            assertTrue(OpenSpecFileUtil.isOpenSpecProject(getProject()));
            assertNotNull(OpenSpecFileUtil.getSpecsDir(getProject()));
            assertNotNull(resolver.snapshot().openspecDir());
        }

        assertEquals("reading the root re-resolved nothing", reads, inputReads.get());
    }

    public void testADeletedRootIsNeverHandedOutAndIsReResolved() throws Exception {
        Path store = store("demo", "demo");
        pointAt("demo");
        resolveOffEdt();
        VirtualFile dir = resolver.snapshot().openspecDir();
        assertNotNull(dir);

        FileUtil.delete(store.resolve("openspec").toFile());
        VfsUtil.markDirtyAndRefresh(false, true, true, LocalFileSystem.getInstance().refreshAndFindFileByNioFile(store));
        assertFalse(dir.isValid());

        for (int i = 0; i < 50; i++) {
            assertNull("a dead directory handle is never returned", resolver.snapshot().openspecDir());
        }
        // Fifty reads queued one re-resolve, not fifty postponements: it finishes, and the store is now unhealthy.
        waitFor(() -> resolver.snapshot().problem() != null);
        assertEquals("unhealthy_store_root", resolver.snapshot().problem().code());
    }

    public void testTheFacadeNeverRefreshesTheFileSystem() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/johnnyblabs/openspec/util/OpenSpecFileUtil.java"));
        for (String forbidden : List.of("refreshAndFind", ".refresh(", "markDirty", "java.nio.file.Files")) {
            assertFalse("OpenSpecFileUtil must not use " + forbidden, source.contains(forbidden));
        }
    }

    public void testAddingAPointerReResolvesToTheStore() throws Exception {
        Path store = store("demo", "demo");
        Files.createDirectories(base.resolve("openspec"));
        Files.writeString(base.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        resolveOffEdt();
        assertEquals(RootResolution.Source.NEAREST, resolver.snapshot().resolution().source());

        VirtualFile config = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base.resolve("openspec/config.yaml"));
        WriteAction.run(() -> config.setBinaryContent("schema: spec-driven\nstore: demo\n".getBytes(StandardCharsets.UTF_8)));

        waitFor(() -> resolver.snapshot().resolution().source() == RootResolution.Source.DECLARED);
        assertEquals(store.resolve("openspec").toString().replace('\\', '/'),
                OpenSpecFileUtil.getOpenSpecRoot(getProject()).getPath());
    }

    public void testTheOldStoreIsNoLongerWatchedAfterTheRootMoves() throws Exception {
        Path one = store("one", "one");
        Path two = store("two", "two");
        pointAt("one");
        resolveOffEdt();
        assertTrue(watched().contains(sys(one.resolve("openspec"))));

        pointAt("two");
        resolveOffEdt();

        assertTrue(watched().contains(sys(two.resolve("openspec"))));
        assertFalse("the first store's watch was released", watched().contains(sys(one.resolve("openspec"))));
    }

    // ---- Helpers ----------------------------------------------------------------------------------------------------

    /** A healthy store at {@code <machine>/stores/<dir>}, registered as {@code id}, with one main spec. */
    private Path store(String id, String dir) throws Exception {
        Path store = machine.resolve("stores").resolve(dir);
        Files.createDirectories(store.resolve(".openspec-store"));
        Files.writeString(store.resolve(".openspec-store/store.yaml"), "version: 1\nid: " + id + "\n");
        Files.createDirectories(store.resolve("openspec/specs/cap-a"));
        Files.createDirectories(store.resolve("openspec/changes/archive"));
        Files.writeString(store.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(store.resolve("openspec/specs/cap-a/spec.md"), SPEC);
        Path registry = machine.resolve("data/openspec/stores/registry.yaml");
        Files.createDirectories(registry.getParent());
        String existing = Files.exists(registry) ? Files.readString(registry) : "version: 1\nstores:\n";
        Files.writeString(registry, existing + "  " + id + ":\n    backend:\n      type: git\n      local_path: "
                + store.toString().replace('\\', '/') + "\n");
        return store;
    }

    private void pointAt(String id) throws Exception {
        Files.createDirectories(base.resolve("openspec"));
        Files.writeString(base.resolve("openspec/config.yaml"), "store: " + id + "\n");
    }

    private void resolveOffEdt() throws Exception {
        var work = ApplicationManager.getApplication().executeOnPooledThread(resolver::resolveNow);
        waitFor(work::isDone);
        work.get();
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        assertTrue("timed out", condition.getAsBoolean());
    }

    private List<String> watched() {
        return resolver.watchRequests().stream().map(LocalFileSystem.WatchRequest::getRootPath).toList();
    }

    private static String sys(Path p) {
        return p.toString().replace('\\', '/');
    }
}

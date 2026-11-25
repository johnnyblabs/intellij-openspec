package com.johnnyblabs.openspec.root;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.ui.UIUtil;
import com.johnnyblabs.openspec.coordination.CoordinationPaths;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Working with specs that live in a store outside the project: the tree says where they live, an ignored pointer is
 * shown, store files are writable without the not-in-project prompt and carry a banner, and only the not-set-up hint
 * initializes. Real disk (a heavy project): the platform treats in-memory test files as always writable.
 */
public class StoreRootEditingPlatformTest extends HeavyPlatformTestCase {

    private Path machine;
    private Path base;
    private OpenSpecRootResolver resolver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        machine = Files.createTempDirectory("osp-store-edit-test").toRealPath();
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), machine.toString());
        base = Path.of(getProject().getBasePath());
        Files.createDirectories(base);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base);
        resolver = OpenSpecRootResolver.getInstance(getProject());
        resolver.setInputsForTests(release -> new RootResolver.Inputs(machine.resolve("config/openspec/config.json"),
                CoordinationPaths.resolve(Map.of("XDG_DATA_HOME", machine.resolve("data").toString())::get, false, null),
                "2.1.1"));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(machine);
            if (dir != null) WriteAction.run(() -> dir.delete(this));
            FileUtil.delete(machine.toFile());
        } finally {
            super.tearDown();
        }
    }

    // ---- The tree says where the specs live -------------------------------------------------------------------------

    public void testTheTreeRootNamesADeclaredStore() throws Exception {
        Path store = store("demo");
        pointAt("demo");
        resolveOffEdt();

        SpecTreeModel.TreeNodeData root = rootData();

        assertEquals(SpecTreeModel.TreeNodeType.ROOT, root.type());
        assertEquals("OpenSpec · store demo (declared)", root.label());
        assertTrue(root.tooltip(), root.tooltip().contains(store.resolve("openspec").toString()));
        assertTrue(root.tooltip(), root.tooltip().endsWith("Commit changes from the store's own repository."));
        assertEquals("expansion is keyed by a fixed id, not the label", "root", root.getPathElementId());
    }

    public void testTheTreeRootNamesTheGlobalDefault() throws Exception {
        store("demo");
        Files.createDirectories(machine.resolve("config/openspec"));
        Files.writeString(machine.resolve("config/openspec/config.json"), "{\"defaultStore\": \"demo\"}");
        resolveOffEdt();

        assertEquals("OpenSpec · store demo (global default)", rootData().label());
    }

    public void testAProjectsOwnRootKeepsThePlainLabel() throws Exception {
        Files.createDirectories(base.resolve("openspec/changes"));
        Files.writeString(base.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        resolveOffEdt();

        SpecTreeModel.TreeNodeData root = rootData();
        assertEquals("OpenSpec", root.label());
        assertNull(root.tooltip());
        assertEquals("root", root.getPathElementId());
    }

    // ---- An ignored pointer ---------------------------------------------------------------------------------------

    public void testAnIgnoredPointerIsAHintAndNotifiedOnceWhenItBegins() throws Exception {
        store("demo");
        store("other");
        Files.createDirectories(base.resolve("openspec/changes"));
        List<Notification> ignored = collectIgnoredNotifications();

        Files.writeString(base.resolve("openspec/config.yaml"), "store: demo\n");
        resolveOffEdt();
        DefaultMutableTreeNode hint = hintUnderRoot(SpecTreeModel.HINT_IGNORED_POINTER);
        assertNotNull("the ignored-pointer hint is shown", hint);
        assertTrue(String.valueOf(hint), String.valueOf(hint).startsWith("config.yaml names store 'demo'"));
        assertEquals(1, settledCount(ignored));

        // Still ignored, but a different answer: the resolver publishes again, and must not notify again.
        Files.writeString(base.resolve("openspec/config.yaml"), "store: other\n");
        resolveOffEdt();
        assertEquals("notified once while the pointer stays ignored", 1, settledCount(ignored));

        // Leaving the state and coming back is a new beginning.
        Files.writeString(base.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        resolveOffEdt();
        assertNull(hintUnderRoot(SpecTreeModel.HINT_IGNORED_POINTER));
        Files.writeString(base.resolve("openspec/config.yaml"), "store: demo\n");
        resolveOffEdt();
        assertEquals(2, settledCount(ignored));
    }

    // ---- Only the not-set-up hint initializes ---------------------------------------------------------------------

    public void testOnlyTheNotSetUpHintInitializes() throws Exception {
        store("demo");
        resolveOffEdt();
        DefaultMutableTreeNode notSetUp = hintUnderRoot(SpecTreeModel.HINT_NOT_SET_UP);
        assertNotNull(notSetUp);
        assertTrue("the not-set-up hint initializes", SpecTreeModel.initializesOnActivation(notSetUp));

        pointAt("ghost");
        resolveOffEdt();
        DefaultMutableTreeNode problem = hintUnderRoot(SpecTreeModel.HINT_STORE_PROBLEM);
        assertNotNull(problem);
        assertTrue(String.valueOf(problem), String.valueOf(problem).startsWith("The OpenSpec store 'ghost' can't be used: "));
        assertFalse("a store problem never initializes", SpecTreeModel.initializesOnActivation(problem));

        Files.createDirectories(base.resolve("openspec/changes"));
        pointAt("demo");
        resolveOffEdt();
        DefaultMutableTreeNode ignoredHint = hintUnderRoot(SpecTreeModel.HINT_IGNORED_POINTER);
        assertNotNull(ignoredHint);
        assertFalse("an ignored pointer never initializes", SpecTreeModel.initializesOnActivation(ignoredHint));
    }

    // ---- Store files are writable, and carry a banner -------------------------------------------------------------

    public void testAStoreFileIsWritableWithoutThePromptAndOthersAreNot() throws Exception {
        Path store = store("demo");
        pointAt("demo");
        resolveOffEdt();
        NonProjectFileWritingAccessProvider.enableChecksInTests(getTestRootDisposable());
        VirtualFile spec = vf(store.resolve("openspec/specs/cap-a/spec.md"));
        Path elsewhere = Files.createDirectories(machine.resolve("elsewhere"));
        VirtualFile unrelated = vf(Files.writeString(elsewhere.resolve("notes.md"), "x"));

        assertTrue("a store spec is writable", NonProjectFileWritingAccessProvider.isWriteAccessAllowed(spec, getProject()));
        assertFalse("an unrelated file outside the project keeps the prompt",
                NonProjectFileWritingAccessProvider.isWriteAccessAllowed(unrelated, getProject()));

        // The baseline: before the root is known, the same store file is protected, so the check above is real.
        resolver.resetToPendingForTests();
        assertFalse(NonProjectFileWritingAccessProvider.isWriteAccessAllowed(spec, getProject()));
    }

    public void testTheBannerNamesTheStoreOnlyOnStoreFilesAndCanBeDismissed() throws Exception {
        Path store = store("demo");
        pointAt("demo");
        resolveOffEdt();
        VirtualFile spec = vf(store.resolve("openspec/specs/cap-a/spec.md"));
        VirtualFile projectFile = vf(Files.writeString(base.resolve("README.md"), "x"));
        StoreFileBanner banner = new StoreFileBanner();

        assertNull("no banner on a project file", banner.collectNotificationData(getProject(), projectFile));
        var data = banner.collectNotificationData(getProject(), spec);
        assertNotNull("a banner on a store file", data);

        FileEditor editor = FileEditorManager.getInstance(getProject()).openFile(spec, false)[0];
        try {
            JComponent panel = data.apply(editor);
            assertTrue(panel instanceof EditorNotificationPanel);
            String text = String.join(" ", UIUtil.findComponentsOfType(panel, JLabel.class).stream()
                    .map(JLabel::getText).filter(t -> t != null).toList());
            assertTrue(text, text.contains("This file is in OpenSpec store 'demo', outside this project."));

            HyperlinkLabel dismiss = UIUtil.findComponentsOfType(panel, HyperlinkLabel.class).stream()
                    .filter(l -> StoreFileBanner.DONT_SHOW.equals(l.getText())).findFirst().orElseThrow();
            dismiss.doClick();
        } finally {
            FileEditorManager.getInstance(getProject()).closeFile(spec);
        }
        assertTrue(PropertiesComponent.getInstance(getProject()).getBoolean(StoreFileBanner.DISMISSED_KEY));
        assertNull("dismissed for the project", banner.collectNotificationData(getProject(), spec));
    }

    public void testALocalRootHasNoBannerAndNoWriteException() throws Exception {
        Files.createDirectories(base.resolve("openspec/specs/cap-a"));
        Path spec = Files.writeString(base.resolve("openspec/specs/cap-a/spec.md"), "# x\n");
        resolveOffEdt();

        VirtualFile file = vf(spec);
        assertNull(OutsideRoot.holding(getProject(), file));
        assertNull(new StoreFileBanner().collectNotificationData(getProject(), file));
    }

    // ---- Helpers ----------------------------------------------------------------------------------------------------

    private SpecTreeModel.TreeNodeData rootData() {
        var model = new SpecTreeModel(getProject()).buildModel();
        return (SpecTreeModel.TreeNodeData) ((DefaultMutableTreeNode) model.getRoot()).getUserObject();
    }

    private DefaultMutableTreeNode hintUnderRoot(String kind) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) new SpecTreeModel(getProject()).buildModel().getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            if (child.getUserObject() instanceof SpecTreeModel.TreeNodeData d
                    && d.type() == SpecTreeModel.TreeNodeType.HINT && kind.equals(d.artifactId())) {
                return child;
            }
        }
        return null;
    }

    private List<Notification> collectIgnoredNotifications() {
        List<Notification> seen = new ArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(Notification notification) {
                if ("OpenSpec store ignored".equals(notification.getTitle())) seen.add(notification);
            }
        });
        return seen;
    }

    private int settledCount(List<Notification> seen) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        return seen.size();
    }

    private Path store(String id) throws Exception {
        Path store = machine.resolve("stores").resolve(id);
        Files.createDirectories(store.resolve(".openspec-store"));
        Files.writeString(store.resolve(".openspec-store/store.yaml"), "version: 1\nid: " + id + "\n");
        Files.createDirectories(store.resolve("openspec/specs/cap-a"));
        Files.createDirectories(store.resolve("openspec/changes"));
        Files.writeString(store.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(store.resolve("openspec/specs/cap-a/spec.md"), "# cap-a Specification\n");
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

    private VirtualFile vf(Path p) {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(p);
        assertNotNull(p.toString(), file);
        return file;
    }

    private void resolveOffEdt() throws Exception {
        var work = ApplicationManager.getApplication().executeOnPooledThread(resolver::resolveNow);
        waitFor(work::isDone);
        work.get();
        // The VFS must see the project's own tree too, for the in-memory local check.
        com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true,
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base));
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(10);
        }
        assertTrue("timed out", condition.getAsBoolean());
    }
}

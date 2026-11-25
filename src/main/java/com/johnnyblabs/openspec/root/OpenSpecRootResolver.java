package com.johnnyblabs.openspec.root;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.messages.Topic;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.validation.ValidationRelease;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * The project's resolved OpenSpec root, cached. {@link #snapshot()} only reads a field, so actions, inspections and
 * file-event handlers may call it on any thread. Resolution ({@link RootResolver}) runs on a pooled thread, debounced,
 * and publishes {@link Listener#rootChanged} when the answer changes.
 *
 * <p>It re-resolves when {@code <project>/openspec} or its config changes, when the store registry or the global
 * config changes, when the resolved root is deleted, when CLI detection completes (the followed release decides which
 * sources apply), and on {@link #invalidate()}. A root outside the project is watched for file changes.
 */
@Service(Service.Level.PROJECT)
public final class OpenSpecRootResolver implements Disposable {

    private static final Logger LOG = Logger.getInstance(OpenSpecRootResolver.class);
    private static final int DEBOUNCE_MS = 300;

    public static final Topic<Listener> TOPIC = Topic.create("OpenSpec root resolved", Listener.class);

    public interface Listener {
        void rootChanged(@NotNull Snapshot snapshot);
    }

    public enum Kind {PENDING, RESOLVED, NONE}

    /**
     * One resolution. {@code openspecDir} is the root's {@code openspec/} directory when it resolved and the file
     * system has it.
     */
    public record Snapshot(Kind kind, RootResolution resolution, @Nullable VirtualFile openspecDir) {
        static final Snapshot PENDING = new Snapshot(Kind.PENDING, RootResolution.none(), null);

        public boolean pending() {
            return kind == Kind.PENDING;
        }

        public @Nullable RootResolution.Problem problem() {
            return resolution.problem();
        }
    }

    private final Project project;
    private final Alarm alarm;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    /** A re-resolve for a deleted root is queued; repeat reads must not keep pushing it back. */
    private final AtomicBoolean staleQueued = new AtomicBoolean();
    /** Set under the lock in {@link #dispose}; a resolve still running then registers no watch and publishes nothing. */
    private boolean disposed;
    private volatile Function<String, RootResolver.Inputs> inputs = RootResolver.Inputs::host;
    private volatile Snapshot snapshot = Snapshot.PENDING;
    private volatile Set<LocalFileSystem.WatchRequest> watches = Set.of();
    /** Paths whose change re-resolves; read by the VFS listener. */
    private volatile Watched watched = Watched.NONE;

    public OpenSpecRootResolver(Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                Watched w = watched;
                for (VFileEvent e : events) {
                    if (w.matches(e.getPath())) {
                        invalidate();
                        return;
                    }
                }
            }
        });
        project.getMessageBus().connect(this).subscribe(CliDetectionService.TOPIC,
                (CliDetectionService.Listener) (available, version) -> invalidate());
    }

    public static OpenSpecRootResolver getInstance(Project project) {
        return project.getService(OpenSpecRootResolver.class);
    }

    /**
     * The cached root. Never reads the disk, refreshes or runs the CLI. The first read schedules resolution. A cached
     * root that was deleted is never handed out: the read gets the same answer without its directory, and one
     * re-resolve is queued.
     */
    public Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s.pending()) {
            if (scheduled.compareAndSet(false, true)) invalidate();
        } else if (s.openspecDir() != null && !s.openspecDir().isValid()) {
            if (staleQueued.compareAndSet(false, true)) invalidate();
            return new Snapshot(s.kind(), s.resolution(), null);
        }
        return s;
    }

    /** The cached snapshot as it stands: no scheduling, for callers that must not cause work (editor hooks). */
    public Snapshot current() {
        return snapshot;
    }

    /** Re-resolve soon, off the UI thread. */
    public void invalidate() {
        scheduled.set(true);
        if (alarm.isDisposed() || project.isDisposed()) return;
        alarm.cancelAllRequests();
        alarm.addRequest(this::resolveNow, DEBOUNCE_MS);
    }

    /**
     * Resolve now, on the calling thread, and publish when the answer changed. Reads the disk and may refresh the
     * file system, so callers must be off the UI thread and outside any read action (tests excepted). Serialized: the
     * startup activity, the debounce and a refresh can't interleave, so the last resolution started is the one kept.
     */
    public synchronized Snapshot resolveNow() {
        scheduled.set(true);
        staleQueued.set(false);
        String base = project.getBasePath();
        Path start = path(base);
        RootResolution resolution = start == null ? RootResolution.none()
                : RootResolver.resolve(start, inputs.apply(ValidationRelease.followed(project)));
        if (resolution.warning() != null) LOG.info(resolution.warning());
        if (resolution.problem() != null) LOG.info("OpenSpec root: " + resolution.problem().message());

        LocalFileSystem lfs = LocalFileSystem.getInstance();
        VirtualFile dir = null;
        Path openspecDir = resolution.openspecDir();
        if (openspecDir != null) {
            dir = lfs.refreshAndFindFileByNioFile(openspecDir);
            // A newly resolved tree is refreshed and loaded once. The VFS reports a change only under folders whose
            // contents it has loaded; a project's files load through indexing, but a store outside the project isn't
            // indexed, so without this a change made in the store outside the IDE never arrives.
            VirtualFile before = snapshot.openspecDir();
            if (dir != null && !dir.equals(before)) {
                VfsUtil.markDirtyAndRefresh(false, true, true, dir);
                VfsUtilCore.iterateChildrenRecursively(dir, null, file -> true);
            }
        }
        RootResolver.Inputs in = inputs.apply(null);
        Path registry = in.dataPaths().storeRegistryFile();
        Path globalConfig = in.globalConfigFile();
        // Load both into the file system so a later edit raises an event.
        lfs.refreshAndFindFileByNioFile(registry);
        lfs.refreshAndFindFileByNioFile(globalConfig);
        updateWatches(lfs, base, openspecDir, registry, globalConfig);

        Snapshot next = new Snapshot(resolution.resolved() ? Kind.RESOLVED : Kind.NONE, resolution, dir);
        Snapshot old = snapshot;
        snapshot = next;
        boolean live;
        synchronized (this) {
            live = !disposed;
        }
        if (!same(old, next)) LOG.info("OpenSpec root: " + next.kind() + " " + resolution.root() + " (" + resolution.source() + ")");
        if (!same(old, next) && live && !project.isDisposed()) {
            project.getMessageBus().syncPublisher(TOPIC).rootChanged(next);
            notifyIgnoredPointer(old, next);
            // Open store files gain or lose their banner with the root.
            com.intellij.ui.EditorNotifications.getInstance(project).updateAllNotifications();
        }
        return next;
    }

    /** Once, on the way into the ignored-pointer state; the tree's hint carries it after that. */
    private void notifyIgnoredPointer(Snapshot old, Snapshot next) {
        String text = RootProvenance.ignoredPointerText(next.resolution());
        if (text == null || old.resolution().ignoredStoreId() != null) return;
        com.johnnyblabs.openspec.util.OpenSpecNotifier.notify(project,
                com.johnnyblabs.openspec.util.OpenSpecNotifier.GROUP_SYSTEM, "OpenSpec store ignored", text,
                com.intellij.notification.NotificationType.INFORMATION);
    }

    private void updateWatches(LocalFileSystem lfs, @Nullable String base, @Nullable Path openspecDir,
                               Path registry, Path globalConfig) {
        String baseOpenspec = base == null ? null : sys(Path.of(base).resolve(RootResolver.OPENSPEC_DIR));
        String rootOpenspec = openspecDir == null ? null : sys(openspecDir);
        watched = new Watched(baseOpenspec, rootOpenspec, sys(registry), sys(globalConfig));

        List<String> recursive = new ArrayList<>();
        // The project is watched already; only a root outside it needs its own watch.
        // Compared canonically, as the resolved root is: a symlinked checkout is still the project.
        if (rootOpenspec != null && (base == null || !openspecDir.startsWith(RootResolver.canonical(Path.of(base))))) {
            recursive.add(rootOpenspec);
        }
        List<String> flat = List.of(sys(registry.getParent()), sys(globalConfig.getParent()));
        synchronized (this) {
            if (disposed) return;
            Set<LocalFileSystem.WatchRequest> before = watches;
            watches = lfs.replaceWatchedRoots(watches, recursive, flat);
            if (!watches.equals(before)) {
                LOG.info("OpenSpec root watches: recursive " + recursive + ", flat " + flat + " -> "
                        + watches.stream().map(LocalFileSystem.WatchRequest::getRootPath).toList());
            }
        }
    }

    @Override
    public void dispose() {
        synchronized (this) {
            disposed = true;
            LocalFileSystem.getInstance().removeWatchedRoots(watches);
            watches = Set.of();
        }
    }

    /** Point the machine-wide inputs (registry, global config) somewhere else; the argument is the followed release. */
    @TestOnly
    public void setInputsForTests(Function<String, RootResolver.Inputs> inputs) {
        this.inputs = inputs;
    }

    /** Back to the state before the first resolution, without scheduling one. */
    @TestOnly
    public void resetToPendingForTests() {
        alarm.cancelAllRequests();
        scheduled.set(true);
        snapshot = Snapshot.PENDING;
    }

    @TestOnly
    public Set<LocalFileSystem.WatchRequest> watchRequests() {
        return watches;
    }

    private static boolean same(Snapshot a, Snapshot b) {
        return a.kind() == b.kind() && Objects.equals(a.resolution(), b.resolution())
                && Objects.equals(a.openspecDir(), b.openspecDir());
    }

    private static @Nullable Path path(@Nullable String s) {
        if (s == null) return null;
        try {
            return Path.of(s);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** A path as the file system reports it in events: forward slashes. */
    private static String sys(Path p) {
        return p.toString().replace('\\', '/');
    }

    /**
     * The paths whose change re-resolves: {@code <project>/openspec} itself and its direct children (config, or a
     * {@code specs/}/{@code changes/} directory appearing), the resolved {@code openspec/} directory, the registry and
     * the global config.
     */
    record Watched(@Nullable String baseOpenspec, @Nullable String rootOpenspec, @Nullable String registry,
                   @Nullable String globalConfig) {
        static final Watched NONE = new Watched(null, null, null, null);

        boolean matches(String path) {
            if (path.equals(registry) || path.equals(globalConfig)) return true;
            if (baseOpenspec != null && (path.equals(baseOpenspec) || parentIs(path, baseOpenspec))) return true;
            return rootOpenspec != null && path.equals(rootOpenspec);
        }

        private static boolean parentIs(String path, String dir) {
            return path.startsWith(dir + "/") && path.indexOf('/', dir.length() + 1) < 0;
        }
    }
}

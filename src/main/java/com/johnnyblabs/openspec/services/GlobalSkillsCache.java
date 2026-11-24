package com.johnnyblabs.openspec.services;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The home directory's OpenSpec skills ({@link HomeSkills}), read on a pooled thread and refreshed when something may
 * have changed them: a CLI run the plugin started, or the project's window regaining focus (the user may have run the
 * CLI in a terminal). {@link #snapshot} never touches the disk on the EDT. A refresh keeps the previous snapshot until
 * the new read finishes, so a lookup right after returning to the IDE still sees the skills that were there; only the
 * very first lookup on the EDT, before any read, answers "no home skills" and schedules one.
 * <p>
 * Each {@link #invalidate} starts a new generation, and a read stores its result only if no invalidate happened while
 * it ran, so a slow read can't put back what a CLI run just changed.
 */
public final class GlobalSkillsCache implements Disposable {

    private final Project project;
    private volatile @Nullable HomeSkills cached;
    private volatile boolean stale = true;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile Supplier<@Nullable Path> home = AiToolDetectionService::home;

    public GlobalSkillsCache(Project project) {
        this.project = project;
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
                    @Override
                    public void applicationActivated(@NotNull IdeFrame ideFrame) {
                        if (ideFrame.getProject() == project) invalidate();
                    }
                });
    }

    public static GlobalSkillsCache getInstance(Project project) {
        return project.getService(GlobalSkillsCache.class);
    }

    /**
     * The home skills. Off the EDT a stale or missing snapshot is read now; on the EDT the last snapshot (or none) is
     * returned at once and a read is scheduled.
     */
    public HomeSkills snapshot() {
        HomeSkills now = cached;
        if (now != null && !stale) return now;
        if (ApplicationManager.getApplication().isDispatchThread()) {
            scheduleLoad();
            return now != null ? now : HomeSkills.NONE;
        }
        return load();
    }

    /** Marks the snapshot out of date and starts a background read; the old one serves until that read finishes. */
    public void invalidate() {
        generation.incrementAndGet();
        stale = true;
        scheduleLoad();
    }

    /** Whether an up-to-date snapshot is held, for tests. */
    boolean isFresh() {
        return cached != null && !stale;
    }

    private HomeSkills load() {
        long started = generation.get();
        HomeSkills read = HomeSkills.scan(home.get());
        // Store only if nothing was invalidated meanwhile; otherwise a newer read is on its way.
        synchronized (this) {
            if (generation.get() == started) {
                cached = read;
                stale = false;
            }
        }
        return read;
    }

    private void scheduleLoad() {
        if (!loading.compareAndSet(false, true)) return;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                // Read until a read finishes with no invalidate in between.
                while (!project.isDisposed() && !isFresh()) load();
            } finally {
                loading.set(false);
                // An invalidate that lost the race with the flag above still gets its read.
                if (!project.isDisposed() && !isFresh()) scheduleLoad();
            }
        });
    }

    @TestOnly
    void setHomeForTest(@Nullable Supplier<@Nullable Path> supplier) {
        home = supplier == null ? AiToolDetectionService::home : supplier;
        generation.incrementAndGet();
        cached = null;
        stale = true;
    }

    @Override
    public void dispose() {
    }
}

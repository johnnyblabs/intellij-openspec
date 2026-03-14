package com.johnnyblabs.openspec.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * Watches for an artifact file to be created or modified,
 * then fires a callback. Includes periodic VFS refresh as
 * fallback for external changes (e.g., Claude Code CLI).
 */
public class ArtifactFileWatcher implements Disposable {

    private static final int REFRESH_INTERVAL_MS = 5_000;
    private static final int TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

    private final String changeDir;
    private final String outputPath;
    private final Runnable onFileDetected;
    private final Runnable onTimeout;

    private volatile boolean disposed = false;
    private Timer refreshTimer;
    private Timer timeoutTimer;

    public ArtifactFileWatcher(String changeDir, String outputPath,
                               Runnable onFileDetected, Runnable onTimeout) {
        this.changeDir = changeDir;
        this.outputPath = outputPath;
        this.onFileDetected = onFileDetected;
        this.onTimeout = onTimeout;
    }

    public void start() {
        // Listen for VFS events
        ApplicationManager.getApplication().getMessageBus()
                .connect(this)
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends VFileEvent> events) {
                        if (disposed) return;
                        for (VFileEvent event : events) {
                            if (event instanceof VFileCreateEvent || event instanceof VFileContentChangeEvent) {
                                String path = event.getPath();
                                if (path != null && path.startsWith(changeDir)) {
                                    fireDetected();
                                    return;
                                }
                            }
                        }
                    }
                });

        // Periodic VFS refresh as fallback for external changes
        refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> {
            if (disposed) return;
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                String fullPath = changeDir + "/" + outputPath;
                VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath);
                if (file != null && file.exists() && file.getLength() > 0) {
                    fireDetected();
                }
            });
        });
        refreshTimer.setRepeats(true);
        refreshTimer.start();

        // Timeout
        timeoutTimer = new Timer(TIMEOUT_MS, e -> {
            if (disposed) return;
            dispose();
            ApplicationManager.getApplication().invokeLater(onTimeout);
        });
        timeoutTimer.setRepeats(false);
        timeoutTimer.start();
    }

    private void fireDetected() {
        if (disposed) return;
        dispose();
        ApplicationManager.getApplication().invokeLater(onFileDetected);
    }

    @Override
    public void dispose() {
        disposed = true;
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}

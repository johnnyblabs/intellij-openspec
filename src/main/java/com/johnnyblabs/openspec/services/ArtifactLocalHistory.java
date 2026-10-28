package com.johnnyblabs.openspec.services;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Marks a Direct API write in the IDE's Local History, which is where "what changed, and how do I get it back" lives:
 * upstream OpenSpec has no artifact history, and the plugin stores none. Only public, non-deprecated platform API is
 * used ({@code putSystemLabel}, {@code isUnderControl}); {@code putUserLabel} is internal and {@code isEnabled} is
 * missing on 2024.2.
 */
public final class ArtifactLocalHistory {

    /** Puts a label. A disabled Local History quietly puts none, and the write goes ahead. */
    public interface Labeler {
        void putLabel(@NotNull Project project, @NotNull String name);
    }

    private static final Labeler PLATFORM = (project, name) -> LocalHistory.getInstance().putSystemLabel(project, name);

    private static final Logger LOG = Logger.getInstance(ArtifactLocalHistory.class);

    private static volatile Labeler labeler = PLATFORM;

    private ArtifactLocalHistory() {
    }

    /** The label put before generating an artifact. */
    public static String generatingLabel(String artifactId) {
        return "OpenSpec: before generating " + artifactId;
    }

    /** The label put before an accepted Regenerate writes. */
    public static String regeneratingLabel(String artifactId) {
        return "OpenSpec: before regenerating " + artifactId;
    }

    /**
     * Saves any unsaved edits to the files about to be written, then puts the label. Local History versions what is on
     * disk, so without the save the label's earlier content would miss text the user had. Any thread; saving runs on
     * the EDT. A label that can't be put is logged, and the write goes ahead.
     */
    public static void beforeWrite(@NotNull Project project, @NotNull String labelName, @NotNull List<Path> paths) {
        Application app = ApplicationManager.getApplication();
        if (app == null) return;
        Runnable save = () -> saveUnsaved(paths);
        if (app.isDispatchThread()) {
            save.run();
        } else if (app.runReadAction((Computable<Boolean>) () -> anyUnsaved(paths))) {
            // Hop only when there is something to save: from a background thread the hop waits for any open modal
            // dialog to close, and writes of new files (Generate All, Fast-Forward, Continue) never need it.
            app.invokeAndWait(save);
        }
        try {
            labeler.putLabel(project, labelName);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            // The label is a convenience beside the write, never a condition for it.
            LOG.warn("Could not put Local History label '" + labelName + "'", e);
        }
    }

    private static boolean anyUnsaved(List<Path> paths) {
        FileDocumentManager documents = FileDocumentManager.getInstance();
        for (Path path : paths) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(path);
            Document document = file == null ? null : documents.getCachedDocument(file);
            if (document != null && documents.isDocumentUnsaved(document)) return true;
        }
        return false;
    }

    private static void saveUnsaved(List<Path> paths) {
        FileDocumentManager documents = FileDocumentManager.getInstance();
        for (Path path : paths) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(path);
            Document document = file == null ? null : documents.getCachedDocument(file);
            if (document != null && documents.isDocumentUnsaved(document)) {
                documents.saveDocument(document);
            }
        }
    }

    /** Whether the IDE's Local History tracks {@code file}, so its history view has something to show. */
    public static boolean isTracked(@Nullable VirtualFile file) {
        return file != null && file.isValid() && LocalHistory.getInstance().isUnderControl(file);
    }

    /** Replaces the labeler until {@code parent} is disposed. Tests only: light fixtures aren't tracked by Local History. */
    public static void setLabelerForTest(@NotNull Labeler testLabeler, @NotNull Disposable parent) {
        labeler = testLabeler;
        Disposer.register(parent, () -> labeler = PLATFORM);
    }
}

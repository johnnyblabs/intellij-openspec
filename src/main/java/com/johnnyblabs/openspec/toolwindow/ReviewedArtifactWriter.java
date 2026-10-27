package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService.PlannedWrite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes a reviewed regeneration. The review shows each file's live text (an open editor's unsaved text, otherwise
 * the file on disk), and Accept writes only if none of them changed since, so an edit the user didn't see is never
 * overwritten. The write is one undoable command; a file open in an editor is written through its document.
 */
final class ReviewedArtifactWriter {

    /** Thrown when a reviewed file changed between the review and Accept; nothing was written. */
    static final class ChangedSinceReviewException extends IOException {
        ChangedSinceReviewException(Path path) {
            super(path.getFileName() + " changed since the review");
        }
    }

    private ReviewedArtifactWriter() {
    }

    /** The writes with each file's live text as its current text. Call off the EDT. */
    static List<PlannedWrite> withLiveText(List<PlannedWrite> writes) throws IOException {
        List<PlannedWrite> live = new ArrayList<>();
        for (PlannedWrite write : writes) {
            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(write.path());
            live.add(file == null ? write.withCurrentText(null) : write.withCurrentText(
                    ApplicationManager.getApplication().runReadAction((ThrowableComputable<String, IOException>)
                            () -> liveText(file))));
        }
        return live;
    }

    /**
     * Writes the reviewed files as one undoable command named {@code commandName}, or nothing if any of them changed
     * since the review. Call on the EDT.
     */
    static List<Path> write(@NotNull Project project, @NotNull String commandName, @NotNull List<PlannedWrite> reviewed)
            throws IOException {
        List<VirtualFile> existing = new ArrayList<>();
        for (PlannedWrite write : reviewed) {
            // Refresh-and-find, so a file created outside the IDE since the review is seen rather than overwritten.
            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(write.path());
            if (file != null) existing.add(file);
        }
        // Pick up edits made outside the IDE since the review, so the check below sees them.
        VfsUtil.markDirtyAndRefresh(false, false, false, existing.toArray(VirtualFile.EMPTY_ARRAY));

        IOException[] failure = new IOException[1];
        List<Path> written = new ArrayList<>();
        WriteCommandAction.runWriteCommandAction(project, commandName, null, () -> {
            try {
                for (PlannedWrite write : reviewed) {
                    VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(write.path());
                    String now = file == null ? null : liveText(file);
                    if (!sameText(write.currentText(), now)) throw new ChangedSinceReviewException(write.path());
                }
                // Resolve every target before changing any, so a failure that can be foreseen writes no file.
                List<Target> targets = new ArrayList<>();
                for (PlannedWrite write : reviewed) targets.add(target(write));
                for (Target target : targets) {
                    target.apply();
                    written.add(target.write().path());
                }
            } catch (IOException e) {
                failure[0] = e;
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (RuntimeException e) {
                failure[0] = new IOException(e.getMessage() != null ? e.getMessage() : e.toString(), e);
            }
        });
        if (failure[0] != null) throw failure[0];
        return written;
    }

    /** A reviewed write whose directory exists and whose existing file is writable; nothing has changed yet. */
    private record Target(PlannedWrite write, VirtualFile dir, @Nullable VirtualFile file) {
        void apply() throws IOException {
            byte[] bytes = write.newText().getBytes(StandardCharsets.UTF_8);
            if (file == null) {
                // A new file gets the text as generated; a new document would save it with the platform's separator.
                dir.createChildData(ReviewedArtifactWriter.class, write.path().getFileName().toString())
                        .setBinaryContent(bytes);
                return;
            }
            // Through the document, so the command can be undone and the file keeps its line separator, then saved so
            // the CLI reads the new text from disk.
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) {
                file.setBinaryContent(bytes);
                return;
            }
            document.setText(StringUtil.convertLineSeparators(write.newText()));
            FileDocumentManager.getInstance().saveDocument(document);
        }
    }

    private static Target target(PlannedWrite write) throws IOException {
        Path path = write.path();
        VirtualFile dir = VfsUtil.createDirectoryIfMissing(path.getParent().toString());
        if (dir == null || !dir.isDirectory()) throw new IOException("Failed to create directory: " + path.getParent());
        VirtualFile file = dir.findChild(path.getFileName().toString());
        if (file != null && !file.isWritable()) throw new IOException(path.getFileName() + " is read-only");
        return new Target(write, dir, file);
    }

    /** An open editor's text, otherwise the file on disk. A file that can't be read fails rather than reading as absent. */
    private static String liveText(VirtualFile file) throws IOException {
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document != null) return document.getText();
        return VfsUtilCore.loadText(file);
    }

    /** Equal ignoring line-separator style, so a CRLF file read from disk matches its editor text. */
    static boolean sameText(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) return Objects.equals(a, b);
        return StringUtil.convertLineSeparators(a).equals(StringUtil.convertLineSeparators(b));
    }
}

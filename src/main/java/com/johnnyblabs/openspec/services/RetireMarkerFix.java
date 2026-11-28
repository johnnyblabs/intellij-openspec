package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the fix OpenSpec prints when an archive is refused only for want of the retirement marker: add
 * {@code retire_capabilities: true} to the change's {@code .openspec.yaml}. No OpenSpec command writes the marker (only
 * the author can authorize deleting a spec), so this is that printed fix, applied after the user confirms. It never
 * deletes a spec: the Sync preview, shown next, does that on Apply.
 */
public final class RetireMarkerFix {

    /**
     * Every capability the marker would make deletable in one change; {@code replacesFalse} when the metadata says
     * {@code retire_capabilities: false} now (OpenSpec still prints the hint then), so the confirmation says so.
     */
    public record Offer(String changeName, List<String> capabilities, boolean replacesFalse) {
        public Offer {
            capabilities = List.copyOf(capabilities);
        }

        public Offer(String changeName, List<String> capabilities) {
            this(changeName, capabilities, false);
        }
    }

    private static final Pattern HINT = Pattern.compile(
            "This change removes the last requirement '([^']+)' has\\..*add `retire_capabilities: true`", Pattern.DOTALL);
    private static final Pattern CANNOT_HONOR = Pattern.compile("The marker present now cannot be honored \\((.*)\\)\\.$",
            Pattern.DOTALL);
    private static final Pattern MARKER_LINE = Pattern.compile("^(['\"]?)retire_capabilities\\1\\s*:.*$");
    private static final Pattern FALSE_LINE =
            Pattern.compile("^(['\"]?)retire_capabilities\\1\\s*:\\s*(?:false|False|FALSE)\\s*(?:#.*)?$", Pattern.MULTILINE);
    /** A trailing comment on a plain scalar value, kept when the value is replaced. */
    private static final Pattern TRAILING_COMMENT = Pattern.compile(":\\s*[^'\"#\\s][^'\"#]*?(\\s+#.*)$");
    private static final String MARKER = "retire_capabilities: true";

    private RetireMarkerFix() {
    }

    /**
     * The text gate: the refusal's fix names the marker, and any "cannot be honored" reason is about the marker's own
     * value. OpenSpec prints that hint only when the marker is the one thing missing (1.8.0 and later).
     */
    public static boolean asksForMarker(String refusal) {
        if (!HINT.matcher(refusal).find()) return false;
        Matcher reason = CANNOT_HONOR.matcher(refusal);
        return !reason.find() || reason.group(1).startsWith("retire_capabilities:");
    }

    /**
     * The metadata with the marker set: a top-level {@code retire_capabilities} line (and lines indented under it) is
     * replaced, otherwise the line is appended. Every other line is kept as it is.
     */
    public static String withMarker(String yaml) {
        String[] lines = yaml.split("\n", -1);
        List<String> out = new ArrayList<>();
        boolean replaced = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String bare = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (!replaced && MARKER_LINE.matcher(bare).matches()) {
                Matcher comment = TRAILING_COMMENT.matcher(bare);
                out.add(MARKER + (comment.find() ? comment.group(1) : "") + (line.endsWith("\r") ? "\r" : ""));
                replaced = true;
                // Drop the value's continuation: lines indented under the key (blank lines stay).
                while (i + 1 < lines.length && !lines[i + 1].isBlank() && Character.isWhitespace(lines[i + 1].charAt(0))) i++;
                continue;
            }
            out.add(line);
        }
        if (replaced) return String.join("\n", out);
        String crlf = yaml.contains("\r\n") ? "\r\n" : "\n";
        boolean endsWithBreak = yaml.isEmpty() || yaml.endsWith("\n");
        return yaml + (endsWithBreak ? "" : crlf) + MARKER + crlf;
    }

    /**
     * The offer for one change: the capabilities the marker would make deletable, or {@code null} when no refusal asks
     * for the marker or the metadata, with it added, would still not be honored. The second check matters because the
     * plugin's own refusal leaves out a reason no capture pins (an invalid schema override, say).
     *
     * @param yaml        the change's metadata text ({@code null} when there is none)
     * @param retirable   the capabilities the plugin's merge retires with the marker declared
     */
    public static @Nullable Offer decide(String changeName, List<String> refusals, @Nullable String yaml,
                                         RetirementMarkers.SchemaCheck schemas, Supplier<List<String>> retirable) {
        if (yaml == null || refusals.stream().noneMatch(RetireMarkerFix::asksForMarker)) return null;
        if (!RetirementMarkers.parse(withMarker(yaml), schemas).declared()) return null;
        List<String> ids = retirable.get();
        return ids.isEmpty() ? null : new Offer(changeName, ids, replacesFalse(yaml));
    }

    /** Whether the metadata declines retirement in so many words: {@code retire_capabilities: false}. */
    static boolean replacesFalse(String yaml) {
        return FALSE_LINE.matcher(yaml.replace("\r\n", "\n")).find();
    }

    /** {@link #decide} for a change in the project. Reads the disk and may run the CLI: off the UI thread. */
    public static @Nullable Offer offer(Project project, String changeName, List<String> refusals) {
        if (refusals.stream().noneMatch(RetireMarkerFix::asksForMarker)) return null;
        return editOffer(project, changeName);
    }

    /** Every refusal in a sync's results. */
    public static List<String> refusals(List<SpecSyncResult> results) {
        return results.stream().flatMap(r -> r.refusals().stream()).toList();
    }

    /** The edit check alone, on the metadata as it is now on disk. Off the UI thread. */
    private static @Nullable Offer editOffer(Project project, String changeName) {
        SpecSyncService sync = project.getService(SpecSyncService.class);
        String yaml = read(metadataPath(sync, changeName));
        if (yaml == null || !RetirementMarkers.parse(withMarker(yaml), sync.schemaCheck()).declared()) return null;
        List<String> ids = sync.computeSync(changeName, new SpecMerge.RetirementMarker(true, null)).stream()
                .filter(SpecSyncResult::retired).map(SpecSyncResult::capabilityName).toList();
        return ids.isEmpty() ? null : new Offer(changeName, ids, replacesFalse(yaml));
    }

    /**
     * The confirmation's text. OpenSpec leaves the deletion to the author ("recoverable only from git"), so it says
     * everything the marker does: which specs it makes deletable, that it covers the whole change and stays, that it is
     * written before the preview, and that it overrides an explicit {@code false}.
     */
    public static String confirmation(Offer offer) {
        List<String> ids = offer.capabilities();
        String specs = String.join(", ", ids.stream().map(id -> "specs/" + id + "/spec.md").toList());
        String head = ids.size() == 1 ? "Retire capability '" + ids.getFirst() + "'?"
                : "Retire capabilities " + String.join(", ", ids.stream().map(id -> "'" + id + "'").toList()) + "?";
        return head + "\n\nSetting " + MARKER + " in " + offer.changeName() + "/.openspec.yaml lets Sync Specs, "
                + "archive and OpenSpec's agent skills delete " + specs + "."
                + (offer.replacesFalse() ? " It replaces retire_capabilities: false." : "")
                + "\n\nThe marker covers the whole change and stays in its metadata: a later edit that removes another "
                + "capability's last requirement retires that one too. It is written now; Apply in the Sync preview "
                + "that follows does the deleting, and cancelling there leaves the marker in place. A deleted spec can "
                + "be restored from git only if it was committed.";
    }

    /**
     * Confirms, writes the marker as one undoable edit, then runs {@code preview}. The edit is checked again on the
     * file's current text, in the background, before the user is asked; nothing is written if it changed meanwhile or
     * would not be honored. Call on the UI thread.
     */
    public static void apply(Project project, Offer offer, Runnable preview) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Checking retirement marker...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                SpecSyncService sync = project.getService(SpecSyncService.class);
                Path path = metadataPath(sync, offer.changeName());
                String before = read(path);
                Offer current = editOffer(project, offer.changeName());
                ApplicationManager.getApplication().invokeLater(
                        () -> confirmAndWrite(project, offer.changeName(), path, before, current, preview), project.getDisposed());
            }
        });
    }

    /**
     * The UI-thread half of {@link #apply}: {@code current} is the offer re-checked on {@code before}, the file's text
     * then. Returns whether the marker was written.
     */
    static boolean confirmAndWrite(Project project, String changeName, Path path, @Nullable String before,
                                   @Nullable Offer current, Runnable preview) {
        if (current == null || before == null) {
            OpenSpecNotifier.warn(project, "Retire Capability", "The marker can't be added automatically: "
                    + changeName + "/.openspec.yaml would still not be honored. Edit it by hand.");
            return false;
        }
        if (Messages.showYesNoDialog(project, confirmation(current), "Retire Capability", "Add Marker",
                "Cancel", Messages.getWarningIcon()) != Messages.YES) {
            return false;
        }
        Written written = write(project, path, before);
        if (written != Written.YES) {
            OpenSpecNotifier.warn(project, "Retire Capability", written == Written.UNSAVED_EDITS
                    ? changeName + "/.openspec.yaml has unsaved edits; save or discard them, then try again. Nothing was written."
                    : changeName + "/.openspec.yaml changed meanwhile; nothing was written. Try again.");
            return false;
        }
        preview.run();
        return true;
    }

    private enum Written { YES, CHANGED, UNSAVED_EDITS }

    /**
     * Writes the marker through the file's document (undoable, editors stay in sync), then saves: the CLI reads disk.
     * Only over the text that was checked: not over unsaved edits, nor a file changed since.
     */
    private static Written write(Project project, Path path, String checked) {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        if (file == null) return Written.CHANGED;
        FileDocumentManager documents = FileDocumentManager.getInstance();
        if (documents.isFileModified(file)) return Written.UNSAVED_EDITS;
        // A cached file isn't re-read by the lookup above; refresh it so the document matches the disk just checked.
        file.refresh(false, false);
        Document document = documents.getDocument(file);
        if (document == null || !document.getText().equals(normalize(checked))) return Written.CHANGED;
        String updated = withMarker(document.getText());
        WriteCommandAction.runWriteCommandAction(project, "Add Retirement Marker", null,
                () -> document.setText(updated));
        documents.saveDocument(document);
        return Written.YES;
    }

    /** Disk text as a document holds it: no byte-order mark, LF line breaks. */
    static String normalize(String text) {
        return (text.startsWith("\uFEFF") ? text.substring(1) : text).replace("\r\n", "\n");
    }

    private static Path metadataPath(SpecSyncService sync, String changeName) {
        return sync.changeDir(changeName).resolve(".openspec.yaml");
    }

    private static @Nullable String read(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }
}

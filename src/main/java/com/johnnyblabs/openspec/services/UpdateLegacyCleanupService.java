package com.johnnyblabs.openspec.services;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.dialogs.LegacyCleanupDialog;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import com.johnnyblabs.openspec.util.OpenSpecTerminalLauncher;
import com.johnnyblabs.openspec.util.UpdateOutputParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the {@code openspec update} "legacy files pending" outcome gracefully.
 *
 * <p>The CLI's skills migration flags leftover files, certifies them "No user content to
 * preserve", and suggests {@code --force} or an interactive run — both unreachable from the
 * plugin's non-interactive console. This service turns that dead end into a consented flow:
 * review the CLI-listed files, delete exactly the checked ones through undoable VFS
 * operations, verify with a re-run, and terminate truthfully when the CLI regenerates the
 * files (observed on 1.4.1/1.5.0 for junie — see the change's design doc).</p>
 *
 * <p>Invariant: this service NEVER invokes {@code openspec update --force}.</p>
 */
@Service(Service.Level.PROJECT)
public final class UpdateLegacyCleanupService {

    private static final Logger LOG = Logger.getInstance(UpdateLegacyCleanupService.class);
    static final String DISMISSED_SET_KEY = "openspec.update.cleanup.dismissedSet";
    static final String REGENERATING_SET_KEY = "openspec.update.cleanup.regeneratingSet";

    private final Project project;

    public UpdateLegacyCleanupService(Project project) {
        this.project = project;
    }

    /**
     * Entry point from the Update action's result path (EDT). No-op when the output has
     * no migration block, or when the reported set is suppressed (dismissed unchanged, or
     * recorded as regenerating on this CLI).
     */
    public void handleUpdateResult(String stdout) {
        List<String> pending = UpdateOutputParser.parseLegacyCleanup(stdout);
        if (pending.isEmpty()) {
            return;
        }
        String key = setKey(pending);
        PropertiesComponent state = PropertiesComponent.getInstance(project);
        if (key.equals(state.getValue(DISMISSED_SET_KEY))) {
            return;
        }
        if (key.equals(state.getValue(REGENERATING_SET_KEY))) {
            return;
        }
        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "OpenSpec update",
                "OpenSpec has migrated to agent skills and found " + pending.size()
                        + " legacy file(s) it reports safe to remove. Review to resolve — the plugin never runs --force for you.",
                com.intellij.notification.NotificationType.INFORMATION,
                com.intellij.notification.NotificationAction.createSimpleExpiring(
                        "Review legacy cleanup…", () -> showCleanupDialog(pending)));
    }

    private void showCleanupDialog(List<String> pending) {
        LegacyCleanupDialog dialog = new LegacyCleanupDialog(project, pending);
        LegacyCleanupDialog.Outcome outcome = dialog.showAndGetOutcome();
        PropertiesComponent state = PropertiesComponent.getInstance(project);
        switch (outcome) {
            case REMOVE_SELECTED -> removeAndVerify(pending, dialog.getSelectedFiles());
            case RUN_IN_TERMINAL -> {
                if (!OpenSpecTerminalLauncher.launchCommand(project, "openspec update", "openspec update")) {
                    OpenSpecNotifier.info(project, OpenSpecTerminalLauncher.fallbackMessage("openspec update"));
                }
            }
            case NOT_NOW -> state.setValue(DISMISSED_SET_KEY, setKey(pending));
        }
    }

    private void removeAndVerify(List<String> reported, List<String> checked) {
        String basePath = project.getBasePath();
        if (basePath == null) return;
        List<VirtualFile> deletable = resolveDeletable(basePath, checked);
        if (deletable.isEmpty()) {
            OpenSpecNotifier.info(project, "No listed legacy files were found on disk — nothing to remove.");
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, "Remove OpenSpec Legacy Files", null, () -> {
            for (VirtualFile file : deletable) {
                try {
                    file.delete(this);
                } catch (IOException e) {
                    LOG.warn("Failed to delete legacy file " + file.getPath(), e);
                }
            }
        });

        // Verification re-run: the CLI is the judge of whether the cleanup stuck.
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<String> after;
            try {
                CliRunner.CliResult rerun = CliRunner.run(project, "update");
                after = UpdateOutputParser.parseLegacyCleanup(rerun.stdout());
            } catch (CliRunner.CliException e) {
                LOG.warn("Verification re-run failed", e);
                after = List.of();
            }
            List<String> finalAfter = after;
            ApplicationManager.getApplication().invokeLater(() -> reportVerification(reported, finalAfter));
        });
    }

    private void reportVerification(List<String> removedSet, List<String> reportedAfter) {
        PropertiesComponent state = PropertiesComponent.getInstance(project);
        if (isRegenerationLoop(removedSet, reportedAfter)) {
            // Truthful terminal state: this CLI version's tool integration both
            // generates and flags these files. Deletion is futile; suppress and
            // never offer it again for this set.
            state.setValue(REGENERATING_SET_KEY, setKey(reportedAfter));
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "OpenSpec update",
                    "The OpenSpec CLI regenerated the files it flags as legacy — this CLI version's tool "
                            + "integration both generates and flags them. Nothing on your side needs fixing; "
                            + "this notice is suppressed until the CLI reports something different (e.g. after a CLI upgrade).",
                    com.intellij.notification.NotificationType.INFORMATION);
        } else if (reportedAfter.isEmpty()) {
            OpenSpecNotifier.info(project, "Legacy cleanup complete — openspec update reports a clean state.");
        } else {
            // A different set remains (partial cleanup or new findings) — the normal
            // pending flow will pick it up on the next Update.
            OpenSpecNotifier.info(project, "Legacy cleanup applied; openspec update still reports "
                    + reportedAfter.size() + " pending file(s). Run Update again to review them.");
        }
    }

    // --- Pure helpers (unit-tested) ---

    /** Stable identity for a pending file set: sorted, newline-joined. */
    static String setKey(List<String> files) {
        return files.stream().sorted().reduce((a, b) -> a + "\n" + b).orElse("");
    }

    /**
     * True when the post-cleanup report contains the same files that were just removed —
     * the CLI regenerated them and deletion cannot resolve the state.
     */
    static boolean isRegenerationLoop(List<String> removedSet, List<String> reportedAfter) {
        if (reportedAfter.isEmpty()) return false;
        return reportedAfter.stream().anyMatch(removedSet::contains);
    }

    /**
     * Scope lock: only paths from the CLI's list that resolve to existing files inside
     * the project root are deletable. Anything else is silently excluded — degradation
     * shrinks the set, never grows it.
     */
    private List<VirtualFile> resolveDeletable(String basePath, List<String> paths) {
        List<VirtualFile> files = new ArrayList<>();
        Path root = Path.of(basePath).toAbsolutePath().normalize();
        for (String rel : paths) {
            Path resolved = root.resolve(rel).normalize();
            if (!resolved.startsWith(root)) {
                LOG.warn("Legacy path escapes the project root, skipping: " + rel);
                continue;
            }
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolved);
            if (vf != null && vf.exists() && !vf.isDirectory()) {
                files.add(vf);
            }
        }
        return files;
    }

    /** Visible for tests: the pure form of the scope lock, on paths. */
    static List<Path> filterInsideRoot(Path root, List<String> paths) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<Path> result = new ArrayList<>();
        for (String rel : paths) {
            Path resolved = normalizedRoot.resolve(rel).normalize();
            if (resolved.startsWith(normalizedRoot)) {
                result.add(resolved);
            }
        }
        return result;
    }
}

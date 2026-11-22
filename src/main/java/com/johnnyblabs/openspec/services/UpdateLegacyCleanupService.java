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
import com.johnnyblabs.openspec.util.CliText;
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
 * <p>The CLI's skills migration flags leftover files (certifying "No user content to preserve" before
 * 1.14; warning to back up custom content from 1.14.0) and suggests {@code --force} or an interactive run — both unreachable from the
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

    /** The registered Update action the completion notice offers; it opens the preview first on 1.15+. */
    static final String UPDATE_ACTION_ID = "OpenSpec.Update";
    static final String UPDATE_ACTION_TEXT = "Update OpenSpec…";

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
        offerCleanup(UpdateOutputParser.parseLegacyCleanupBlock(stdout));
    }

    /**
     * Offers the cleanup flow for {@code block}: files the CLI listed, from the text output (CLIs before 1.15) or from
     * the forced dry-run plan (1.15+, see {@link UpdatePreviewService}). Same suppression rules either way. EDT.
     */
    public void offerCleanup(UpdateOutputParser.LegacyCleanup block) {
        List<String> pending = block.files();
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
                "OpenSpec has migrated to agent skills and listed " + pending.size()
                        + " legacy file(s) for removal. Review them before anything is deleted — the plugin never runs"
                        + " --force for you.",
                com.intellij.notification.NotificationType.INFORMATION,
                com.intellij.notification.NotificationAction.createSimpleExpiring(
                        "Review legacy cleanup…", () -> showCleanupDialog(block)));
    }

    private void showCleanupDialog(UpdateOutputParser.LegacyCleanup block) {
        List<String> pending = block.files();
        LegacyCleanupDialog dialog = new LegacyCleanupDialog(project, block);
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

        verifyCleanup(reported);
    }

    /** Verifies the cleanup off the EDT and reports what the CLI established. Package-private for tests. */
    void verifyCleanup(List<String> reported) {
        // The CLI is the judge of whether the cleanup stuck.
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) return;
            Verification outcome = runVerification();
            // Expires with the project: two CLI runs give the user time to close it before the report lands.
            ApplicationManager.getApplication().invokeLater(() -> reportVerification(reported, outcome),
                    project.getDisposed());
        });
    }

    /**
     * On CLI 1.15.0+ a read-only dry run ({@link UpdatePreviewService#verifyCleanupReadOnly()}): from 1.17 a real
     * {@code openspec update} removes unedited legacy files unasked and still prints them as "to remove". Below 1.15,
     * which has no dry run, a re-run of {@code openspec update}. Call off the EDT; package-private for tests.
     */
    Verification runVerification() {
        UpdatePreviewService preview = UpdatePreviewService.getInstance(project);
        if (preview != null && preview.supportsPreview()) {
            UpdatePreviewService.CleanupCheck check = preview.verifyCleanupReadOnly();
            if (check.unverifiedReason() != null) {
                LOG.info("Read-only cleanup verification failed: " + check.unverifiedReason());
            }
            return fromReadOnly(check);
        }
        try {
            return classifyVerification(updateCli.run(project, "update"), null);
        } catch (CliRunner.CliException e) {
            LOG.warn("Verification re-run failed", e);
            return classifyVerification(null, e);
        }
    }

    /** Maps a read-only check onto a {@link Verification}. Pure; package-private for tests. */
    static Verification fromReadOnly(UpdatePreviewService.CleanupCheck check) {
        if (check.unverifiedReason() != null) {
            return new Verification(List.of(), check.unverifiedReason(), true, false, false);
        }
        return new Verification(check.pending(), null, true, check.noteRemains(), check.updateRemains());
    }

    /** Runs {@code openspec update}; a seam so tests can feed the verification re-run captured output. */
    interface UpdateCli {
        CliRunner.CliResult run(Project project, String... args) throws CliRunner.CliException;
    }

    private UpdateCli updateCli = CliRunner::run;

    @org.jetbrains.annotations.TestOnly
    void setUpdateCliForTest(UpdateCli cli) {
        this.updateCli = cli == null ? CliRunner::run : cli;
    }

    /**
     * What the verification re-run established: the files {@code openspec update} still reports, or, when the re-run
     * failed, why the cleanup could not be verified ({@code unverifiedReason} non-null, {@code after} empty).
     */
    record Verification(List<String> after, @org.jetbrains.annotations.Nullable String unverifiedReason,
                        boolean readOnly, boolean noteRemains, boolean updateRemains) {
        /**
         * The outcome of a re-run of {@code openspec update} (CLIs below 1.15). {@code readOnly}: a dry-run check,
         * where nothing ran that could regenerate files. {@code noteRemains}: the plan still notes legacy files.
         * {@code updateRemains}: a note-free plan that still has work, so Update is offered.
         */
        Verification(List<String> after, @org.jetbrains.annotations.Nullable String unverifiedReason) {
            this(after, unverifiedReason, false, false, false);
        }

        boolean verified() {
            return unverifiedReason == null;
        }
    }

    /**
     * Classifies the verification re-run. Only a run that exits 0 is read for pending files; a non-zero exit, a run
     * that could not start or timed out is "could not verify", never "clean". Pure; package-private for tests.
     */
    static Verification classifyVerification(@org.jetbrains.annotations.Nullable CliRunner.CliResult rerun,
                                             @org.jetbrains.annotations.Nullable CliRunner.CliException failure) {
        if (rerun == null) {
            return new Verification(List.of(), failure != null && failure.getMessage() != null
                    ? failure.getMessage() : "the OpenSpec CLI could not be run");
        }
        if (!rerun.isSuccess()) {
            String stderr = CliText.stripAnsi(rerun.stderr() == null ? "" : rerun.stderr()).strip();
            return new Verification(List.of(), stderr.isEmpty()
                    ? "openspec update exited with code " + rerun.exitCode() : stderr);
        }
        return new Verification(UpdateOutputParser.parseLegacyCleanup(rerun.stdout()), null);
    }

    /** Reports a verification on the EDT. Package-private for tests. */
    void reportVerification(List<String> removedSet, Verification verification) {
        if (!verification.verified()) {
            OpenSpecNotifier.warn(project, "OpenSpec update",
                    "Legacy files were removed, but the cleanup couldn't be verified: "
                            + verification.unverifiedReason() + ". Run Update again to check.");
            return;
        }
        List<String> reportedAfter = verification.after();
        PropertiesComponent state = PropertiesComponent.getInstance(project);
        if (!verification.readOnly() && isRegenerationLoop(removedSet, reportedAfter)) {
            // Truthful terminal state: this CLI version's tool integration both
            // generates and flags these files. Deletion is futile; suppress and
            // never offer it again for this set.
            state.setValue(REGENERATING_SET_KEY, setKey(reportedAfter));
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "OpenSpec update",
                    "The OpenSpec CLI regenerated the files it flags as legacy — this CLI version's tool "
                            + "integration both generates and flags them. Nothing on your side needs fixing; "
                            + "this notice is suppressed until the CLI reports something different (e.g. after a CLI upgrade).",
                    com.intellij.notification.NotificationType.INFORMATION);
        } else if (reportedAfter.isEmpty() && verification.noteRemains()) {
            // The CLI still notes legacy files but lists none to remove, e.g. OpenSpec markers left in an edited file.
            OpenSpecNotifier.info(project, "Legacy cleanup applied, but openspec update still reports legacy files"
                    + " it lists nothing to remove for. Run openspec update in a terminal to finish the cleanup.");
        } else if (reportedAfter.isEmpty() && verification.updateRemains()) {
            // Read-only check (1.15+): nothing pending, but the update itself hasn't run — finish it via the preview.
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "OpenSpec update",
                    "Legacy cleanup complete — openspec update reports no legacy files left. Run Update OpenSpec"
                            + " to refresh your skills and commands; it shows you what it will change first.",
                    com.intellij.notification.NotificationType.INFORMATION,
                    com.johnnyblabs.openspec.actions.ActionLauncher.notificationAction(project, UPDATE_ACTION_TEXT,
                            UPDATE_ACTION_ID));
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

package com.johnnyblabs.openspec.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.testFramework.LightVirtualFile;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.dialogs.ReviseChangeDialog;
import com.johnnyblabs.openspec.model.Change;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * "Revise Change...": hands a change to the user's AI tool's own {@code update} workflow, which revises its planning
 * artifacts, checks they still agree, and confirms each write. The plugin delivers only what the user would type to
 * run that workflow, the change name and an optional note; it never copies the workflow in. Never called "Update",
 * which is Update OpenSpec… (the CLI's generated-files refresh).
 *
 * <p>From the change's right-click menu or the workflow panel's header, the change comes from the data context; from
 * the OpenSpec menu without one, the only active change is used, or the user picks one.
 */
// DumbAware: it reads a few files, never the IDE's indexes, and the panel header launches it through ActionLauncher.
public class ReviseChangeAction extends OpenSpecBaseAction implements DumbAware {

    static final String TITLE = "Revise Change";
    static final String EDITOR_TAB_FILE_NAME = "openspec-revise-change.md";
    static final String NOT_INSTALLED_NOTICE = "Revise Change needs your AI tool's update workflow, which this project "
            + "doesn't have. Run Update OpenSpec… (or add update to your profile), then retry.";
    static final String DIRECT_API_NOTE = "Revise runs in your AI agent, which shows each revision and asks before "
            + "writing it, so it was copied for you to paste instead of sent.";

    @Override
    protected String getWorkflowId() {
        return WorkflowInvocation.UPDATE;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        String changeName = e.getData(OpenSpecDataKeys.CHANGE_NAME);
        if (changeName != null) {
            revise(project, changeName);
            return;
        }
        ChangeService changes = project.getService(ChangeService.class);
        List<String> active = changes == null ? List.of() : changes.getActiveChanges().stream().map(Change::getName).toList();
        if (active.isEmpty()) {
            OpenSpecNotifier.warn(project, TITLE, "No active changes to revise.");
        } else if (active.size() == 1) {
            revise(project, active.getFirst());
        } else {
            chooser.choose(active, e.getDataContext(), name -> revise(project, name));
        }
    }

    /** Asks which of several active changes to revise. */
    interface ChangeChooser {
        void choose(List<String> changes, DataContext where, Consumer<String> onChosen);
    }

    private static final ChangeChooser POPUP_CHOOSER = (changes, where, onChosen) ->
            JBPopupFactory.getInstance().createPopupChooserBuilder(changes)
                    .setTitle(TITLE)
                    .setItemChosenCallback(onChosen::accept)
                    .createPopup()
                    .showInBestPositionFor(where);

    /** The chooser; replaced in tests, which can't show a popup. */
    private static volatile ChangeChooser chooser = POPUP_CHOOSER;

    @TestOnly
    static void setChooserForTest(@Nullable ChangeChooser testChooser) {
        chooser = testChooser == null ? POPUP_CHOOSER : testChooser;
    }

    /** Makes the note dialog; replaced in tests, which can't show a modal dialog. */
    private static volatile BiFunction<Project, String, ReviseChangeDialog> dialogFactory = ReviseChangeDialog::new;

    @TestOnly
    public static void setDialogFactoryForTest(@Nullable BiFunction<Project, String, ReviseChangeDialog> factory) {
        dialogFactory = factory == null ? ReviseChangeDialog::new : factory;
    }

    /** Asks for the note, then delivers the handoff for {@code changeName}. Call on the EDT. */
    public static void revise(@NotNull Project project, @NotNull String changeName) {
        String base = project.getBasePath();
        // A handful of file checks, cheap enough for the EDT; done before the dialog so nothing is asked for in vain.
        WorkflowInvocation.Found found = base == null ? null : WorkflowInvocation.find(Path.of(base),
                WorkflowInvocation.candidateTools(project), WorkflowInvocation.UPDATE, WorkflowInvocation.UPDATE_SKILL);
        if (found == null) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_WORKFLOW, TITLE, NOT_INSTALLED_NOTICE,
                    NotificationType.WARNING, ActionLauncher.notificationAction(project, "Update OpenSpec…", "OpenSpec.Update"));
            return;
        }
        ReviseChangeDialog dialog = dialogFactory.apply(project, changeName);
        if (!dialog.showAndGet()) return;
        deliver(project, found, handoff(found, changeName, dialog.getNote()));
    }

    /** What the agent gets: the tool's update invocation, the change name, then the note when there is one. */
    static String handoff(WorkflowInvocation.Found found, String changeName, String note) {
        String args = note.isEmpty() ? changeName : changeName + " " + note;
        return WorkflowInvocation.handoff(found.invocation(), WorkflowInvocation.UPDATE_SKILL, args);
    }

    private static void deliver(@NotNull Project project, WorkflowInvocation.Found found, String handoff) {
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMode mode = resolver != null ? resolver.resolve().mode() : DeliveryMode.CLIPBOARD;
        String tool = found.tool().name();
        switch (mode) {
            case EDITOR_TAB -> {
                LightVirtualFile file = new LightVirtualFile(EDITOR_TAB_FILE_NAME, handoff);
                ApplicationManager.getApplication().invokeLater(() -> new OpenFileDescriptor(project, file).navigate(true),
                        ModalityState.nonModal(), project.getDisposed());
            }
            case CLIPBOARD, DIRECT_API -> {
                CopyPasteManager.getInstance().setContents(new StringSelection(handoff));
                String copied = "Copied " + handoff + " for " + tool + ". Paste it into " + tool + "'s chat.";
                OpenSpecNotifier.info(project, TITLE, mode == DeliveryMode.DIRECT_API ? copied + " " + DIRECT_API_NOTE : copied);
            }
        }
    }
}

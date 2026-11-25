package com.johnnyblabs.openspec.actions;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiProvider;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.dialogs.ProposeChangeDialog;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.toolwindow.OpenSpecToolWindowPanel;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Does what OpenSpec's {@code propose} workflow does: creates a change and every artifact it needs before apply,
 * from a description of what the user wants to build.
 *
 * <ul>
 *   <li><b>Direct API:</b> the plugin creates the change ({@code openspec new change}, or the same scaffold without
 *       the CLI) and generates the apply-required artifacts with the description as the request, then stops, as
 *       upstream does. The OpenSpec tool window shows the generation.</li>
 *   <li><b>Clipboard or editor tab:</b> the plugin hands the AI tool its own propose workflow (the skill on CLI 2.x,
 *       a skill or command on 1.x) with the description. The agent picks the name, asks its own questions and writes
 *       the artifacts; the plugin creates nothing.</li>
 * </ul>
 */
// DumbAware: Propose reads no indexes, and in-panel links launch it through ActionLauncher, whose update session
// would otherwise silently skip it while the IDE is indexing.
public class OpenSpecProposeAction extends OpenSpecBaseAction implements com.intellij.openapi.project.DumbAware {

    static final String TITLE = "Propose";
    static final String EDITOR_TAB_FILE_NAME = "openspec-propose.md";
    static final String NOT_INSTALLED_NOTICE = "Propose hands your description to your AI tool's propose workflow, "
            + "which this project doesn't have yet. Run Update OpenSpec… to install it, or use Direct API.";
    static final String WHATS_NEXT = "Your change is ready to implement. Review its artifacts in the workflow panel, "
            + "then implement the tasks with Apply, and archive the change when it's done.";

    @Override
    protected String getWorkflowId() { return WorkflowInvocation.PROPOSE; }

    /** The dialog; tests replace it, since they can't show a modal dialog. */
    interface DialogFactory {
        ProposeChangeDialog create(Project project, boolean directApi, String title, String modeLine);
    }

    private static final DialogFactory REAL_DIALOG = ProposeChangeDialog::new;
    private static volatile DialogFactory dialogFactory = REAL_DIALOG;

    @TestOnly
    static void setDialogFactoryForTest(@Nullable DialogFactory factory) {
        dialogFactory = factory == null ? REAL_DIALOG : factory;
    }

    /** Where a Direct API proposal goes; tests replace it to capture the call. */
    interface DirectApiTarget {
        void propose(Project project, String changeName, String description, @Nullable String schema, Runnable afterGenerated);
    }

    private static final DirectApiTarget TOOL_WINDOW = (project, name, description, schema, after) ->
            OpenSpecToolWindowPanel.withPanel(project, panel -> panel.proposeAndGenerate(name, description, schema, after));
    private static volatile DirectApiTarget directApiTarget = TOOL_WINDOW;

    @TestOnly
    static void setDirectApiTargetForTest(@Nullable DirectApiTarget target) {
        directApiTarget = target == null ? TOOL_WINDOW : target;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        propose(project);
    }

    /** Asks for the description and proposes in the resolved delivery mode. Call on the EDT. */
    static void propose(@NotNull Project project) {
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMode mode = resolver != null ? resolver.resolve().mode() : DeliveryMode.CLIPBOARD;
        if (mode == DeliveryMode.DIRECT_API) {
            proposeWithDirectApi(project);
        } else {
            handOff(project, mode);
        }
    }

    private static void proposeWithDirectApi(Project project) {
        String provider = AiProvider.fromString(OpenSpecSettings.getInstance(project).getAiProvider()).getDisplayName();
        ProposeChangeDialog dialog = dialogFactory.create(project, true, "Propose a Change with " + provider,
                directApiModeLine(provider));
        if (!dialog.showAndGet()) return;
        directApiTarget.propose(project, dialog.getChangeName(), dialog.getDescription(), dialog.getSelectedSchema(),
                () -> whatsNextOnce(project));
    }

    private static void handOff(Project project, DeliveryMode mode) {
        handOff(project, mode, HANDOFF);
    }

    /**
     * A workflow Propose or Fast-Forward hands to the AI tool in clipboard and editor-tab modes: its id and skill,
     * the dialog title's verb, the editor tab's file name, and the notice when the project doesn't have it.
     */
    record Handoff(String workflowId, String skill, String title, String verb, String editorTabFileName,
                   String notInstalledNotice) {
    }

    static final Handoff HANDOFF = new Handoff(WorkflowInvocation.PROPOSE, WorkflowInvocation.PROPOSE_SKILL,
            TITLE, "Propose a Change", EDITOR_TAB_FILE_NAME, NOT_INSTALLED_NOTICE);

    /**
     * Asks for the description and hands the tool's {@code handoff} workflow the description alone: the workflow
     * picks the name and schema itself, and the plugin creates nothing. Call on the EDT.
     */
    static void handOff(Project project, DeliveryMode mode, Handoff handoff) {
        // A handful of project file checks, cheap enough for the EDT (home skills come from a cache that never reads
        // there); done before the dialog so nothing is asked for in vain, and so the dialog can name the tool.
        WorkflowInvocation.Found found = WorkflowInvocation.find(project, handoff.workflowId(), handoff.skill());
        if (found == null) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_WORKFLOW, handoff.title(), handoff.notInstalledNotice(),
                    NotificationType.WARNING, ActionLauncher.notificationAction(project, "Update OpenSpec…", "OpenSpec.Update"));
            return;
        }
        ProposeChangeDialog dialog = dialogFactory.create(project, false,
                handoff.verb() + " with " + found.tool().name(), handoffModeLine(found, mode));
        if (!dialog.showAndGet()) return;
        String text = WorkflowInvocation.handoff(found.invocation(), handoff.skill(), dialog.getDescription());
        WorkflowHandoffDelivery.deliver(project, handoff.title(), handoff.editorTabFileName(), "", found, text, mode);
    }

    static String directApiModeLine(String provider) {
        return "Creates the change and generates its planning artifacts with " + provider + ". To hand it to your AI "
                + "tool instead, change the delivery mode in the OpenSpec tool window.";
    }

    static String handoffModeLine(WorkflowInvocation.Found found, DeliveryMode mode) {
        String tool = found.tool().name();
        String how = mode == DeliveryMode.EDITOR_TAB ? "Opens " + found.invocation() + " in an editor tab"
                : "Copies " + found.invocation();
        return how + " for " + tool + ", with your description. " + tool + " creates the change and asks what it needs. "
                + "Change the delivery mode in the OpenSpec tool window.";
    }

    /** The first proposal's "What's next" notice, once generation has finished. */
    private static void whatsNextOnce(Project project) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (settings.isFirstProposalCompleted()) return;
        OpenSpecNotifier.info(project, "What's Next", WHATS_NEXT);
        settings.setFirstProposalCompleted(true);
    }
}

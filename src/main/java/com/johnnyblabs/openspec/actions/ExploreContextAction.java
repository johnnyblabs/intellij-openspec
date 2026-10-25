package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.dialogs.ExploreTopicDialog;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.services.ExplorePromptService;
import com.johnnyblabs.openspec.services.ExplorePromptService.Delivery;
import com.johnnyblabs.openspec.services.ExplorePromptService.Instructions;
import com.johnnyblabs.openspec.services.ExplorePromptService.Prompt;
import com.johnnyblabs.openspec.toolwindow.ExplorePanel;
import com.johnnyblabs.openspec.toolwindow.ExplorePanelService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Explore action aligned with the OpenSpec explore workflow.
 * Prompts for an optional topic, assembles the explore prompt (skill instructions +
 * project context + topic), and delivers via the configured delivery mode.
 *
 * <p>Assembling the context runs the OpenSpec CLI, so every delivery builds the prompt in a background task and
 * delivers it when the task finishes.
 */
public class ExploreContextAction extends OpenSpecBaseAction {

    private static final Logger LOG = Logger.getInstance(ExploreContextAction.class);

    static final String NO_PROVIDER_NOTICE = "No Direct API provider is configured, so the prompt was copied to the "
            + "clipboard. Paste it into your AI tool, or configure a provider in Settings \u2192 Tools \u2192 OpenSpec "
            + "\u2192 Direct API.";
    static final String CANCELLED_NOTICE = "Cancelled.";
    static final String EDITOR_TAB_FILE_NAME = "openspec-explore-prompt.md";

    /** The Direct API call behind Explore; replaced in tests, because {@link DirectApiService} is final. */
    public interface RawRequest {
        boolean isConfigured();

        String send(String prompt) throws AiApiException;
    }

    private static volatile @Nullable RawRequest rawRequestOverride;

    /** Starts Explore's background tasks; replaced in tests, where ProgressManager runs a task synchronously. */
    private static volatile Consumer<Task.Backgroundable> taskRunner = ExploreContextAction::runInBackground;

    private static void runInBackground(Task.Backgroundable task) {
        ProgressManager.getInstance().run(task);
    }

    /** Replaces how Explore starts its tasks, so a test can see one was started; {@code null} restores the real one. */
    @TestOnly
    public static void setTaskRunnerForTest(@Nullable Consumer<Task.Backgroundable> runner) {
        taskRunner = runner == null ? ExploreContextAction::runInBackground : runner;
    }

    @TestOnly
    public static void setRawRequestForTest(@Nullable RawRequest request) {
        rawRequestOverride = request;
    }

    private static @Nullable RawRequest rawRequest(@NotNull Project project) {
        RawRequest override = rawRequestOverride;
        if (override != null) return override;
        DirectApiService apiService = project.getService(DirectApiService.class);
        if (apiService == null) return null;
        return new RawRequest() {
            @Override
            public boolean isConfigured() {
                return apiService.isConfigured();
            }

            @Override
            public String send(String prompt) throws AiApiException {
                return apiService.generateRaw(prompt);
            }
        };
    }

    @Override
    protected String getWorkflowId() { return "explore"; }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // If Direct API, activate the Explore panel and focus its inline input
        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMethodResolver.ResolvedMethod resolved = resolver != null
                ? resolver.resolve()
                : new DeliveryMethodResolver.ResolvedMethod(DeliveryMode.CLIPBOARD, "Copy to Clipboard");

        if (resolved.mode() == DeliveryMode.DIRECT_API) {
            ExplorePanelService panelService = project.getService(ExplorePanelService.class);
            ExplorePanel panel = panelService != null ? panelService.getAndActivate() : null;
            if (panel != null) {
                panel.focusInput();
                return;
            }
            // Fall through to dialog if panel not available
        }

        runExploreWithDialog(project);
    }

    /**
     * Runs the explore workflow via the modal dialog for non-Direct-API delivery modes.
     */
    private static void runExploreWithDialog(@NotNull Project project) {
        ExploreTopicDialog dialog = new ExploreTopicDialog(project);
        if (!dialog.showAndGet()) {
            return; // User cancelled
        }
        String topic = dialog.getTopic();
        runExploreDeliver(project, topic);
    }

    /**
     * Runs the explore workflow: show topic dialog, build prompt, deliver.
     * Can be called from the action or from the ExplorePanel toolbar.
     */
    public static void runExplore(@NotNull Project project) {
        runExplore(project, null);
    }

    /**
     * Runs the explore workflow with an optional pre-filled topic.
     * If topic is null, shows the dialog. If non-null, skips the dialog and uses it directly.
     */
    public static void runExplore(@NotNull Project project, String prefillTopic) {
        String topic;
        if (prefillTopic != null) {
            topic = prefillTopic;
        } else {
            ExploreTopicDialog dialog = new ExploreTopicDialog(project);
            if (!dialog.showAndGet()) {
                return; // User cancelled
            }
            topic = dialog.getTopic();
        }
        runExploreDeliver(project, topic);
    }

    /**
     * Builds the explore prompt and delivers via Direct API, bypassing the delivery mode resolver.
     * Used by the Explore panel's inline input, which only exists when Direct API is configured.
     */
    public static void runExploreDirect(@NotNull Project project, String topic) {
        runExploreDirect(project, project.getService(ExplorePromptService.class), topic);
    }

    static void runExploreDirect(@NotNull Project project, @Nullable ExplorePromptService promptService, String topic) {
        if (promptService == null) {
            // The panel disabled its input before calling; nothing will run to give it back.
            ExplorePanelService panelService = project.getService(ExplorePanelService.class);
            ExplorePanel panel = panelService != null ? panelService.getExplorePanel() : null;
            if (panel != null) panel.restoreInput();
            return;
        }

        deliverDirectApi(project, promptService, topic);
    }

    /**
     * Builds the explore prompt and delivers via the configured delivery mode.
     */
    private static void runExploreDeliver(@NotNull Project project, String topic) {
        ExplorePromptService promptService = project.getService(ExplorePromptService.class);
        if (promptService == null) return;

        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMethodResolver.ResolvedMethod resolved = resolver != null
                ? resolver.resolve()
                : new DeliveryMethodResolver.ResolvedMethod(DeliveryMode.CLIPBOARD, "Copy to Clipboard");

        switch (resolved.mode()) {
            case CLIPBOARD -> taskRunner.accept(newBuildTask(project, promptService, null, topic,
                    Delivery.AGENT, prompt -> deliverClipboard(project, prompt)));
            case EDITOR_TAB -> taskRunner.accept(newBuildTask(project, promptService, null, topic,
                    Delivery.AGENT, prompt -> deliverEditorTab(project, prompt)));
            case DIRECT_API -> deliverDirectApi(project, promptService, topic);
        }
    }

    /** Appended to a delivery's message: which file the instructions came from, or that the default was used. */
    static String sourceNote(Instructions instructions) {
        return instructions.isBuiltInDefault()
                ? " " + ExplorePanel.BUILT_IN_DEFAULT_NOTE
                : " Instructions: " + instructions.sourcePath() + ".";
    }

    private static void copyToClipboard(String prompt) {
        CopyPasteManager.getInstance().setContents(new StringSelection(prompt));
    }

    private static void deliverClipboard(@NotNull Project project, Prompt prompt) {
        copyToClipboard(prompt.text());
        OpenSpecNotifier.info(project, "Explore",
                "Explore prompt copied \u2014 paste into your AI tool to start exploring."
                        + sourceNote(prompt.instructions()));
    }

    /** Opens the prompt in an in-memory editor tab, so nothing is written to disk or shared between projects. */
    private static void deliverEditorTab(@NotNull Project project, Prompt prompt) {
        LightVirtualFile file = new LightVirtualFile(EDITOR_TAB_FILE_NAME, prompt.text());
        ApplicationManager.getApplication().invokeLater(() -> {
            new OpenFileDescriptor(project, file).navigate(true);
            if (prompt.instructions().isBuiltInDefault()) {
                OpenSpecNotifier.info(project, "Explore", ExplorePanel.BUILT_IN_DEFAULT_NOTE);
            }
        }, ModalityState.nonModal(), project.getDisposed());
    }

    private static void deliverDirectApi(@NotNull Project project, ExplorePromptService promptService, String topic) {
        RawRequest request = rawRequest(project);
        ExplorePanelService panelService = project.getService(ExplorePanelService.class);

        if (request == null || !request.isConfigured()) {
            // The clipboard goes to an agent, which has tools: copy the agent prompt, without the Direct API preamble.
            taskRunner.accept(newBuildTask(project, promptService, panelService, topic, Delivery.AGENT,
                    prompt -> {
                        copyToClipboard(prompt.text());
                        ExplorePanel existing = panelService != null ? panelService.getExplorePanel() : null;
                        if (existing != null) {
                            // The user is looking at the panel; say it there (onFinished gives the input back).
                            existing.setInstructionsSource(prompt.instructions());
                            existing.showNotice(topic, NO_PROVIDER_NOTICE);
                        } else {
                            OpenSpecNotifier.info(project, "Explore",
                                    NO_PROVIDER_NOTICE + sourceNote(prompt.instructions()));
                        }
                    }));
            return;
        }

        // "Thinking..." covers assembling the context as well as the request.
        ExplorePanel panel = panelService != null ? panelService.getAndActivate() : null;
        if (panel != null) {
            panel.showLoading(topic);
        }

        taskRunner.accept(newExploreTask(project, request, panelService,
                () -> promptService.build(topic, Delivery.DIRECT_API), topic));
    }

    /** Runs {@code runnable} on the EDT outside any modal dialog, and not at all once the project is closed. */
    private static void onEdt(@NotNull Project project, Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.nonModal(), project.getDisposed());
    }

    /**
     * Builds the prompt in the background, then hands it to {@code onBuilt} on the EDT. When the Explore panel started
     * it ({@code panelService} non-null), a cancel or failure is shown there and the input is always given back.
     * Package-private so tests can run it directly.
     */
    static Task.Backgroundable newBuildTask(@NotNull Project project, @NotNull ExplorePromptService promptService,
                                            @Nullable ExplorePanelService panelService, String topic,
                                            Delivery delivery, Consumer<Prompt> onBuilt) {
        return new Task.Backgroundable(project, "Preparing explore prompt...", true) {
            private Prompt built;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Gathering project context...");
                built = promptService.build(topic, delivery);
            }

            @Override
            public void onSuccess() {
                onBuilt.accept(built);
            }

            @Override
            public void onCancel() {
                ExplorePanel p = panelService != null ? panelService.getExplorePanel() : null;
                if (p != null) {
                    p.showNotice(topic, CANCELLED_NOTICE);
                }
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                LOG.warn("Building the explore prompt failed", error);
                String message = "Explore failed: "
                        + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
                ExplorePanel p = panelService != null ? panelService.getExplorePanel() : null;
                if (p != null) {
                    p.showError(topic, message);
                } else {
                    OpenSpecNotifier.warn(project, "Explore", message);
                }
            }

            @Override
            public void onFinished() {
                ExplorePanel p = panelService != null ? panelService.getExplorePanel() : null;
                if (p != null) {
                    p.restoreInput();
                }
            }
        };
    }

    /**
     * The background request behind the Explore panel: builds the prompt with {@code build}, then sends it. One task,
     * so one cancel covers both, and a build failure is shown like a failed request. Package-private so tests can run
     * it directly.
     */
    static Task.Backgroundable newExploreTask(@NotNull Project project, @NotNull RawRequest request,
                                              @Nullable ExplorePanelService panelService, Supplier<Prompt> build,
                                              String topic) {
        return new Task.Backgroundable(project, "Exploring...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);

                try {
                    indicator.setText("Gathering project context...");
                    Prompt prompt = build.get();
                    onEdt(project, () -> {
                        ExplorePanel p = panelService != null ? panelService.getExplorePanel() : null;
                        if (p != null) {
                            p.setInstructionsSource(prompt.instructions());
                        }
                    });
                    indicator.checkCanceled();

                    indicator.setText("Sending explore prompt to AI provider...");
                    String response = request.send(prompt.text());
                    // A cancel during the call wins over a late response (onCancel shows it).
                    indicator.checkCanceled();

                    onEdt(project, () -> {
                        ExplorePanel p = panelService != null ? panelService.getAndActivate() : null;
                        if (p != null) {
                            p.showResult(topic, response);
                        }
                    });
                } catch (AiApiException ex) {
                    onEdt(project, () -> {
                        ExplorePanel p = panelService != null ? panelService.getExplorePanel() : null;
                        if (p != null) {
                            p.showError(topic, ex.getMessage());
                        }
                        OpenSpecNotifier.warn(project, "Explore",
                                "API error: " + ex.getMessage());
                    });
                } catch (ProcessCanceledException ex) {
                    throw ex;
                } catch (RuntimeException ex) {
                    LOG.warn("Explore request failed", ex);
                    String message = "Explore failed: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                    onEdt(project, () -> {
                        ExplorePanel p = panelService != null ? panelService.getExplorePanel() : null;
                        if (p != null) {
                            p.showError(topic, message);
                        }
                    });
                }
            }

            @Override
            public void onCancel() {
                ExplorePanel p = panelService != null ? panelService.getExplorePanel() : null;
                if (p != null) {
                    p.showNotice(topic, CANCELLED_NOTICE);
                }
            }

            @Override
            public void onFinished() {
                // Runs on the EDT however the task ended, so no path leaves the input disabled.
                ExplorePanel p = panelService != null ? panelService.getExplorePanel() : null;
                if (p != null) {
                    p.restoreInput();
                }
            }
        };
    }
}

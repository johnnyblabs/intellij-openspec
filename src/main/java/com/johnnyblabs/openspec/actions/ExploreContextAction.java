package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.ai.AiApiException;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.ai.DirectApiService;
import com.johnnyblabs.openspec.dialogs.ExploreTopicDialog;
import com.johnnyblabs.openspec.services.DeliveryMethodResolver;
import com.johnnyblabs.openspec.services.ExplorePromptService;
import com.johnnyblabs.openspec.toolwindow.ExplorePanel;
import com.johnnyblabs.openspec.toolwindow.ExplorePanelService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Explore action aligned with the OpenSpec explore workflow.
 * Prompts for an optional topic, assembles the explore prompt (skill instructions +
 * project context + topic), and delivers via the configured delivery mode.
 */
public class ExploreContextAction extends OpenSpecBaseAction {

    private static final Logger LOG = Logger.getInstance(ExploreContextAction.class);

    static final String NO_PROVIDER_NOTICE = "No Direct API provider is configured, so the prompt was copied to the "
            + "clipboard. Paste it into your AI tool, or configure a provider in Settings \u2192 Tools \u2192 OpenSpec "
            + "\u2192 Direct API.";
    static final String CANCELLED_NOTICE = "Cancelled.";

    /** The Direct API call behind Explore; replaced in tests, because {@link DirectApiService} is final. */
    public interface RawRequest {
        boolean isConfigured();

        String send(String prompt) throws AiApiException;
    }

    private static volatile @Nullable RawRequest rawRequestOverride;

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
        ExplorePromptService promptService = project.getService(ExplorePromptService.class);
        if (promptService == null) return;

        String prompt = promptService.buildPrompt(topic);
        deliverDirectApi(project, prompt, topic);
    }

    /**
     * Builds the explore prompt and delivers via the configured delivery mode.
     */
    private static void runExploreDeliver(@NotNull Project project, String topic) {
        ExplorePromptService promptService = project.getService(ExplorePromptService.class);
        if (promptService == null) return;

        String prompt = promptService.buildPrompt(topic);

        DeliveryMethodResolver resolver = project.getService(DeliveryMethodResolver.class);
        DeliveryMethodResolver.ResolvedMethod resolved = resolver != null
                ? resolver.resolve()
                : new DeliveryMethodResolver.ResolvedMethod(DeliveryMode.CLIPBOARD, "Copy to Clipboard");

        switch (resolved.mode()) {
            case CLIPBOARD -> deliverClipboard(project, prompt);
            case EDITOR_TAB -> deliverEditorTab(project, prompt);
            case DIRECT_API -> deliverDirectApi(project, prompt, topic);
        }
    }

    private static void copyToClipboard(String prompt) {
        CopyPasteManager.getInstance().setContents(new StringSelection(prompt));
    }

    private static void deliverClipboard(@NotNull Project project, String prompt) {
        copyToClipboard(prompt);
        OpenSpecNotifier.info(project, "Explore",
                "Explore prompt copied \u2014 paste into your AI tool to start exploring.");
    }

    private static void deliverEditorTab(@NotNull Project project, String prompt) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Path tmpFile = Path.of(System.getProperty("java.io.tmpdir"),
                        "openspec-explore-prompt.md");
                Files.writeString(tmpFile, prompt, StandardCharsets.UTF_8);

                // VFS refresh on pooled thread — only editor open needs EDT
                var vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                        .refreshAndFindFileByNioFile(tmpFile);
                if (vf != null) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        new com.intellij.openapi.fileEditor.OpenFileDescriptor(project, vf).navigate(true);
                    });
                }
            } catch (IOException ex) {
                OpenSpecNotifier.warn(project, "Explore",
                        "Failed to create explore prompt file: " + ex.getMessage());
            }
        });
    }

    private static void deliverDirectApi(@NotNull Project project, String prompt, String topic) {
        RawRequest request = rawRequest(project);
        ExplorePanelService panelService = project.getService(ExplorePanelService.class);

        if (request == null || !request.isConfigured()) {
            copyToClipboard(prompt);
            ExplorePanel existing = panelService != null ? panelService.getExplorePanel() : null;
            if (existing != null) {
                // The user is looking at the panel; say it there and give the input back.
                existing.showNotice(topic, NO_PROVIDER_NOTICE);
            } else {
                OpenSpecNotifier.info(project, "Explore", NO_PROVIDER_NOTICE);
            }
            return;
        }

        // Show loading state in Explore panel
        ExplorePanel panel = panelService != null ? panelService.getAndActivate() : null;
        if (panel != null) {
            panel.showLoading(topic);
        }

        ProgressManager.getInstance().run(newExploreTask(project, request, panelService, prompt, topic));
    }

    /** The background request behind the Explore panel; package-private so tests can run it directly. */
    static Task.Backgroundable newExploreTask(@NotNull Project project, @NotNull RawRequest request,
                                              @Nullable ExplorePanelService panelService, String prompt, String topic) {
        return new Task.Backgroundable(project, "Exploring...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Sending explore prompt to AI provider...");

                try {
                    String response = request.send(prompt);
                    // A cancel during the call wins over a late response (onCancel shows it).
                    indicator.checkCanceled();

                    ApplicationManager.getApplication().invokeLater(() -> {
                        ExplorePanel p = panelService != null ? panelService.getAndActivate() : null;
                        if (p != null) {
                            p.showResult(topic, response);
                        }
                    });
                } catch (AiApiException ex) {
                    ApplicationManager.getApplication().invokeLater(() -> {
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
                    ApplicationManager.getApplication().invokeLater(() -> {
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

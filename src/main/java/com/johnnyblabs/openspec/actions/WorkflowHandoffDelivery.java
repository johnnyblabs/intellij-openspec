package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;
import com.johnnyblabs.openspec.ai.DeliveryMode;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

/**
 * Hands a workflow's invocation to the user's AI tool: opens it in an editor tab, or copies it to the clipboard for the
 * user to paste into the tool's chat. Direct API never reaches here; the action generates in the IDE instead.
 */
final class WorkflowHandoffDelivery {

    private WorkflowHandoffDelivery() {
    }

    static void deliver(@NotNull Project project, @NotNull String title, @NotNull String editorTabFileName,
                        @NotNull String directApiNote, WorkflowInvocation.Found found, String handoff,
                        @NotNull DeliveryMode mode) {
        String tool = found.tool().name();
        switch (mode) {
            case EDITOR_TAB -> {
                LightVirtualFile file = new LightVirtualFile(editorTabFileName, handoff);
                ApplicationManager.getApplication().invokeLater(() -> new OpenFileDescriptor(project, file).navigate(true),
                        ModalityState.nonModal(), project.getDisposed());
            }
            case CLIPBOARD, DIRECT_API -> {
                CopyPasteManager.getInstance().setContents(new StringSelection(handoff));
                String copied = "Copied " + handoff + " for " + tool + ". Paste it into " + tool + "'s chat.";
                OpenSpecNotifier.info(project, title, mode == DeliveryMode.DIRECT_API ? copied + " " + directApiNote : copied);
            }
        }
    }
}

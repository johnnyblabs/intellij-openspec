package com.johnnyblabs.openspec.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OpenSpecLineMarkerProvider implements LineMarkerProvider {

    private static final Icon REQUIREMENT_ICON = IconLoader.getIcon("/icons/requirement.svg",
            OpenSpecLineMarkerProvider.class);

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (element.getContainingFile() == null
                || !OpenSpecFileUtil.isSpecFile(element.getContainingFile().getVirtualFile())) {
            return null;
        }

        String text = element.getText();
        String name = com.johnnyblabs.openspec.util.SpecPatterns.requirementName(text);
        if (name != null) {
            return new LineMarkerInfo<>(
                    element,
                    element.getTextRange(),
                    REQUIREMENT_ICON,
                    e -> "Requirement: " + name,
                    null,
                    GutterIconRenderer.Alignment.LEFT,
                    () -> "OpenSpec Requirement"
            );
        }

        return null;
    }
}

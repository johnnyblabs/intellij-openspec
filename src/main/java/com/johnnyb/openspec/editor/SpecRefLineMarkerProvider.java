package com.johnnyb.openspec.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects {@code // @spec domain:Requirement Name} comments in Java source files
 * and shows a gutter icon that navigates to the corresponding spec file.
 */
public final class SpecRefLineMarkerProvider implements LineMarkerProvider {

    private static final Icon SPEC_ICON = IconLoader.getIcon("/icons/requirement.svg",
            SpecRefLineMarkerProvider.class);

    private static final Pattern SPEC_REF_PATTERN = Pattern.compile(
            "@spec\\s+([\\w-]+):(.+)");

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiComment comment)) {
            return null;
        }

        Project project = element.getProject();
        if (!OpenSpecFileUtil.isOpenSpecProject(project)) {
            return null;
        }

        String text = comment.getText();
        if (text == null) return null;

        Matcher matcher = SPEC_REF_PATTERN.matcher(text);
        if (!matcher.find()) return null;

        String domain = matcher.group(1).trim();
        String requirement = matcher.group(2).trim();

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                SPEC_ICON,
                e -> "Spec: " + domain + " \u2014 " + requirement,
                (mouseEvent, elt) -> {
                    VirtualFile specsDir = OpenSpecFileUtil.getSpecsDir(elt.getProject());
                    if (specsDir == null) return;
                    VirtualFile domainDir = specsDir.findChild(domain);
                    if (domainDir == null) return;
                    VirtualFile specFile = domainDir.findChild("spec.md");
                    if (specFile != null) {
                        FileEditorManager.getInstance(elt.getProject()).openFile(specFile, true);
                    }
                },
                GutterIconRenderer.Alignment.LEFT,
                () -> "OpenSpec Spec Reference"
        );
    }
}

package com.johnnyblabs.openspec.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects {@code // @spec domain:Requirement Name} comments in source files of any language
 * and shows a gutter icon that navigates to the corresponding spec file. Detection keys off the
 * language-neutral {@link PsiComment} interface, so it works in Java, Kotlin, Go, Python, and any
 * other language whose comments are exposed as {@code PsiComment} (registered for all languages
 * via {@code language=""} in plugin.xml).
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
        // Fast, non-refreshing check: this runs for every comment of every language on each
        // highlighting pass, so it must not trigger a synchronous VFS refresh.
        if (!OpenSpecFileUtil.isOpenSpecProjectFast(project)) {
            return null;
        }

        String[] ref = parseSpecRef(comment.getText());
        if (ref == null) return null;

        String domain = ref[0];
        String requirement = ref[1];

        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                SPEC_ICON,
                e -> "Spec: " + domain + " \u2014 " + requirement,
                (mouseEvent, elt) -> navigateToSpec(elt.getProject(), domain),
                GutterIconRenderer.Alignment.LEFT,
                () -> "OpenSpec Spec Reference"
        );
    }

    /**
     * Parses an {@code @spec <domain>:<requirement>} reference from comment text. Returns
     * {@code [domain, requirement]} (both trimmed) or {@code null} if the text carries no
     * reference. The detection is pure text and therefore language-agnostic — it matches the
     * same reference in a {@code //}, {@code #}, {@code --}, or any other comment style.
     *
     * <p>Visible for testing so the language-agnostic detection can be verified without
     * constructing platform {@code LineMarkerInfo}/PSI.
     */
    static String @Nullable [] parseSpecRef(@Nullable String commentText) {
        if (commentText == null) return null;
        Matcher matcher = SPEC_REF_PATTERN.matcher(commentText);
        if (!matcher.find()) return null;
        return new String[]{matcher.group(1).trim(), matcher.group(2).trim()};
    }

    /**
     * Opens {@code openspec/specs/<domain>/spec.md} in the editor, if it exists. No-op when the
     * specs directory, the domain directory, or the spec file is missing. Visible for testing.
     */
    static void navigateToSpec(@NotNull Project project, @NotNull String domain) {
        VirtualFile specsDir = OpenSpecFileUtil.getSpecsDir(project);
        if (specsDir == null) return;
        VirtualFile domainDir = specsDir.findChild(domain);
        if (domainDir == null) return;
        VirtualFile specFile = domainDir.findChild("spec.md");
        if (specFile != null) {
            FileEditorManager.getInstance(project).openFile(specFile, true);
        }
    }
}

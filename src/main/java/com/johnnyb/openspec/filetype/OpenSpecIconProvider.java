package com.johnnyb.openspec.filetype;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class OpenSpecIconProvider extends IconProvider {

    private static final Icon SPEC_ICON = IconLoader.getIcon("/icons/spec.svg", OpenSpecIconProvider.class);
    private static final Icon DELTA_SPEC_ICON = IconLoader.getIcon("/icons/delta-spec.svg", OpenSpecIconProvider.class);

    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        if (!(element instanceof PsiFile psiFile)) {
            return null;
        }
        if (!"spec.md".equals(psiFile.getName())) {
            return null;
        }

        String path = psiFile.getVirtualFile() != null ? psiFile.getVirtualFile().getPath() : null;
        if (path == null) {
            return null;
        }

        // Delta spec: openspec/changes/<name>/specs/ or openspec/changes/archive/<name>/specs/
        if (path.contains("/openspec/changes/") && path.contains("/specs/")) {
            return DELTA_SPEC_ICON;
        }

        // Main spec: openspec/specs/<domain>/spec.md
        if (path.contains("/openspec/specs/")) {
            return SPEC_ICON;
        }

        return null;
    }
}

package com.johnnyb.openspec.validation;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ConfigValidationInspection extends LocalInspectionTool {

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
        if (file.getVirtualFile() == null
                || !"config.yaml".equals(file.getVirtualFile().getName())
                || file.getVirtualFile().getParent() == null
                || !"openspec".equals(file.getVirtualFile().getParent().getName())) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        String text = file.getText();
        List<ProblemDescriptor> problems = new ArrayList<>();

        if (!text.contains("schema:")) {
            PsiElement element = file.getFirstChild();
            if (element != null) {
                problems.add(manager.createProblemDescriptor(
                        element,
                        "OpenSpec config.yaml must contain a 'schema' field",
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        isOnTheFly));
            }
        }

        if (!text.contains("profile:")) {
            PsiElement element = file.getFirstChild();
            if (element != null) {
                problems.add(manager.createProblemDescriptor(
                        element,
                        "OpenSpec config.yaml should contain a 'profile' field",
                        (LocalQuickFix) null,
                        ProblemHighlightType.WARNING,
                        isOnTheFly));
            }
        }

        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }
}

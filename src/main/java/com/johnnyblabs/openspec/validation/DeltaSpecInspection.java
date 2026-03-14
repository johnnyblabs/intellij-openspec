package com.johnnyblabs.openspec.validation;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class DeltaSpecInspection extends LocalInspectionTool {

    private static final Pattern DELTA_SECTION_PATTERN = Pattern.compile(
            "^## (ADDED|MODIFIED|REMOVED)", Pattern.MULTILINE);

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
        if (!OpenSpecFileUtil.isDeltaSpecFile(file.getVirtualFile())) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        String text = file.getText();
        List<ProblemDescriptor> problems = new ArrayList<>();

        if (!DELTA_SECTION_PATTERN.matcher(text).find()) {
            PsiElement element = file.getFirstChild();
            if (element != null) {
                problems.add(manager.createProblemDescriptor(
                        element,
                        "Delta spec should contain ADDED, MODIFIED, or REMOVED sections",
                        (LocalQuickFix) null,
                        ProblemHighlightType.WARNING,
                        isOnTheFly));
            }
        }

        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }
}

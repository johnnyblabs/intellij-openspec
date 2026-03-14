package com.johnnyblabs.openspec.validation;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class SpecFormatInspection extends LocalInspectionTool {

    private static final Pattern REQUIREMENT_PATTERN = Pattern.compile("### Requirement:");
    private static final Pattern RFC_2119_PATTERN = Pattern.compile("\\b(SHALL NOT|SHOULD NOT|SHALL|SHOULD|MAY)\\b");

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
        if (!OpenSpecFileUtil.isSpecFile(file.getVirtualFile())) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        String text = file.getText();
        java.util.List<ProblemDescriptor> problems = new java.util.ArrayList<>();

        if (!REQUIREMENT_PATTERN.matcher(text).find()) {
            PsiElement element = file.getFirstChild();
            if (element != null) {
                problems.add(manager.createProblemDescriptor(
                        element,
                        "Spec file should contain at least one '### Requirement:' heading",
                        (LocalQuickFix) null,
                        ProblemHighlightType.WARNING,
                        isOnTheFly));
            }
        }

        String[] sections = text.split("### Requirement:");
        for (int i = 1; i < sections.length; i++) {
            String section = sections[i];
            int nextReq = section.indexOf("### Requirement:");
            String reqBody = nextReq > 0 ? section.substring(0, nextReq) : section;

            if (!RFC_2119_PATTERN.matcher(reqBody).find()) {
                PsiElement element = file.findElementAt(
                        text.indexOf("### Requirement:" + section.substring(0, Math.min(20, section.length()))));
                if (element != null) {
                    problems.add(manager.createProblemDescriptor(
                            element,
                            "Requirement should contain an RFC 2119 keyword (SHALL, SHOULD, MAY)",
                            (LocalQuickFix) null,
                            ProblemHighlightType.WEAK_WARNING,
                            isOnTheFly));
                }
            }
        }

        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }
}

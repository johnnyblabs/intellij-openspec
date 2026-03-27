package com.johnnyblabs.openspec.validation;

import com.intellij.codeInspection.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.util.ArrayList;
import java.util.List;

public class ConfigValidationInspection extends LocalInspectionTool {

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
        if (file.getVirtualFile() == null) return ProblemDescriptor.EMPTY_ARRAY;

        String fileName = file.getVirtualFile().getName();
        boolean isConfig = "config.yaml".equals(fileName)
                && file.getVirtualFile().getParent() != null
                && "openspec".equals(file.getVirtualFile().getParent().getName());
        boolean isChangeMetadata = ".openspec.yaml".equals(fileName);

        if (!isConfig && !isChangeMetadata) return ProblemDescriptor.EMPTY_ARRAY;

        String text = file.getText();
        List<ProblemDescriptor> problems = new ArrayList<>();

        // YAML syntax validation — applies to both config.yaml and .openspec.yaml
        try {
            new Yaml(new LoaderOptions()).load(text);
        } catch (MarkedYAMLException e) {
            String problem = e.getProblem() != null ? e.getProblem() : "invalid YAML syntax";
            String location = "";
            if (e.getProblemMark() != null) {
                location = " (line " + (e.getProblemMark().getLine() + 1)
                        + ", column " + (e.getProblemMark().getColumn() + 1) + ")";
            }

            // Try to highlight near the error location
            int offset = 0;
            if (e.getProblemMark() != null) {
                int markOffset = e.getProblemMark().getIndex();
                if (markOffset >= 0 && markOffset < text.length()) {
                    offset = markOffset;
                }
            }
            PsiElement target = findNonEmptyElement(file, offset);
            if (target != null) {
                problems.add(manager.createProblemDescriptor(
                        target,
                        "YAML syntax error: " + problem + location,
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        isOnTheFly));
            }
            // Return early — no point checking fields if YAML is unparseable
            return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
        }

        // Field-level validation — only for config.yaml
        if (isConfig) {
            if (!text.contains("schema:")) {
                PsiElement element = findNonEmptyElement(file, 0);
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
                PsiElement element = findNonEmptyElement(file, 0);
                if (element != null) {
                    problems.add(manager.createProblemDescriptor(
                            element,
                            "OpenSpec config.yaml should contain a 'profile' field",
                            (LocalQuickFix) null,
                            ProblemHighlightType.WARNING,
                            isOnTheFly));
                }
            }
        }

        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    private static PsiElement findNonEmptyElement(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        if (element == null) element = file.getFirstChild();
        while (element != null && element.getTextLength() == 0) {
            element = element.getParent();
        }
        return element;
    }
}

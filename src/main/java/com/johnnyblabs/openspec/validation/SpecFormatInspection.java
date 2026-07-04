package com.johnnyblabs.openspec.validation;

import com.intellij.codeInspection.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.util.SpecPatterns;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpecFormatInspection extends LocalInspectionTool {

    private static final Pattern REQUIREMENT_PATTERN = SpecPatterns.REQUIREMENT_HEADER;
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
            PsiElement element = findNonEmptyElement(file, 0);
            if (element != null) {
                problems.add(manager.createProblemDescriptor(
                        element,
                        "Spec file should contain at least one '### Requirement:' heading",
                        (LocalQuickFix) null,
                        ProblemHighlightType.WARNING,
                        isOnTheFly));
            }
        }

        Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(text);
        while (reqMatcher.find()) {
            String headerLine = reqMatcher.group();
            String reqName = reqMatcher.group(1).trim();
            // Body runs from the end of the header line to the next requirement header (or EOF).
            // The header line itself is excluded: an RFC keyword that only appears in the header
            // does not satisfy the requirement-body rule (CLI 1.4+ parity).
            Matcher next = REQUIREMENT_PATTERN.matcher(text);
            int bodyEnd = next.find(reqMatcher.end()) ? next.start() : text.length();
            String reqBody = text.substring(reqMatcher.end(), bodyEnd);

            if (RFC_2119_PATTERN.matcher(reqBody).find()) continue;

            PsiElement element = findNonEmptyElement(file, reqMatcher.start());
            if (element == null) continue;

            if (RFC_2119_PATTERN.matcher(headerLine).find()) {
                // Keyword present but only in the header — mirror the CLI's targeted hint.
                problems.add(manager.createProblemDescriptor(
                        element,
                        "Requirement '" + reqName + "' has its RFC 2119 keyword only in the header — "
                                + "move the keyword onto the requirement body line",
                        new AddKeywordBodyLine(reqName),
                        ProblemHighlightType.WARNING,
                        isOnTheFly));
            } else {
                problems.add(manager.createProblemDescriptor(
                        element,
                        "Requirement should contain an RFC 2119 keyword (SHALL, SHOULD, MAY)",
                        (LocalQuickFix) null,
                        ProblemHighlightType.WEAK_WARNING,
                        isOnTheFly));
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

    /**
     * Deterministic fix for a keyword-in-header-only requirement: insert the header's
     * requirement text (which carries the keyword) as the first body line, leaving the
     * header itself unchanged. The author then edits the duplicated sentence in place.
     */
    static class AddKeywordBodyLine implements LocalQuickFix {
        private final String reqName;

        AddKeywordBodyLine(String reqName) {
            this.reqName = reqName;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Add requirement body line with the RFC 2119 keyword";
        }

        @Override
        public @NotNull String getName() {
            return "Insert '" + reqName + "' as the requirement body line";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
            if (psiFile == null) return;
            Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
            if (document == null) return;

            String text = psiFile.getText();
            Matcher m = SpecPatterns.REQUIREMENT_HEADER.matcher(text);
            while (m.find()) {
                if (!reqName.equals(m.group(1).trim())) continue;
                int insertAt = m.end();
                WriteCommandAction.runWriteCommandAction(project, () ->
                        document.insertString(insertAt, "\n\n" + reqName));
                return;
            }
        }
    }
}

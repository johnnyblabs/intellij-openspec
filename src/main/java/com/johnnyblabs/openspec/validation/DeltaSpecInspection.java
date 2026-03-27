package com.johnnyblabs.openspec.validation;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeltaSpecInspection extends LocalInspectionTool {

    private static final Pattern DELTA_SECTION_PATTERN = Pattern.compile(
            "^## (ADDED|MODIFIED|REMOVED) Requirements", Pattern.MULTILINE);
    private static final Pattern FULL_SPEC_PATTERN = Pattern.compile(
            "^## (Requirements|Purpose)", Pattern.MULTILINE);
    private static final Pattern REQUIREMENT_PATTERN = Pattern.compile(
            "^### Requirement:\\s*.+", Pattern.MULTILINE);
    private static final Pattern SCENARIO_PATTERN = Pattern.compile(
            "^#{4} Scenario:.+", Pattern.MULTILINE);

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly) {
        if (!OpenSpecFileUtil.isDeltaSpecFile(file.getVirtualFile())) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        String text = file.getText();
        List<ProblemDescriptor> problems = new ArrayList<>();

        // Skip full specs (## Requirements / ## Purpose) — only delta specs need ADDED/MODIFIED/REMOVED
        if (!DELTA_SECTION_PATTERN.matcher(text).find() && !FULL_SPEC_PATTERN.matcher(text).find()) {
            PsiElement element = findNonEmptyElement(file, 0);
            if (element != null) {
                problems.add(manager.createProblemDescriptor(
                        element,
                        "Delta spec should contain ADDED, MODIFIED, or REMOVED sections",
                        (LocalQuickFix) null,
                        ProblemHighlightType.WARNING,
                        isOnTheFly));
            }
        }

        // Structural validation of requirement blocks within delta sections
        Matcher sectionMatcher = DELTA_SECTION_PATTERN.matcher(text);
        while (sectionMatcher.find()) {
            String sectionType = sectionMatcher.group(1);
            int sectionStart = sectionMatcher.end();

            // Find end of section (next ## heading or end)
            Pattern nextH2 = Pattern.compile("^## ", Pattern.MULTILINE);
            Matcher nextH2Matcher = nextH2.matcher(text);
            int sectionEnd = text.length();
            if (nextH2Matcher.find(sectionStart)) {
                sectionEnd = nextH2Matcher.start();
            }
            String sectionContent = text.substring(sectionStart, sectionEnd);

            Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(sectionContent);
            while (reqMatcher.find()) {
                String reqHeader = reqMatcher.group().replaceFirst("^###\\s*Requirement:\\s*", "").trim();
                int nextReq = findNext(REQUIREMENT_PATTERN, sectionContent, reqMatcher.end());
                String reqContent = sectionContent.substring(reqMatcher.end(), nextReq);

                PsiElement element = findNonEmptyElement(file, sectionStart + reqMatcher.start());
                if (element == null) continue;

                if ("REMOVED".equals(sectionType)) {
                    boolean hasReason = reqContent.contains("**Reason**");
                    boolean hasMigration = reqContent.contains("**Migration**");
                    if (!hasReason || !hasMigration) {
                        String missing = !hasReason && !hasMigration ? "**Reason** and **Migration**"
                                : !hasReason ? "**Reason**" : "**Migration**";
                        problems.add(manager.createProblemDescriptor(
                                element,
                                "REMOVED requirement '" + reqHeader + "' must contain " + missing + " fields",
                                (LocalQuickFix) null,
                                ProblemHighlightType.ERROR,
                                isOnTheFly));
                    }
                } else {
                    if (!SCENARIO_PATTERN.matcher(reqContent).find()) {
                        String detail = "MODIFIED".equals(sectionType)
                                ? "MODIFIED requirement '" + reqHeader + "' must include full updated content with at least one scenario"
                                : "ADDED requirement '" + reqHeader + "' must have at least one scenario";

                        // Offer quick-fix for MODIFIED requirements if main spec has the requirement
                        LocalQuickFix fix = null;
                        if ("MODIFIED".equals(sectionType)) {
                            String capability = OpenSpecFileUtil.getDomainName(file.getVirtualFile());
                            String mainSpecBlock = findRequirementInMainSpec(
                                    file.getProject(), capability, reqHeader);
                            if (mainSpecBlock != null) {
                                fix = new CopyRequirementFromMainSpec(capability, reqHeader, mainSpecBlock);
                            }
                        }

                        problems.add(manager.createProblemDescriptor(
                                element,
                                detail,
                                fix,
                                ProblemHighlightType.ERROR,
                                isOnTheFly));
                    }
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

    private int findNext(Pattern pattern, String content, int from) {
        Matcher m = pattern.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }

    static String findRequirementInMainSpec(Project project, String capability, String reqName) {
        if (capability == null || reqName == null) return null;
        VirtualFile root = OpenSpecFileUtil.getOpenSpecRoot(project);
        if (root == null) return null;
        VirtualFile specsDir = root.findChild("specs");
        if (specsDir == null) return null;
        VirtualFile capDir = specsDir.findChild(capability);
        if (capDir == null) return null;
        VirtualFile specFile = capDir.findChild("spec.md");
        if (specFile == null) return null;

        try {
            String content = new String(specFile.contentsToByteArray(), StandardCharsets.UTF_8);
            Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(content);
            while (reqMatcher.find()) {
                String name = reqMatcher.group().replaceFirst("^###\\s*Requirement:\\s*", "").trim();
                if (reqName.equals(name)) {
                    int blockEnd = findNextStatic(REQUIREMENT_PATTERN, content, reqMatcher.end());
                    // Also stop at next ## heading
                    Matcher h2 = Pattern.compile("^## ", Pattern.MULTILINE).matcher(content);
                    if (h2.find(reqMatcher.end())) {
                        blockEnd = Math.min(blockEnd, h2.start());
                    }
                    return content.substring(reqMatcher.start(), blockEnd).stripTrailing();
                }
            }
        } catch (IOException e) {
            // Can't read main spec — no fix available
        }
        return null;
    }

    private static int findNextStatic(Pattern pattern, String content, int from) {
        Matcher m = pattern.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }

    private static class CopyRequirementFromMainSpec implements LocalQuickFix {
        private final String capability;
        private final String reqName;
        private final String mainSpecBlock;

        CopyRequirementFromMainSpec(String capability, String reqName, String mainSpecBlock) {
            this.capability = capability;
            this.reqName = reqName;
            this.mainSpecBlock = mainSpecBlock;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Copy requirement from main spec";
        }

        @Override
        public @NotNull String getName() {
            return "Copy '" + reqName + "' from " + capability + "/spec.md";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
            if (psiFile == null) return;

            String text = psiFile.getText();
            // Find the requirement line in the delta spec
            Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(text);
            while (reqMatcher.find()) {
                String name = reqMatcher.group().replaceFirst("^###\\s*Requirement:\\s*", "").trim();
                if (!reqName.equals(name)) continue;

                // Find the end of this requirement block
                int blockEnd = findNextStatic(REQUIREMENT_PATTERN, text, reqMatcher.end());
                // Also stop at next ## heading
                Matcher h2 = Pattern.compile("^## ", Pattern.MULTILINE).matcher(text);
                if (h2.find(reqMatcher.end())) {
                    blockEnd = Math.min(blockEnd, h2.start());
                }

                // Replace the requirement block with the main spec content
                String before = text.substring(0, reqMatcher.start());
                String after = text.substring(blockEnd);
                String newText = before + mainSpecBlock + "\n\n" + after;

                com.intellij.openapi.editor.Document document =
                        com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(psiFile);
                if (document != null) {
                    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, () ->
                            document.setText(newText));
                }
                return;
            }
        }
    }
}

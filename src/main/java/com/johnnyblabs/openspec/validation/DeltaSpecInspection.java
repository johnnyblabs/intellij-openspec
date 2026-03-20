package com.johnnyblabs.openspec.validation;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

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

                PsiElement element = file.findElementAt(sectionStart + reqMatcher.start());
                if (element == null) element = file.getFirstChild();
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
                        problems.add(manager.createProblemDescriptor(
                                element,
                                detail,
                                (LocalQuickFix) null,
                                ProblemHighlightType.ERROR,
                                isOnTheFly));
                    }
                }
            }
        }

        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    private int findNext(Pattern pattern, String content, int from) {
        Matcher m = pattern.matcher(content);
        if (m.find(from)) {
            return m.start();
        }
        return content.length();
    }
}

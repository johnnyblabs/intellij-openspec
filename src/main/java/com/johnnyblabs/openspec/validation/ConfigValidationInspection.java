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
        Object parsed;
        try {
            parsed = new Yaml(new LoaderOptions()).load(text);
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
            // A pointer-only config hands planning to a store, whose own config sets the schema.
            if (!text.contains("schema:") && !declaresStore(text)) {
                PsiElement element = findNonEmptyElement(file, 0);
                if (element != null) {
                    // INFORMATION, not WARNING: `openspec validate` is clean on a missing schema
                    // (upstream defaults to 'spec-driven'), so a warning squiggle would be stricter than
                    // the CLI. This is an advisory-only nudge, mirroring the built-in validator's
                    // config-schema-required INFO.
                    problems.add(manager.createProblemDescriptor(
                            element,
                            "OpenSpec config.yaml has no 'schema' field; it defaults to 'spec-driven' "
                                    + "— add one to be explicit",
                            (LocalQuickFix) null,
                            ProblemHighlightType.INFORMATION,
                            isOnTheFly));
                }
            }
            // skillsScope (CLI 2.1+): any value but project or global, an empty one included, is what upstream's
            // config reader warns about. It ignores the value and still applies the rest of the config.
            if (parsed instanceof java.util.Map<?, ?> map && map.containsKey("skillsScope")
                    && !"project".equals(map.get("skillsScope")) && !"global".equals(map.get("skillsScope"))) {
                PsiElement element = findNonEmptyElement(file, keyOffset(text, "skillsScope"));
                if (element != null) {
                    problems.add(manager.createProblemDescriptor(element, INVALID_SKILLS_SCOPE,
                            (LocalQuickFix) null, ProblemHighlightType.WARNING, isOnTheFly));
                }
            }
            // No `profile:` nag: `profile` is the GLOBAL workflow profile (`openspec config profile`),
            // never a project config.yaml field, and it's absent from a clean `openspec init` config and
            // from upstream's Zod schema. Nagging for it warned on a CLI-clean file (and implied an
            // off-model concept). The "Profile field absent is accepted" validation scenario already
            // documents that absence is a non-issue.
        }

        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    /** CLI 2.1's own warning for an invalid {@code skillsScope}, word for word. */
    static final String INVALID_SKILLS_SCOPE = "Invalid 'skillsScope' field in config (must be \"project\" or \"global\")";

    /** Where a top-level {@code key:} starts, or 0 when it can't be found on a line of its own. */
    private static int keyOffset(String text, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^" + key + "\\s*:").matcher(text);
        return m.find() ? m.start() : 0;
    }

    private static PsiElement findNonEmptyElement(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        if (element == null) element = file.getFirstChild();
        while (element != null && element.getTextLength() == 0) {
            element = element.getParent();
        }
        return element;
    }

    /** Whether the config has a top-level {@code store:} key, which hands planning to that store. */
    static boolean declaresStore(String text) {
        try {
            Object loaded = new org.yaml.snakeyaml.Yaml().load(text);
            return loaded instanceof java.util.Map<?, ?> map && map.containsKey("store");
        } catch (RuntimeException e) {
            return false;
        }
    }
}

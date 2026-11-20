package com.johnnyblabs.openspec.search;

import com.johnnyblabs.openspec.util.CodeFences;
import com.johnnyblabs.openspec.util.SpecPatterns;

import java.util.Arrays;

/**
 * Pure, fence-aware locator for a requirement's {@code ### Requirement: <name>} header line.
 *
 * <p>Mirrors {@code SpecParsingService}'s fence masking so Search-Everywhere navigation lands on
 * the same header the parser recognized: a {@code ### Requirement:} inside a fenced code block is
 * not a requirement and must not be matched. No Swing/IntelliJ-platform dependency, so it is
 * unit-testable headlessly (the requirement-jump behavior is otherwise only observable in a headful
 * uiSmoke run).
 */
public final class RequirementLineFinder {

    private RequirementLineFinder() {
    }

    /**
     * 0-based index of the non-fenced requirement header line whose name equals
     * {@code requirementName}, or 0 (top of file) when not found.
     */
    public static int findRequirementLine(String content, String requirementName) {
        if (content == null || requirementName == null) {
            return 0;
        }
        String target = requirementName.trim();
        String[] lines = content.split("\n", -1);
        boolean[] fenced = CodeFences.mask(Arrays.asList(lines));
        for (int i = 0; i < lines.length; i++) {
            if (fenced[i]) {
                continue;
            }
            String name = SpecPatterns.requirementName(lines[i]);
            if (name != null && name.equals(target)) {
                return i;
            }
        }
        return 0;
    }
}

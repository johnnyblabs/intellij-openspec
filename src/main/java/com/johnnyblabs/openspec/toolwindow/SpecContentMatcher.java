package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.Scenario;
import com.johnnyblabs.openspec.model.SpecFile;

import java.util.Locale;

/**
 * Pure, case-insensitive full-text matcher over parsed spec content.
 *
 * <p>The Browse tree's search box historically matched node <em>labels</em> only. This matcher
 * widens that reach to requirement bodies and scenario text so a term occurring only inside a
 * requirement's prose ("rate limiting") surfaces its spec and requirement nodes. It is deliberately
 * free of any Swing/platform dependency so it can be unit-tested headlessly, and it is the single
 * source of the "searchable text" a tree node carries (see {@link SpecTreeModel#buildSpecsNode}).
 */
public final class SpecContentMatcher {

    private SpecContentMatcher() {
    }

    /**
     * The concatenated searchable text of a requirement: its name, body, and every scenario's name
     * and clauses. Used both for {@link #matches(Requirement, String)} and to seed the tree node's
     * {@code searchText}, so the tree filter and the matcher agree by construction.
     */
    public static String searchableText(Requirement requirement) {
        if (requirement == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, requirement.getName());
        append(sb, requirement.getBody());
        for (Scenario scenario : requirement.getScenarios()) {
            append(sb, scenario.getName());
            for (String clause : scenario.getClauses()) {
                append(sb, clause);
            }
        }
        return sb.toString();
    }

    /**
     * True when {@code query} occurs (case-insensitively) anywhere in the requirement's name, body,
     * or scenario text.
     */
    public static boolean matches(Requirement requirement, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return searchableText(requirement)
                .toLowerCase(Locale.ROOT)
                .contains(query.toLowerCase(Locale.ROOT).trim());
    }

    /**
     * True when any requirement in the spec matches {@code query}.
     */
    public static boolean matches(SpecFile spec, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        for (Requirement requirement : spec.getRequirements()) {
            if (matches(requirement, query)) {
                return true;
            }
        }
        return false;
    }

    private static void append(StringBuilder sb, String text) {
        if (text != null && !text.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(text);
        }
    }
}

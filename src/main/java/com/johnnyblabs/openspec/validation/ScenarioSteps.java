package com.johnnyblabs.openspec.validation;

import com.johnnyblabs.openspec.util.CodeFences;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * OpenSpec's scenario step check (CLI 1.16.0 and later), a port of {@code findScenarioStepIssues}
 * ({@code dist/core/validation/validator.js}) with {@code parseScenarioBlocks}, {@code isScenarioLabeledHeader},
 * {@code hasScenarioBody} and {@code scenarioStepCoverage} ({@code dist/core/parsers}).
 * <ul>
 *   <li>A scenario is any {@code #### } header outside a fence; it runs to the next one, so a {@code #####} heading and
 *       nested lists belong to it.</li>
 *   <li>Only a header labeled {@code Scenario:} (in any case) is checked, and only when it has a body.</li>
 *   <li>A trigger is {@code **WHEN**}/{@code **GIVEN**} bolded in any case ({@code **} or {@code __}, a colon allowed
 *       inside the bold), or a bare uppercase {@code WHEN}/{@code GIVEN} anywhere on a body line; an outcome is the
 *       same for {@code THEN}. The header line and fenced lines don't count; HTML comments do. {@code AND} adds
 *       nothing by itself.</li>
 * </ul>
 */
public final class ScenarioSteps {

    private static final Pattern SCENARIO_HEADER = Pattern.compile("^####\\s+");
    private static final Pattern LABELED = Pattern.compile("^Scenario:\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOSING_RUN = Pattern.compile("[ \\t]+#+[ \\t]*$");
    private static final Pattern BOLD_TRIGGER = Pattern.compile("(?:\\*\\*|__)(?:WHEN|GIVEN):?(?:\\*\\*|__)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_TRIGGER = Pattern.compile("\\b(?:WHEN|GIVEN)\\b");
    private static final Pattern BOLD_OUTCOME = Pattern.compile("(?:\\*\\*|__)THEN:?(?:\\*\\*|__)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_OUTCOME = Pattern.compile("\\bTHEN\\b");

    /** A labeled scenario missing a step; {@code line} is the header's 0-based offset within the block given. */
    public record Missing(String scenario, int line, boolean trigger, boolean outcome) {
        /** "a WHEN/GIVEN step", "a THEN step", or both, as OpenSpec words it. */
        public String describe() {
            List<String> parts = new ArrayList<>();
            if (trigger) parts.add("a WHEN/GIVEN step");
            if (outcome) parts.add("a THEN step");
            return String.join(" and ", parts);
        }
    }

    private ScenarioSteps() {
    }

    /** The labeled scenarios in a requirement block (raw, not fence-masked) that lack a trigger or an outcome. */
    public static List<Missing> missing(String requirementRaw) {
        List<String> lines = List.of(requirementRaw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
        boolean[] mask = CodeFences.mask(lines);
        List<Missing> missing = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            if (!isScenarioHeader(lines, mask, i)) {
                i++;
                continue;
            }
            int start = i++;
            while (i < lines.size() && !isScenarioHeader(lines, mask, i)) i++;
            String header = lines.get(start);
            String afterHashes = SCENARIO_HEADER.matcher(header).replaceFirst("");
            if (!LABELED.matcher(afterHashes).find()) continue;
            List<String> body = lines.subList(start + 1, i);
            if (String.join("\n", body).strip().isEmpty()) continue;
            boolean trigger = false;
            boolean outcome = false;
            for (int j = start + 1; j < i; j++) {
                if (mask[j]) continue;
                String line = lines.get(j);
                if (BOLD_TRIGGER.matcher(line).find() || BARE_TRIGGER.matcher(line).find()) trigger = true;
                if (BOLD_OUTCOME.matcher(line).find() || BARE_OUTCOME.matcher(line).find()) outcome = true;
            }
            if (trigger && outcome) continue;
            String name = LABELED.matcher(CLOSING_RUN.matcher(afterHashes).replaceFirst("")).replaceFirst("").strip();
            missing.add(new Missing(name, start, !trigger, !outcome));
        }
        return missing;
    }

    private static boolean isScenarioHeader(List<String> lines, boolean[] mask, int i) {
        return !mask[i] && SCENARIO_HEADER.matcher(lines.get(i)).find();
    }
}

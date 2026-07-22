package com.johnnyblabs.openspec.legacy;

import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.Scenario;
import com.johnnyblabs.openspec.model.SpecFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pre-{@code align-spec-parser-with-cli} regex parser, copied verbatim from the previous
 * {@code SpecParsingService.parseSpecContent} (git {@code HEAD~1}) so a differential test can pin
 * the intended behavior changes against the old behavior. This class lives under {@code src/test/}
 * on purpose: it is <em>not</em> instrumented for coverage and must never ship in the plugin — it is
 * a frozen reference of the fence-blind multiline-regex parser the new line scanner replaced.
 *
 * <p>Only {@code parseSpecContent} is retained; the VFS/IO/EDT machinery is dropped because the
 * differential test drives pure string content. The recognition it encodes (the divergences the new
 * parser intentionally corrects) are: markers matched inside code fences, the bold {@code
 * **Scenario:**} form counted as a scenario, only {@code Scenario:}-labelled level-4 headers counted,
 * the {@code SHALL/SHOULD/MAY} keyword set (no {@code MUST}), and keyword classification over the
 * whole requirement section (header included) rather than the body.
 */
public final class LegacyRegexSpecParser {

    private static final Pattern TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern REQUIREMENT_PATTERN = com.johnnyblabs.openspec.util.SpecPatterns.REQUIREMENT_HEADER;
    private static final Pattern SCENARIO_PATTERN = Pattern.compile("^(?:#{4}\\s+Scenario:\\s+(.+)|\\*\\*Scenario:\\s+(.+?)\\*\\*)$", Pattern.MULTILINE);
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("\\b(SHALL NOT|SHOULD NOT|SHALL|SHOULD|MAY)\\b");

    public SpecFile parseSpecContent(String content, String domain, String filePath) {
        SpecFile spec = new SpecFile(domain, filePath);

        Matcher titleMatcher = TITLE_PATTERN.matcher(content);
        if (titleMatcher.find()) {
            spec.setTitle(titleMatcher.group(1).trim());
        }

        Matcher reqMatcher = REQUIREMENT_PATTERN.matcher(content);
        List<int[]> reqPositions = new ArrayList<>();
        while (reqMatcher.find()) {
            reqPositions.add(new int[]{reqMatcher.start(), reqMatcher.end()});
        }

        for (int i = 0; i < reqPositions.size(); i++) {
            int start = reqPositions.get(i)[0];
            int end = (i + 1 < reqPositions.size()) ? reqPositions.get(i + 1)[0] : content.length();
            String reqSection = content.substring(start, end);

            Matcher nameMatcher = REQUIREMENT_PATTERN.matcher(reqSection);
            if (!nameMatcher.find()) continue;

            Requirement req = new Requirement(nameMatcher.group(1).trim());

            Matcher kwMatcher = KEYWORD_PATTERN.matcher(reqSection);
            if (kwMatcher.find()) {
                req.setKeyword(kwMatcher.group(1));
            }

            String bodyStart = reqSection.substring(nameMatcher.end()).trim();
            int scenarioIdx = bodyStart.indexOf("**Scenario:");
            int headingScenarioIdx = bodyStart.indexOf("#### Scenario:");
            int firstScenario = -1;
            if (scenarioIdx > 0 && headingScenarioIdx > 0) {
                firstScenario = Math.min(scenarioIdx, headingScenarioIdx);
            } else if (scenarioIdx > 0) {
                firstScenario = scenarioIdx;
            } else if (headingScenarioIdx > 0) {
                firstScenario = headingScenarioIdx;
            }
            if (firstScenario > 0) {
                req.setBody(bodyStart.substring(0, firstScenario).trim());
            } else {
                req.setBody(bodyStart.trim());
            }

            Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(reqSection);
            while (scenarioMatcher.find()) {
                String scenarioName = scenarioMatcher.group(1) != null
                        ? scenarioMatcher.group(1).trim()
                        : scenarioMatcher.group(2).trim();
                Scenario scenario = new Scenario(scenarioName);

                int scenarioStart = scenarioMatcher.end();
                int scenarioEnd = reqSection.length();
                Matcher nextScenario = SCENARIO_PATTERN.matcher(reqSection.substring(scenarioStart));
                if (nextScenario.find()) {
                    scenarioEnd = scenarioStart + nextScenario.start();
                }

                String scenarioBody = reqSection.substring(scenarioStart, scenarioEnd);
                Pattern clausePattern = Pattern.compile("^-\\s+\\*{0,2}(GIVEN|WHEN|THEN|AND)\\*{0,2}\\s+(.+)$", Pattern.MULTILINE);
                Matcher clauseMatcher = clausePattern.matcher(scenarioBody);
                while (clauseMatcher.find()) {
                    scenario.addClause(clauseMatcher.group(1) + " " + clauseMatcher.group(2).trim());
                }

                req.addScenario(scenario);
            }

            spec.addRequirement(req);
        }

        return spec;
    }
}

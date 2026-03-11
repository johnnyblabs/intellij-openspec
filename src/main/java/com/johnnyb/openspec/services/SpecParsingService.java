package com.johnnyb.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyb.openspec.model.Requirement;
import com.johnnyb.openspec.model.Scenario;
import com.johnnyb.openspec.model.SpecFile;
import com.johnnyb.openspec.util.OpenSpecFileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class SpecParsingService {
    private static final Logger LOG = Logger.getInstance(SpecParsingService.class);

    private static final Pattern TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern REQUIREMENT_PATTERN = Pattern.compile("^###\\s+Requirement:\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern SCENARIO_PATTERN = Pattern.compile("^\\*\\*Scenario:\\s+(.+?)\\*\\*$", Pattern.MULTILINE);
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("\\b(SHALL NOT|SHOULD NOT|SHALL|SHOULD|MAY)\\b");

    private final Project project;

    public SpecParsingService(Project project) {
        this.project = project;
    }

    public List<SpecFile> parseAllSpecs() {
        List<SpecFile> specs = new ArrayList<>();
        VirtualFile specsDir = OpenSpecFileUtil.getSpecsDir(project);
        if (specsDir == null || !specsDir.exists()) return specs;

        for (VirtualFile domainDir : specsDir.getChildren()) {
            if (!domainDir.isDirectory()) continue;
            VirtualFile specMd = domainDir.findChild("spec.md");
            if (specMd != null) {
                SpecFile parsed = parseSpec(specMd, domainDir.getName());
                if (parsed != null) {
                    specs.add(parsed);
                }
            }
        }
        return specs;
    }

    public SpecFile parseSpec(VirtualFile file, String domain) {
        try {
            String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            return parseSpecContent(content, domain, file.getPath());
        } catch (IOException e) {
            LOG.error("Failed to read spec file: " + file.getPath(), e);
            return null;
        }
    }

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
            if (scenarioIdx > 0) {
                req.setBody(bodyStart.substring(0, scenarioIdx).trim());
            } else {
                req.setBody(bodyStart.trim());
            }

            Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(reqSection);
            while (scenarioMatcher.find()) {
                String scenarioName = scenarioMatcher.group(1).trim();
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

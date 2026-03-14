package com.johnnyb.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyb.openspec.model.CoverageResult;
import com.johnnyb.openspec.model.CoverageResult.DomainCoverage;
import com.johnnyb.openspec.model.CoverageResult.RequirementCoverage;
import com.johnnyb.openspec.model.SpecFile;
import com.johnnyb.openspec.model.Requirement;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class SpecCoverageService {
    private static final Logger LOG = Logger.getInstance(SpecCoverageService.class);
    private static final Pattern SPEC_REF_PATTERN = Pattern.compile("@spec\\s+([\\w-]+):(.+)");

    private final Project project;
    private volatile CoverageResult cachedResult;

    public SpecCoverageService(Project project) {
        this.project = project;
    }

    public CoverageResult computeCoverage() {
        SpecParsingService parsingService = project.getService(SpecParsingService.class);
        List<SpecFile> specs = parsingService.parseAllSpecs();

        // Scan source files for @spec references
        // Key: "domain:requirement" → list of file paths
        Map<String, List<String>> references = scanSourceFiles();

        // Build coverage result
        Map<String, DomainCoverage> domains = new LinkedHashMap<>();
        int totalReqs = 0;
        int coveredReqs = 0;

        for (SpecFile spec : specs) {
            List<RequirementCoverage> reqCoverages = new ArrayList<>();
            for (Requirement req : spec.getRequirements()) {
                String key = spec.getDomain() + ":" + req.getName();
                List<String> refs = references.getOrDefault(key, List.of());
                boolean covered = !refs.isEmpty();
                reqCoverages.add(new RequirementCoverage(
                        req.getName(), covered, spec.getFilePath(), refs));
                totalReqs++;
                if (covered) coveredReqs++;
            }
            if (!reqCoverages.isEmpty()) {
                domains.put(spec.getDomain(),
                        new DomainCoverage(spec.getDomain(), reqCoverages));
            }
        }

        CoverageResult result = new CoverageResult(domains, totalReqs, coveredReqs);
        cachedResult = result;
        return result;
    }

    public CoverageResult getCachedResult() {
        return cachedResult;
    }

    private Map<String, List<String>> scanSourceFiles() {
        Map<String, List<String>> references = new HashMap<>();
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

        fileIndex.iterateContent(file -> {
            if (file.isDirectory() || !"java".equals(file.getExtension())) return true;
            try {
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                Matcher matcher = SPEC_REF_PATTERN.matcher(content);
                while (matcher.find()) {
                    String domain = matcher.group(1).trim();
                    String requirement = matcher.group(2).trim();
                    String key = domain + ":" + requirement;
                    references.computeIfAbsent(key, k -> new ArrayList<>()).add(file.getPath());
                }
            } catch (Exception e) {
                LOG.debug("Failed to read file for coverage scan: " + file.getPath(), e);
            }
            return true;
        });

        return references;
    }
}

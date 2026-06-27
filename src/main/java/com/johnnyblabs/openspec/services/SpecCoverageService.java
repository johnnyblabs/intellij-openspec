package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.CoverageResult;
import com.johnnyblabs.openspec.model.CoverageResult.DomainCoverage;
import com.johnnyblabs.openspec.model.CoverageResult.RequirementCoverage;
import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.model.Requirement;

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
            if (file.isDirectory() || isRecognizedBinary(file)) return true;
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

    /**
     * Whether a file should be skipped because it is a recognized binary (image, archive,
     * compiled class, ...). The {@code @spec} reference convention is plain comment text and is
     * language-agnostic, so any text file is scanned regardless of language.
     *
     * <p>{@link UnknownFileType} is deliberately treated as scannable even though its
     * {@code isBinary()} returns {@code true}: when the running IDE has no plugin for a language
     * (e.g. a {@code .go} file in IntelliJ IDEA Community), that file resolves to
     * {@code UnknownFileType}. Skipping it would silently re-create the very 0%-coverage bug this
     * guard exists to avoid. Only file types that are both binary <em>and</em> recognized are
     * skipped.
     */
    private static boolean isRecognizedBinary(VirtualFile file) {
        FileType type = file.getFileType();
        return type.isBinary() && type != UnknownFileType.INSTANCE;
    }
}

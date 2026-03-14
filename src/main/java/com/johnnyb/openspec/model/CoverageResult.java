package com.johnnyb.openspec.model;

import java.util.List;
import java.util.Map;

public record CoverageResult(
        Map<String, DomainCoverage> domains,
        int totalRequirements,
        int coveredRequirements
) {
    public record DomainCoverage(
            String domain,
            List<RequirementCoverage> requirements
    ) {
        public long coveredCount() {
            return requirements.stream().filter(RequirementCoverage::covered).count();
        }
    }

    public record RequirementCoverage(
            String name,
            boolean covered,
            String specFilePath,
            List<String> referencingFiles
    ) {}
}

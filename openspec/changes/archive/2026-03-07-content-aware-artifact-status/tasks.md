## 1. Scaffolding Detection Service

- [x] 1.1 Create `ScaffoldingDetectionService` as a project-level `@Service` that exposes `isScaffolding(String filePath): boolean`
- [x] 1.2 Implement content analysis: strip markdown headings, HTML comments, and known placeholder lines; report as scaffolding if remaining content is below threshold (~20 chars)
- [x] 1.3 Add known placeholder patterns for tasks files (`Task 1`, `Task 2`, `Write unit tests`, `Integration testing`)
- [x] 1.4 Register the service in `plugin.xml`
- [x] 1.5 Write unit tests for scaffolding detection (scaffolded design, scaffolded tasks, real content, empty file, short but real content)

## 2. Status Override in ArtifactOrchestrationService

- [x] 2.1 After parsing the CLI DAG in `getArtifactStatus()`, call `ScaffoldingDetectionService` for each artifact with status DONE
- [x] 2.2 For scaffolded artifacts: resolve the file path from the change directory + outputPath, skip glob patterns (specs)
- [x] 2.3 Override scaffolded artifacts to READY if all dependencies are truly done, or BLOCKED with missingDeps if dependencies are also scaffolded
- [x] 2.4 Recalculate `isComplete` on the DAG after overrides
- [x] 2.5 Write unit tests for the override logic (all scaffolded, some scaffolded, none scaffolded, glob artifact skipped)

## 3. Guidance Card Next-Artifact Indicator

- [x] 3.1 In `showGuidanceCard()`, accept an optional `nextArtifactId` parameter
- [x] 3.2 Add a `guidanceNextLabel` (JBLabel) to the guidance card showing "Next up: Generate [artifact]" when a next artifact exists
- [x] 3.3 In `executeGeneration()`, after clipboard/editor delivery, compute the next ready artifact from the corrected DAG and pass it to `showGuidanceCard()`
- [x] 3.4 Verify the plugin builds and all tests pass

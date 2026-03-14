## Context

The plugin already has two sides of the spec-to-code relationship: `SpecParsingService` parses all specs into `SpecFile` objects with requirements, and `SpecRefLineMarkerProvider` detects `// @spec domain:Requirement Name` comments in Java files. The coverage panel connects these two datasets to show which requirements have code references.

The tool window currently has two tabs (Browse, Console) created in `OpenSpecToolWindowFactory.createNormalContent()`. Adding a third "Coverage" tab follows the existing pattern.

## Goals / Non-Goals

**Goals:**
- Show per-domain and per-requirement coverage status (covered vs uncovered)
- Display overall and per-domain coverage percentages
- Click-to-navigate: requirement rows navigate to the spec file, reference rows navigate to the source file
- Refresh on demand via toolbar button
- Scan only Java files in the project source roots

**Non-Goals:**
- Real-time/automatic refresh on file save (future: use file watcher)
- Coverage enforcement or thresholds
- Supporting non-Java languages for `@spec` detection
- Tracking which specific line references which requirement (just presence/absence per file)

## Decisions

### Decision 1: JTree-based panel reusing existing patterns

Use a `JTree` with `DefaultTreeModel` like the Browse tab. The tree structure:

```
Specs (42 requirements, 67% covered)
├── plugin-core (3/5 covered)
│   ├── ✓ Project Detection          src/…/OpenSpecProjectService.java
│   ├── ✓ Config Parsing             src/…/ConfigService.java
│   ├── ✗ CLI Detection
│   ├── ✓ Startup Activity           src/…/OpenSpecProjectService.java
│   └── ✗ Version Support
├── validation (2/2 covered)
│   ├── ✓ Spec Format Validation     src/…/SpecFormatInspection.java
│   └── ✓ Config Validation          src/…/ConfigValidationInspection.java
└── editor (0/3 covered)
    ├── ✗ Spec Annotator
    ├── ✗ Scenario Annotator
    └── ✗ Line Marker Provider
```

**Alternative considered:** JBTable with columns. Rejected — the hierarchical domain → requirement structure maps naturally to a tree, and the Browse tab already establishes this pattern.

### Decision 2: SpecCoverageService as a project-level service

Create `SpecCoverageService` registered in `plugin.xml`. It:
1. Calls `SpecParsingService.parseAllSpecs()` to get all requirements
2. Scans Java files in project source roots using `FilenameIndex` and `PsiManager` for `@spec` pattern matches
3. Builds a `CoverageResult` mapping each `domain:requirement` to a list of referencing file paths
4. Caches results; refreshed on explicit user action

The scan uses the same regex as `SpecRefLineMarkerProvider`: `@spec\s+([\w-]+):(.+)` applied to file content via `VirtualFile.contentsToByteArray()` on a background thread.

**Alternative considered:** Reusing PSI/LineMarkerProvider infrastructure. Rejected — LineMarkerProvider only runs on open files. We need to scan all project source files.

### Decision 3: Background scanning with progress

The scan runs on a pooled thread via `ApplicationManager.getApplication().executeOnPooledThread()`. The panel shows a "Scanning..." state while running. For typical projects (hundreds of Java files), this completes in under a second. No progress bar needed initially.

### Decision 4: Coverage tab in tool window

Add the tab in `OpenSpecToolWindowFactory.createNormalContent()` between Browse and Console. The tab is labeled "Coverage" and creates a `SpecCoveragePanel` instance.

## Risks / Trade-offs

- **[Low risk] File scanning performance** → Only scans `.java` files in source roots, not test/resource files. Regex is fast (no backtracking). Projects with thousands of files might take 1-2 seconds.
- **[Low risk] Stale cache** → Coverage results are cached until the user clicks Refresh. Acceptable for v0.2.0 — auto-refresh via file watcher can be added later.
- **[Trade-off] String matching vs PSI** → Using raw file content + regex is simpler and doesn't require read locks or PSI parsing. Trade-off: slightly less precise (could match inside block comments or strings), but `@spec` is a distinctive enough pattern that false positives are negligible.

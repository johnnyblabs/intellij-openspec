## 1. Coverage Data Model

- [x] 1.1 Create `CoverageResult` record in `com.johnnyb.openspec.model` with: `Map<String, DomainCoverage> domains`, `int totalRequirements`, `int coveredRequirements`. `DomainCoverage` contains: `String domain`, `List<RequirementCoverage> requirements`. `RequirementCoverage` contains: `String name`, `boolean covered`, `String specFilePath`, `List<String> referencingFiles`.

## 2. Coverage Service

- [x] 2.1 Create `SpecCoverageService` as `@Service(Service.Level.PROJECT)` that provides `computeCoverage()` returning `CoverageResult`. It should: call `SpecParsingService.parseAllSpecs()`, scan Java source files in project content roots for `@spec` pattern matches using `ProjectFileIndex` and `VirtualFile.contentsToByteArray()`, cross-reference matches against parsed requirements, cache the result.
- [x] 2.2 Register `SpecCoverageService` in `plugin.xml`

## 3. Coverage Panel UI

- [x] 3.1 Create `SpecCoveragePanel` extending `JPanel` with: a `JTree` displaying the coverage tree, a toolbar with a Refresh button (`AllIcons.Actions.Refresh`), a summary label showing "X/Y requirements covered (Z%)"
- [x] 3.2 Build tree model from `CoverageResult`: root node shows overall stats, domain nodes show "domain (N/M covered)", requirement nodes show covered/uncovered indicator with referencing file name for covered items
- [x] 3.3 Use green (`JBColor`) for covered requirements, gray for uncovered. Use `requirement.svg` icon for all requirement nodes.
- [x] 3.4 Add double-click handler: navigates to `spec.md` for the requirement's domain
- [x] 3.5 Add "Scanning..." label shown while scan runs on background thread, replaced by tree when complete

## 4. Tool Window Integration

- [x] 4.1 Add Coverage tab in `OpenSpecToolWindowFactory.createNormalContent()` between Browse and Console tabs

## 5. Verification

- [x] 5.1 Build the project (`./gradlew build`) and verify no compilation errors

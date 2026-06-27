# Tasks

## 1. Delete the Coverage panel surface
- [x] 1.1 Remove the Coverage tab wiring in `OpenSpecToolWindowFactory` (the `SpecCoveragePanel` construction + `addContent(..., "Coverage", ...)`).
- [x] 1.2 Delete `toolwindow/SpecCoveragePanel.java`.
- [x] 1.3 Delete `services/SpecCoverageService.java` and `model/CoverageResult.java`.
- [x] 1.4 N/A — `SpecCoverageServiceTest` was added by the abandoned A branch; it does not exist on `main`/this branch, so there is nothing to delete.

## 2. Delete the @spec gutter markers
- [x] 2.1 Remove the `SpecRefLineMarkerProvider` `codeInsight.lineMarkerProvider` registration in `plugin.xml` (kept the sibling `OpenSpecLineMarkerProvider`). Also removed the two plugin-description bullets (Coverage panel, Gutter markers) and the `SpecCoverageService` `projectService` registration.
- [x] 2.2 Delete `editor/SpecRefLineMarkerProvider.java`.
- [x] 2.3 N/A — `SpecRefLineMarkerProviderTest` was A's; not present on this branch.

## 3. Scrub residual references
- [x] 3.1 N/A — `OpenSpecFileUtil` has no `@spec` mention on this branch (that comment was an A change).
- [x] 3.2 `grep -rn "SpecCoverage\|SpecRefLineMarker\|@spec\|CoverageResult" src/` returns nothing.

## 4. Docs
- [x] 4.1 Removed `@spec`/Coverage-tab/gutter descriptions from `docs/getting-started-browser.md`, `docs/feature-reference.md`, `docs/marketplace-page.md`, `docs/feature-comparison-matrix.md`, `docs/getting-started-api.md`, `docs/getting-started-cli-companion.md`, `README.md`, `CONTRIBUTING.md`. (Kept generic spec line-markers + the Verify action; reworded Verify "coverage" → "task progress".)
- [x] 4.2 CHANGELOG Removed entry points users at OpenSpec's `verify-change` workflow for completeness.
- [x] 4.3 `CHANGELOG.md` (unreleased v0.3.0): added the "Removed" entry. (No "#18 fixed" line on `main` to replace — that was only on the abandoned A branch.)

## 5. Build & validate
- [x] 5.1 `./gradlew test` — BUILD SUCCESSFUL; full suite green (no feature tests existed on this branch to delete).
- [~] 5.2 `./gradlew test` compiled + jarred the plugin (instrumentCode/jar/composedJar) green. A manual runtime "tool window has no Coverage tab" check was NOT performed.
- [x] 5.3 `openspec validate remove-spec-code-annotations --strict` — green.

## 6. Supersede the in-flight fix & close the loop
- [x] 6.1 Do this removal on a fresh branch off `main` — DONE (`change/remove-spec-code-annotations`). Still pending: delete the abandoned `change/fix-coverage-language-detection` branch (do not merge or archive it; unreleased, nothing regresses).
- [ ] 6.2 Post the vendor-neutral GitHub #18 reply — see design.md → "GitHub #18 response". Note: no maintainer comment exists yet (we did NOT prematurely reply); OP @j0bro (Kotlin) + @ikaven1024 (Go) are waiting, and @ikaven1024 offered a PR — respond before they invest effort on a feature being removed.
- [ ] 6.3 Mirror to internal trackers via `/mirror-change-trackers remove-spec-code-annotations`.
## 1. Coverage scan — language-agnostic

- [x] 1.1 In `SpecCoverageService.scanSourceFiles()`, replace the `.java`-only gate (`!"java".equals(file.getExtension())`) with: skip directories and skip RECOGNIZED binaries only — `fileType.isBinary() && fileType != UnknownFileType.INSTANCE`. Scan all other files yielded by `ProjectFileIndex.iterateContent`. Do NOT add an `isInSourceContent` restriction (would re-break projects without configured source roots), and do NOT use a plain `isBinary()` (UnknownFileType reports binary, so it would skip languages whose plugin isn't installed).
- [x] 1.2 Confirm the existing `@spec` regex and reference-keying logic are unchanged; only the file filter changes.

## 2. Gutter markers — language-agnostic

- [x] 2.1 In `src/main/resources/META-INF/plugin.xml`, change the `SpecRefLineMarkerProvider` registration from `language="JAVA"` to `language=""` (matching `OpenSpecLineMarkerProvider`).
- [x] 2.2 Update the `SpecRefLineMarkerProvider` class Javadoc that says "Java source files" to reflect any-language comments.

## 3. Tests — full coverage

- [x] 3.1 Add `src/test/java/com/johnnyblabs/openspec/integration/SpecCoverageServiceTest.java` (first tests for this service), extending `OpenSpecIntegrationTestBase`.
- [x] 3.2 Java baseline (regression): `.java` file with `// @spec actions:Init Action` → Init Action covered.
- [x] 3.3 Kotlin: `.kt` file `// @spec` → covered (core fix).
- [x] 3.4 Go: `.go` file `// @spec` → covered.
- [x] 3.5 Python: `.py` file `# @spec` → covered (different comment syntax still matches).
- [x] 3.6 Mixed-language project: refs to both requirements across `.kt`/`.go` → both covered, coveredReqs == totalReqs.
- [x] 3.7 Uncovered requirement: only one of two requirements referenced → the other reported uncovered; counts correct.
- [x] 3.8 Recognized-binary skip: `@spec` present only inside a `.zip`/binary file → that requirement stays uncovered.
- [x] 3.9 Multiple files referencing the same requirement → its refs list has both paths.
- [x] 3.10 Reference to a non-existent `domain:requirement` → no crash, coverage unaffected.
- [x] 3.11 No references anywhere → coveredReqs == 0, totalReqs == 2.
- [x] 3.12 Extra whitespace around `@spec` token still matches; `getCachedResult()` is null before compute and equals the result after.
- [x] 3.13 Add `src/test/java/com/johnnyblabs/openspec/integration/SpecRefLineMarkerProviderTest.java`: provider returns a marker for an `@spec` comment regardless of language (non-`PsiComment` element → null; comment without `@spec` → null; comment with `@spec` → marker on the element).

## 4. Specs & docs

- [ ] 4.1 Sync the `editor` delta into `openspec/specs/editor/spec.md` (both modified requirements) — at archive time via the sync flow.
- [x] 4.2 Update `docs/feature-reference.md` and `CONTRIBUTING.md` where the `@spec` convention is described to state it works in any language's comments, not only Java.
- [x] 4.3 Add a `CHANGELOG.md` entry under v0.3.0 → Fixed (user-facing): coverage and gutter markers now work in non-Java languages.

## 5. Verify

- [x] 5.1 `./gradlew test` — all green, including the new `SpecCoverageServiceTest`.
- [x] 5.2 `openspec validate fix-coverage-language-detection --strict` — green.
- [ ] 5.3 Manual sanity (optional): open a Kotlin/Go sample with an `@spec` comment, confirm the gutter icon appears and the Coverage tab reports > 0%.

## 6. Ship & track

- [ ] 6.1 Land as a branch + PR on the Forgejo `origin` remote, then push the same branch to GitHub and open the public PR linked with `Closes #18`; sync `main` back to `origin` after merge.
- [ ] 6.2 Post the agreed reply on GitHub #18 (vendor-neutral — no internal tracker references) confirming the diagnosis, the language-agnostic fix, and inviting @ikaven1024 to review/test on Go.
- [ ] 6.3 Mirror to internal trackers via `/mirror-change-trackers fix-coverage-language-detection`; record IDs in the gitignored `.tracking.yaml` sidecar.
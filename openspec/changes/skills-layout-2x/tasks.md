# Tasks

## 1. Captured data

- [x] 1.1 Write `scripts/capture-released-fingerprints.py`. It uses `scripts/lib/openspec_capture.py` to extract `RELEASED_GENERATED_FINGERPRINTS` from the 2.1.1 package into `src/main/resources/openspec/released-skill-fingerprints.txt`, with a header naming the source version. Document it in the fixtures README table.
- [x] 1.2 Add `ReleasedSkillFingerprints`, which loads lazily and ports `normalizeGeneratedContent`, plus `ReleasedSkillFingerprintsContractTest`. Real captured skill text must be a member, a one-character edit must not be, and the normalization cases (BOM, CRLF, `generatedBy` version) must match upstream.
- [x] 1.4 Add two detection cases to `scripts/capture-ai-tools-fixtures.py` that keep full `SKILL.md` text: `agents-unmarked-from-1.17` and `codex-unmarked-from-1.17`. Recapture 2.0.0, 2.1.0 and 2.1.1 `detect/`, and update `CASE_COUNT`. Record 2.1.0's fingerprint `set` digest with `--vectors-only`.
- [x] 1.3 Add `SkillLinkDirs`: the link folder per 2.x line (`V2_0`, `V2_1`) per tool, as captured in `effective.linkDir`. Add a contract test that the table equals every 2.x `ai-tools.json` `effective` block for the tools the plugin knows, and another that every 2.x `workflow-invocations.json` `linkPath` equals `SkillLinkDirs.linkPath`.

## 2. Tool list and folder shapes

- [x] 2.1 Add `SkillToolList`: the multi-line parse, `own-files`, upstream's id aliases, unknown ids skipped, and the 1.x one-line form.
- [x] 2.2 Add `OpenSpecTargetListContractTest`, which parses every captured `.openspec-target` in the 1.x and 2.x detect fixtures, plus unit cases for CRLF, blank lines, aliases and unknown ids.
- [x] 2.3 Add `SkillSpot.classify` (the classifier lives beside `SkillLocator`), using `NOFOLLOW_LINKS` with `isSymbolicLink() || isOther()`, for the LINK, COPY, RELEASED, BROKEN, USER and MISSING shapes. Port upstream's `linkPointsTo`: normalize `\` to `/`, compare literal and real paths, then compare the real paths of the spot and the target. Ignore OS junk files when deciding COPY.
- [x] 2.5 Write `scripts/capture-skill-spot-verdicts.py`, which runs upstream's `classifySkillLinkSpot` over each constructed shape listed in design Decision 8 into `<v>/skill-spots/`. Document it in the fixtures README.
- [x] 2.4 Add `SkillFolderShapeTest` covering every row of the host table in design Decision 5, asserting each captured `skill-spots/` case through the stated plugin-to-upstream mapping. Add the shared `SymlinkProbe` (a directory link in `@TempDir`; on a non-Windows host it fails instead of skipping; result reported through `TestReporter`). COPY, COPY beside OS junk, RELEASED with `/` and with `\` targets, RELEASED pointing elsewhere (USER), MISSING and an empty folder run unconditionally on every host. LINK, BROKEN and a link through a linked project root run wherever a symlink probe succeeds, skipped with `assumeTrue` and a named reason otherwise. Add a macOS `/var` vs `/private/var` real-path case.
## 3. Detection by release line

- [x] 3.1 Add `DetectionRules` (`V1`, `V2_0`, `V2_1`) with `forVersion` and the from-disk fallback, and unit-test both.
- [x] 3.2 Port the 2.x `getAvailableTools` into `AiToolDetector`: the shared-path filter, list-aware own-skills check, `isListedToolPresent`, `hasSkillLinks`, pre-list inference gated on released content, and `skills-lock.json` inference from 2.1.1. The global-dir switch is per line. Leave the 1.x path unchanged.
- [x] 3.3 Make `AiToolDetectionService` pick the rules from the detected CLI, or from disk when no CLI is detected.
- [x] 3.4 Make `AiToolDetectionContractTest` replay with each version's rules. Remove the `skills-layout-2x` detect entries from `KnownGaps`, and keep the `openspec-target-2x` and `tool-registry-2x` ones.

## 4. Lookup and home directory

- [x] 4.0 Add `HomeDirectory.resolve(env, props)`, which tries `USERPROFILE`, then `HOME`, then `user.home`, skipping blanks, as upstream does. Move `AiToolDetectionService.home()` onto it, and add `HomeDirectoryTest` for the order and the blank values.
- [x] 4.1 Add the `GlobalSkillsCache` project service: snapshot, pooled `ensureLoaded`, `invalidate`, and an injectable home resolved through `HomeDirectory`. Register it in `plugin.xml`, and have it subscribe to `ApplicationActivationListener.TOPIC` on the application bus through its own connection. Invalidate it on completion of the plugin's `init`/`update` runs.
- [x] 4.2 Add `SkillLocator.find` (shared → link → former own → legacy → home shared → home link), and switch `WorkflowInvocation.find` and `ExplorePromptService` to it. Keep `AiToolRegistry.skillPath` as the path OpenSpec writes, and update its javadoc.
- [x] 4.3 Make `WorkflowInvocationContractTest` materialize `linkPath` before the lookup in `SYMLINK` (where probed), `COPY` and `LINK_TEXT` modes, with the mode in each display name. Remove the `invocation` entries from `KnownGaps`.
- [x] 4.4 Add `GlobalScopeLookupTest` (injected home only, plus an EDT case that returns without touching disk) and `SkillPathResolutionContractTest` on `HeavyPlatformTestCase` (Cursor, Copilot, Claude via link, 1.x copy, home-only). Add a platform test that application-bus activation clears the cache.

## 5. VFS and watcher

- [x] 5.1 After every plugin-run `init`/`update` (one hook in `CliRunner`, `SkillsWritten.afterRun`, which covers `ScaffoldingService`, `ManageAiToolsDialog` and the other call sites), call `refreshNioFiles` recursively on `.agents/skills` and each detected tool's `skills` folder, never on individual spots. Test on the platform tier that a linked skill is visible through VFS after the refresh, and that a RELEASED file replaced on disk by a COPY folder (what Windows `update` does) appears in VFS as a folder holding `SKILL.md`.
- [x] 5.2 Make the artifact watcher key `.agents` on its real path, resolved off the EDT. Check that `.agents` is not excluded, and add a test for each.

## 6. Verification

- [x] 6.1 Run each mutation check listed in design Decision 8, one at a time. Confirm each turns the named cases red, then revert it.
- [ ] 6.2 Run `./gradlew build`, `./gradlew verifyPlugin` (new platform APIs), and `openspec validate --all --strict`. Rerun the existing uiSmoke suite and the screenshot tour under `caffeinate -dimsu`, since the handoff now reads skills differently. Ratchet the coverage floor in `build.gradle.kts`.
- [ ] 6.3 Host coverage: the macOS, Linux and Windows CI matrix runs green, and each host's test report shows the shape tests that apply to it ran. On Windows the COPY and RELEASED cases must have run; skipped link cases must show the probe reason.
- [x] 6.4 Docs: update `docs/feature-reference.md` (detection and lookup on 2.x), the README's CLI support line if it changes, and a CHANGELOG `## Unreleased` entry for users. Keep all of it vendor-neutral.

# Tasks

## 1. Captures

- [x] 1.1 `capture-archive-fixtures.py`: run a scratch-installed release (the shared `scripts/lib` installer); add `new-cap-purpose-bom`, `new-cap-purpose-front-matter` and `main-front-matter-purpose` (the last also on 1.14.3). Capture 2.1.0 and 2.1.1 (new-capability cases first; then each case whose output a 2.x merge could differ on), and record the runs in the fixtures README.

## 2. Merge

- [x] 2.1 `ValidationRelease.NEW_SPEC_LAYOUT = "2.1.1"`; `SpecMerge.merge` takes the release (design decisions 1 and 2), with the existing overloads merging as the releases before 2.1.1 (front-matter blanking gated too, per the captures).
- [x] 2.2 From 2.1.1, `skeleton` writes the blank line after `## Purpose`, and `purposeSection` drops a byte-order mark and skips the delta's front matter (design decision 3).
- [x] 2.3 `SpecSyncService` passes `ValidationRelease.followed(project)`, for Sync Specs and the plugin's archive.

## 3. Tests

- [x] 3.1 `ArchiveMergeContractTest` replays 1.14.3, 2.1.0 and 2.1.1, each under its own release, with no case skipped.
- [x] 3.2 Focused tests: the same new-capability delta on 2.1.0 and 2.1.1 gives each layout; no CLI follows the target release; an existing main spec is unchanged on both.
- [x] 3.3 Mutations, each turning a test red:
  - drop the release gate either way;
  - drop the blank line after `## Purpose`;
  - skip the delta's front-matter blanking in the merge;
  - skip the front-matter blanking in the Purpose reader (caught only by `main-front-matter-purpose`);
  - Sync passes no release.

  The byte-order mark is dropped by `FrontMatter.blank` itself, so an explicit strip was dead code and was removed; the `new-cap-purpose-bom` captures pin the behaviour on both sides.

## 4. Docs and finish

- [x] 4.1 CHANGELOG `## Unreleased`. Verify: `DocumentationHygieneTest` and the vendor-neutral grep.
- [x] 4.2 Final checks, with the outcome recorded here:
  - `./gradlew build`;
  - `openspec validate --all --strict`;
  - test-engineer AUDIT on the diff, with findings fixed or recorded.

  Outcome:
  - `./gradlew build`: green.
  - `openspec validate --all --strict`: 39 passed.
  - Audit (pass-with-nits), all fixed:
    - The CHANGELOG's earlier front-matter line now says it applies from 2.1.1.
    - The front-matter merge test replays the captured delta under 2.1.1 and checks 2.1.0 refuses it. Its mutation (skip the merge-level blank) now fails it.
    - The no-CLI leg fakes an unavailable CLI and compares with the captured 2.1.0 output, with a guard that names the fixture to switch to when the target reaches 2.1.1. `followed()` returning null instead of the target is caught by `ValidationReleaseTest`.
    - The mutation list above matches what was run.

## Workflow follow-up

- The capability-retirement port is filed as its own tracker entry.
- Merge by rebase, archive with spec sync, close the tracker entries.

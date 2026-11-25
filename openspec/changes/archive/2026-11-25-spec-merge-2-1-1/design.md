# Design

## Context

- **Upstream's change, 2.1.0 → 2.1.1** (`dist/core/specs-apply.js`, read from both published packages):
  - `buildSpecSkeleton` returns `# <cap> Specification\n\n## Purpose\n\n<body>\n\n## Requirements\n`. 1.17.0, 2.0.0 and 2.1.0 return the same without the blank line after `## Purpose`.
  - `extractPurposeSection` strips a leading byte-order mark and blanks the delta's front matter (`findFrontmatterEnd`) before looking for `## Purpose`. Earlier releases do neither.
  - The other two 2.1.1 changes don't apply here. The front-matter warning was handled by validator-tasks-parity-2x. The raw-HTML audit (upstream #2211) only feeds capability retirement, which the plugin doesn't port (see Non-Goals).
- **The plugin today.** `SpecMerge.skeleton` writes the pre-2.1.1 layout. `SpecMerge.purposeSection` neither strips a byte-order mark nor skips front matter. `SpecMerge.merge` has no release input. `SpecSyncService` is its production caller, for Sync Specs and for the plugin's own archive (no CLI, or a CLI below 1.5).
- **The release seam already exists.** `ValidationRelease.followed(project)` gives the detected CLI's version, or the plugin's target release with none. `ValidationRelease.has(release, since)` gates a rule on it.
- **Captures.** `fixtures/cli/archive-1.14.3/` holds 56 archive cases, replayed by `ArchiveMergeContractTest`. There are no 2.x archive captures.

## Goals / Non-Goals

**Goals:**
- A new main spec equals the followed release's `openspec archive` output on every captured case: 1.14.3, 2.1.0 and 2.1.1.
- Releases before 2.1.1 behave exactly as today.

**Non-Goals:**
- Retiring a capability (deleting its main spec under `retire_capabilities`), with or without 2.1.1's audit. It's a missing feature on every release since at least 1.17, so it gets its own tracker entry.
- Changing how existing main specs are merged.

## Decisions

### 1. One release constant, in the existing seam

Add `ValidationRelease.NEW_SPEC_LAYOUT = "2.1.1"`, beside the 2.1.1 validation constants, rather than a second release class. The name says what it gates. The seam's "a release that can't be read satisfies no rule" keeps an unreadable version on today's behaviour.

### 2. `SpecMerge.merge` takes the release

Add a `@Nullable String release` parameter. The existing overloads pass `null`, which merges as the releases before 2.1.1 do. That differs from today in one way: the merge used to blank a delta's front matter on every release, but the 1.14.3 and 2.1.0 captures show those releases don't (they refuse a delta whose front matter hides its sections), so blanking is gated on 2.1.1 too, with 2.1.1's warning. `skeleton` and `purposeSection` take a boolean for the 2.1.1 behaviour. `SpecSyncService` passes `ValidationRelease.followed(project)`, read once per sync.

### 3. Front matter is found the way the plugin already finds it

From 2.1.1, `purposeSection` reads through `FrontMatter.blank` (added by validator-tasks-parity-2x), which drops a byte-order mark and then blanks the front matter, upstream's order. This applies to the delta's Purpose and to an existing main spec's: the `main-front-matter-purpose` capture shows 2.1.1 finding a main spec's Purpose past front matter that hides it from earlier releases.

### 4. Captures from scratch-installed releases

`capture-archive-fixtures.py` gains `CAPTURE_VERSION`, which runs a release from the shared `scripts/lib` scratch installer, never a pinned `npx`. Cases added: `new-cap-purpose-bom`, `new-cap-purpose-front-matter` (front matter holding a `<!--` and a fence above the `## Purpose`) and `main-front-matter-purpose` (the same front matter in the main spec). 2.1.0 and 2.1.1 capture every case, since the contract test compares refusal and warning wording too; 1.14.3 gains the three new cases through `CAPTURE_CASES`.

## Risks / Trade-offs

- **2.1.0's verdict on the new cases is unknown until captured.** Front matter in a delta is 2.1.1's addition, so 2.1.0 may refuse it, carry it, or read the Purpose differently. → The spec defers to the capture for releases before 2.1.1, and the plugin follows whatever 2.1.0 really writes.
- **A spec created on 2.1.1 differs by one blank line from one created on 2.1.0.** → That is the CLI's own behaviour. Neither release's validator cares.

## Migration Plan

None. Rollback is reverting the change.

## Open Questions

None.

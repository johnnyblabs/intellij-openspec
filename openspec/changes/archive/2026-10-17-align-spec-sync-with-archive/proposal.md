## Why

The plugin merges a change's delta specs into the main specs itself, for its Sync action, its archive preview, Bulk Archive, and archiving without the CLI. That merge has drifted from what `openspec archive` does, in ways that **corrupt main specs without warning**. An upstream release-notes audit found this, and it was confirmed against real CLI 1.14.3 runs:

- **Fenced content is parsed as structure.** A delta that shows OpenSpec syntax inside a code fence produces a phantom requirement. A main requirement whose example contains `## Notes` gets its block cut short, so the merge leaves the old tail behind, joins a fence line onto a heading, and duplicates a scenario. Upstream has masked fences since 1.7.0. This repo's own specs contain such examples.
- **It applies changes upstream refuses:**
  - an ADDED requirement whose name differs from an existing one only in case or spacing, which gives a duplicate;
  - a MODIFIED matched case-insensitively (upstream matches exactly);
  - a MODIFIED that drops a scenario;
  - a RENAMED `FROM:` with no `TO:`, which is silently skipped.
- **It isn't all-or-nothing.** A change that can't be fully applied is partly applied.
- **The archive folder doesn't match.** The plugin archives to `changes/archive/<name>` and creates `openspec/archive/` when `changes/archive/` is missing. Upstream archives to `changes/archive/YYYY-MM-DD-<name>` and refuses when that target exists. The plugin's own `workflow` spec already says the date-prefixed form.
- **New capabilities differ.** The plugin writes an empty `## Purpose`. Upstream writes a `TBD - created by archiving change …` placeholder, and uses the delta's own `## Purpose` when it has one.

The `spec-sync` spec also still describes the VFS refresh as `invokeLater` plus a latch, which was replaced in this release.

Tracked in the linked tracker entry.

## What Changes

The plugin's merge follows OpenSpec 1.14.3's archive rules, which are pinned by contract tests against captured real CLI archive runs.

- **Ignore fenced content:**
  - The rule: ```` ``` ```` and `~~~` fences at any indentation, closed by the same character at least as long; an unclosed fence runs to the end of the file.
  - Where it applies: every structural search in both deltas and main specs, including section headers, requirement headers, REMOVED and RENAMED entries, block ends and scenario headers.
- **Match requirement names exactly,** after the same normalization as upstream: strip a trailing closing-`#` run, then trim, with no case folding. Delta section titles still match case-insensitively.
- **Refuse what upstream refuses, all-or-nothing per change.** The refusals:
  - case or spacing near-misses for ADDED, REMOVED and RENAMED targets;
  - a MODIFIED target that isn't found;
  - a MODIFIED that drops a scenario;
  - an ADDED that already exists with different content;
  - a RENAMED that's unpaired, has no source, or whose target already exists;
  - duplicates within or across sections.

  A refused change writes nothing, and the preview shows the reason in upstream's wording and disables applying. A REMOVED whose target is already gone is a warning, as upstream treats it as already removed. Bulk Archive's gate now blocks on refusals only, not on warnings.
- **New capabilities** get upstream's exact skeleton, with the `TBD - created by archiving change <name>. Update Purpose after archive.` Purpose, replaced by the delta's own `## Purpose` when it has one.
- **Text outside requirements is preserved** the way upstream preserves it: everything above `## Requirements`, the preamble inside it, and later `##` sections.
- **Archive folder:** archive to `openspec/changes/archive/YYYY-MM-DD-<name>`.
  - The date is local. A name that already starts with a date is kept as is.
  - It refuses before writing any spec when the target exists, and creates `changes/archive/` when missing, never `openspec/archive/`.
  - An existing legacy `openspec/archive/` is still read.
- **Spec fix:** `spec-sync` describes the current `invokeAndWait`-with-modality refresh.
- **Out of scope:** handing the archive write to the CLI when it's available. That's planned as a separate change, which will build on this engine for the preview and the CLI-absent fallback.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `spec-sync`:
  - sync application uses exact name matching, is all-or-nothing, builds new capabilities with upstream's skeleton, and refreshes with `invokeAndWait` plus a modality;
  - new requirements say fenced content is never structure, and that sync refuses what archive refuses.
- `workflow`: a new requirement for the archive target directory: dated name, refuse an existing target, never create `openspec/archive/`.

## Impact

- **Code:**
  - `SpecSyncService`: fence masking, exact matching, refusals, all-or-nothing, the new-capability skeleton, and text preservation.
  - `SpecSyncResult`: refusals kept separate from warnings.
  - The sync preview dialog: shows refusals and disables applying.
  - `BulkArchiveDialog`: the gate uses refusals.
  - `ChangeService.archiveChange`: dated target, collision refusal, no `openspec/archive/` creation.
- **Tests/fixtures:**
  - `fixtures/cli/archive-1.14.3/<case>/`, real `openspec archive` runs per case, each with the main spec before and after, the delta, and the output;
  - contract tests asserting the plugin's merged result matches upstream's main spec after archiving, or that both refuse;
  - unit tests for fence masking and name normalization.
- **Docs:** CHANGELOG `### Fixed`, plus the `docs/feature-reference.md` Sync and Archive entries.
- **Platforms:** no change to IntelliJ 2024.2+ compatibility.

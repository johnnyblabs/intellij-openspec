## Context

`BuiltInValidator` validates delta files section by section with line regexes:
- **Renames:** RENAMED uses `SpecPatterns.RENAMED_PAIR`, which accepts any text after `FROM:`/`TO:`, bare names included. `delta-renamed-fields` fires when entry lines exist but no pair matches.
- **The "no deltas" rule:** the change-level `delta-none-found` rule counts delta `spec.md` files.
- **Missing check:** nothing checks whether a file's sections yielded any entry.

`DeltaSpecInspection` mirrors the RENAMED check in the editor.

Probes of real CLIs, from `validate --json` with isolated `HOME`/`XDG_*`:

| Delta | 1.3.0 | 1.14.3 | Built-in today |
|---|---|---|---|
| bare rename only | invalid: no entries parsed + at least one delta | same | valid |
| bare rename beside a valid ADDED | valid (bare lines ignored) | valid | valid |
| header-form `FROM:` without `TO:`, alone | invalid: no entries + at least one delta | invalid: unpaired + no entries + at least one delta | invalid |
| header-form `FROM:` without `TO:`, beside ADDED | valid | invalid: unpaired | invalid |

## Goals / Non-Goals

**Goals:** the built-in verdict on these shapes matches CLI 1.14.3, and is never stricter than 1.3.0 except where 1.13.1+ itself refuses (unpaired renames, already the case today); the validator, the inspection and the merge read entries the same way.

**Non-Goals:**
- Other validation rules.
- Hints for ignored bare lines: upstream is silent, so the plugin adds none.

## Decisions

### D1. One reader for entries

`SpecMerge` exposes `public static DeltaSummary summarize(String deltaContent)`. It returns the delta sections present (in OpenSpec's `## <TITLE> Requirements` spelling), the entry count (ADDED + MODIFIED blocks, REMOVED names, RENAMED pairs), and the unpaired renames with their side, name and line. The validator and the inspection call it; neither keeps its own rename regex. `SpecPatterns.RENAMED_PAIR` is removed if nothing else reads it. `RENAMED_ENTRY_LINE` stays if its "an empty section is fine" use remains.

### D2. Rules

- **`delta-no-entries` (ERROR, new).** For a delta file with at least one delta section and an entry count of zero. Upstream's message, with its section list joined "A, B and C", anchored to the first section header. It does not stack with `delta-spec-sections`, which remains for a file with no delta sections at all.
- **`delta-renamed-fields` (ERROR).** One per unpaired rename, with upstream's message, anchored to that line. Bare lines produce nothing.
- **`delta-none-found` (ERROR).** For a specs-requiring, non-`skip_specs` change whose delta files hold zero entries in total. Misplaced deltas still suppress it, as today.

### D3. Older-CLI policy

The unpaired-beside-ADDED shape is refused from 1.13.1 and accepted by 1.3.0–1.13.0. The built-in validator already refuses it and targets 1.14.3, so that stays, recorded here. Every other change in this design either makes the validator accept something it rejected or reject what every supported CLI rejects.

## Risks / Trade-offs

- **Users relying on bare renames will see errors** without the CLI. → Every supported CLI already rejects those changes; `validate` and `archive` fail on them today.
- **The validator now depends on `SpecMerge`'s parser.** → That's intended: one reader, so validation, preview and archive can't disagree again.

## Migration Plan

Code-only. To roll back, revert the commit.

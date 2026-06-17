## Why

The upstream OpenSpec CLI recognizes **four** delta-spec section types — `ADDED`, `MODIFIED`, `REMOVED`, and `RENAMED` (`@fission-ai/openspec/dist/core/specs-apply.js`). The plugin's built-in validator and IDE inspection only recognize the first three: a delta file containing `## RENAMED Requirements` triggers a false-positive `delta-spec-sections` warning, even when the section is well-formed. Same architectural lesson as `add-openspec-1-3-tool-support` and the `secure-gemini-auth-header` follow-up — the plugin should never be more restrictive than upstream on an upstream-defined contract.

A quirk worth noting: the plugin's **sync layer is already RENAMED-aware**. `SpecSyncService` parses, sorts, and applies `RENAMED` operations via `FROM:/TO:` regex; `DeltaSpecOperation` has the enum value; `openspec/specs/spec-sync/spec.md` documents the parsing and apply scenarios. The gap is purely in the validation/inspection/scaffolding surface plus an apply-order asymmetry (the plugin sorts `REMOVED → RENAMED → …` while upstream sorts `RENAMED → REMOVED → …`).

## What Changes

- **Validator** (`BuiltInValidator.java`): teach the section regex about `RENAMED`, update the warning text, and add a per-section structural check that verifies `RENAMED` sections contain ≥1 well-formed `FROM:`/`TO:` pair. New rule code: `delta-renamed-fields`.
- **IDE inspection** (`DeltaSpecInspection.java`): mirror the regex, message, and structural check so the live editor highlights RENAMED problems inline.
- **Scaffolding template** (`TemplateProvider.deltaSpecTemplate`): append a `## RENAMED Requirements` example block so new delta specs discover the section type.
- **Apply-order parity** (`SpecSyncService.sortOperations`): re-sort to upstream order `RENAMED → REMOVED → MODIFIED → ADDED`. The current `REMOVED → RENAMED → …` order produced the same end-state for our agent-driven sync flow but diverged from CLI behavior; aligning eliminates the subtle drift so future programmatic sync stays in lockstep with the CLI.
- **Spec text**: update `validation/spec.md`'s "Delta spec validation" requirement to cite four section types and add scenarios for the FROM/TO check. Update `spec-sync/spec.md`'s apply-order scenario to reflect the new sort.
- **Tests**: extend `BuiltInValidatorRulesTest` with three RENAMED cases (valid pass, missing-pair error, mixed-section pass) and update `SpecSyncServiceTest.sortOperations_ordersCorrectly` to expect the new order.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `validation`: the "Delta spec validation" requirement gains RENAMED awareness and a FROM/TO structural check. "Delta spec IDE inspection" inherits the change via the inspection mirror — no requirement-level rewrite needed.
- `spec-sync`: the apply-order scenario realigns from `REMOVED → RENAMED → MODIFIED → ADDED` to `RENAMED → REMOVED → MODIFIED → ADDED`.

## Impact

- **User-visible**: delta specs with `## RENAMED Requirements` no longer trigger the false-positive `delta-spec-sections` warning. A malformed RENAMED section (missing FROM/TO) now produces a clear error (`delta-renamed-fields`) instead of being silently accepted as "no requirement blocks here". Scaffolded delta specs now include a RENAMED example.
- **Code**: regex updates in two files, a small new RENAMED branch in each, one apply-order constant tweak in `SpecSyncService`, and a template extension.
- **Specs**: delta spec under `validation` (MODIFIED) and `spec-sync` (MODIFIED). No change to other capability specs.
- **Tests**: three new tests in `BuiltInValidatorRulesTest`, one updated test in `SpecSyncServiceTest`, one updated template test in `TemplateProviderTest`. The sync layer's RENAMED parse/apply tests in `SpecSyncServiceTest` already pass and need no changes (only the sort-order test moves).
- **Compatibility**: pure additive at the validation surface — existing 3-section delta specs continue to validate identically. The sort-order change has no behavioral effect on the current agent-driven sync (delta specs that target distinct requirements per section), but matches upstream so programmatic sync futures stay aligned.
- **Trackers**: existing tracker entries cover this work; IDs live in the gitignored `.tracking.yaml` sidecar. `mirror-change-trackers` is **not** re-run.
- **Deferred / out of scope**:
  - **Propose-skill RENAMED mention**: `.claude/skills/openspec-propose/SKILL.md` (and the `.augment/`, `.gemini/`, `.github/` mirrors) are upstream-managed by the OpenSpec CLI; editing them locally is overwritten by `openspec update`. Not pursued in this change — `openspec-sync-specs/SKILL.md` already documents RENAMED, so the gap in the propose skill is cosmetic (the LLM agents using these skills can still generate well-formed RENAMED sections by following the sync skill's reference). Revisit if it surfaces as a real friction.
  - **Cross-section FROM-collision detection**: upstream rejects RENAMED entries whose FROM names collide with names in another section (e.g. ADDED + RENAMED FROM same name). Worth replicating eventually — see `design.md`.

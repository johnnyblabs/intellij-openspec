## Context

The plugin's delta-spec recognition was originally written against a three-section contract: `ADDED`, `MODIFIED`, `REMOVED`. Upstream OpenSpec CLI added `RENAMED` as a fourth section type (with a per-entry `FROM:`/`TO:` payload) some time ago — see `@fission-ai/openspec/dist/core/specs-apply.js` lines 95-180 for parse-and-apply behavior. The plugin's sync layer caught up: `SpecSyncService.java:36-41,115-119,151-161,305-314,377…` already parses RENAMED sections, normalizes the `FROM:`/`TO:` regex against bullet and non-bullet variants, and applies the operation via `applyRenamed`. The validator and IDE inspection did not catch up, leaving a documented contract gap and a false-positive warning whenever a delta uses `## RENAMED Requirements`.

A secondary mismatch surfaced during exploration: `SpecSyncService.sortOperations` sorts `REMOVED → RENAMED → MODIFIED → ADDED`, but upstream sorts `RENAMED → REMOVED → MODIFIED → ADDED`. Today this is invisible because every operation in a well-formed delta targets a distinct requirement name, so order-of-application produces identical results. It becomes load-bearing the moment the same requirement name appears in two sections (e.g. RENAMED FROM a name that ADDED in the same delta — an upstream-rejected pattern). Aligning the sort eliminates a subtle divergence that would otherwise need to be reasoned about every time someone touches sync.

## Goals / Non-Goals

**Goals:**
- Close the validator/inspection RENAMED gap so the plugin agrees with the upstream four-section contract.
- Add a structural check that catches malformed RENAMED sections (no `FROM:`/`TO:` pair) instead of silently treating them as "no requirements".
- Realign sort order to match upstream so the plugin's sync behavior tracks the CLI deterministically.
- Update the scaffolded delta-spec template so new contributors discover RENAMED as a first-class section type.

**Non-Goals:**
- Implementing cross-section FROM-collision detection (e.g. "ADDED requirement 'Foo' and RENAMED FROM 'Foo'"). Upstream rejects this; the plugin currently accepts it. Tracked as a follow-up — see D3 below.
- Editing the upstream-managed `openspec-propose` skill files locally. Upstream CLI overwrites them on `openspec update`. The propose skill's silence on RENAMED is a cosmetic gap — `openspec-sync-specs/SKILL.md` already documents the FROM:/TO: format, so LLM agents can synthesize correct RENAMED sections by following the sync skill. Revisit upstream only if the gap turns into observed friction.
- Refactoring the validator's regex-based block walker into an AST-aware parser. Long-discussed in the backlog; out of scope for a narrow contract-alignment change.
- Quick-fix support for malformed RENAMED sections. The existing `CopyRequirementFromMainSpec` quick-fix is MODIFIED-specific and doesn't have an analogue for RENAMED. Worth considering once RENAMED appears in real specs — for now, an inline ERROR with a clear message is enough.

## Decisions

### D1. Reuse the SpecSyncService FROM/TO regex shape

`SpecSyncService.RENAMED_ENTRY` is `^\s*(?:-\s*)?FROM:\s*(.+)$\s*^\s*(?:-\s*)?TO:\s*(.+)$` — it matches both bullet (`- FROM: x`) and non-bullet (`FROM: x`) variants and is already covered by `SpecSyncServiceTest.parsesRenamedSection` + `parsesRenamedWithBulletFormat`. The validator and inspection adopt the same regex shape (inlined as a private constant in each rather than imported, to keep these classes independent of the services package). New rule code: `delta-renamed-fields`. Severity: ERROR — same level as `delta-removed-fields`, since a malformed RENAMED section can't be applied.

**Alternative considered**: extract `RENAMED_ENTRY` to a shared constant. Rejected — three call sites, low churn risk, and reaching across the package boundary would create a `validation → services` dependency that doesn't otherwise exist. The regex is small and stable.

### D2. Realign sort order to RENAMED → REMOVED → MODIFIED → ADDED

Upstream order. The current order produces identical results for well-formed deltas (every op targets a distinct name), so the change is theoretically a no-op for today's traffic. The reason to do it now is to eliminate a divergence that would need to be reasoned about whenever sync evolves. Switch the priority switch in `SpecSyncService.sortOperations` and update the `sortOperations_ordersCorrectly` test. No spec wording changes in the new requirement; the existing "Scenario: Apply order" scenario in `openspec/specs/spec-sync/spec.md` gets a MODIFIED.

**Alternative considered**: leave sort as-is and call out the divergence in a code comment. Rejected — comments rot; aligning the implementation is cheaper and removes the question.

### D3. FROM-collision detection: deferred

Upstream rejects a delta containing both `## ADDED Requirements\n### Requirement: Foo` and `## RENAMED Requirements\nFROM: Bar\nTO: Foo` (the new ADDED name collides with the RENAMED TO name) — and similar across MODIFIED/REMOVED. The plugin currently doesn't flag this. Implementing it requires two passes over the delta (first to collect all reachable names, then to detect collisions), which is a bigger structural change than a regex update. Defer until RENAMED appears in real deltas often enough for the collision pattern to be a realistic mistake. Tracked as future work in `tasks.md`'s "Follow-up" section.

### D4. Inline a structural example in the scaffolding template

Adding `## RENAMED Requirements\n\n- FROM: Old name\n- TO: New name` to `TemplateProvider.deltaSpecTemplate()` follows the same pattern the template already uses for ADDED/MODIFIED/REMOVED. Discoverability beats minimalism — most contributors will never have seen a RENAMED section, and the template is the first place they look.

## Risks / Trade-offs

- **Risk**: the new `delta-renamed-fields` ERROR fires on existing delta specs that contain a `## RENAMED Requirements` heading with no entries (perhaps a stub the author meant to fill in later). → **Mitigation**: ERROR is the correct severity — a stub RENAMED section is not applyable. The author either fills it in or deletes the heading.
- **Risk**: the sort-order change breaks an undocumented downstream assumption in `applyOperations`. → **Mitigation**: the apply-order test (`sortOperations_ordersCorrectly`) and all parse/apply tests cover the realigned order. Worth a code-search for any place that assumes a specific operation order at apply time; none found during exploration.
- **Trade-off**: filing this as an OpenSpec change for a regex update + sort-priority tweak is somewhat heavy. Worth it because (a) the validator surface is part of the documented `validation` contract, (b) the sort-order alignment touches `spec-sync`'s scenario text, (c) tracker entries already exist for this work and need a paper trail to close.

## Migration Plan

1. Land this change in the plugin.
2. Plugin update reaches users via JetBrains Marketplace (CI on `v*` tag push).
3. Users with delta specs containing well-formed `## RENAMED Requirements` sections stop seeing the false-positive `delta-spec-sections` warning. Users with stub/malformed RENAMED sections see a new `delta-renamed-fields` ERROR on first re-validation — they fill in `FROM:`/`TO:` or delete the heading.
4. Rollback: revert the change commit; CI publishes the prior plugin version on next tag. No data migration concerns — the change is purely additive at the validation surface.

## Open Questions

- None blocking. The propose-skill upstream issue is a separate workstream and doesn't gate this change.

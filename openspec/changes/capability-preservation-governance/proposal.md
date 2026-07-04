## Why

A shipped, advertised capability — the 1.4 coordination IDE write actions, present and documented in v0.3.0/v0.3.1 — was **silently removed** during unrelated 1.5 work: no spec delta (the change declared "Modified Capabilities: None"), no changelog entry, and no test to catch it. It was found only by a later documentation audit.

The `cli-version-support-contract` change restored the capability and added a **coordination-scoped** per-version behavior contract + test. But the *class* of failure is not coordination-specific — **any** version-gated capability (schema management, store/workset, direct API) could drift the same way, because nothing plugin-wide governs it. Two links in the failure chain still have no spec:

1. **No plugin-wide capability-preservation guarantee.** Nothing states that the user-facing behavior the plugin delivers *for a supported CLI version* is a preserved contract.
2. **No changelog-disclosure requirement.** `changelog-automation` governs *how* the changelog is generated, not *that* user-facing capability changes must appear in it — so a silent removal passed.

The preservation guarantee must be **per-version and client-faithful**, or it would wrongly forbid correct behavior: the plugin *should* stop offering 1.4 coordination writes on a 1.5 CLI (the client removed those commands) — that is faithful mirroring, not a regression. So the rule preserves each supported version's capability set *bounded by what that version's client provides*, while still requiring that any change to a version's set be spec'd, disclosed, and tested — never silent.

## What Changes

- **`plugin-core`** gains a **"Supported CLI versions and capability preservation"** requirement: the plugin declares its supported CLI versions (floor `1.3.0`; the `1.3.x` / `1.4.x` / `1.5.x` lines) and treats each supported version's delivered, client-backed capabilities as a preserved contract. Removing, disabling, or re-gating such a capability for a version whose client still provides it requires a spec delta + changelog entry + test. Client-faithful, version-scoped differences (a capability present for one version and retired for a newer one because the client removed it) are explicitly *permitted* but still governed (spec + changelog + test), never silent. Per-version tests pin each version's set.
- **`changelog-automation`** gains a **"User-facing capability changes are disclosed"** requirement: adding, removing, or materially changing a user-facing capability — including retiring one for a supported version — SHALL be recorded in the changelog's unreleased section; a shipped capability SHALL NOT be removed silently.

## Capabilities

### Modified Capabilities
- `plugin-core`: adds the supported-version capability-preservation contract (plugin-wide generalization of the coordination-scoped CLI-version behavior contract).
- `changelog-automation`: adds the user-facing-capability-change disclosure requirement.

## Impact

- **Spec:** one ADDED requirement in each of `plugin-core` and `changelog-automation`.
- **Enforcement:** per the chosen model — spec + per-version tests + review. The per-version test pattern already exists (`CoordinationServiceWindowTest.perVersionBehaviorContract`); this change pins the *declared supported-version floor* with a small test so the "supported versions" declaration itself can't drift unnoticed, and relies on the established per-capability per-version test pattern + change review for the rest. No new CI machinery.
- **No runtime behavior change.** This is governance: it makes the guarantee that already held after the restore (each supported version's behavior is preserved and test-pinned) an explicit, plugin-wide spec so the coordination regression can't recur in a different capability.
- **Docs:** none required beyond the specs; the coverage-matrix currency angle is owned by the in-flight documentation-maintenance-framework change.

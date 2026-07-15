## Context

OpenSpec CLI 1.6.0 changed store-root health semantics without changing any JSON shapes: a fresh/config-only root now registers successfully, `store doctor` reports it `healthy: true` with per-directory `present: false` detail, the 1.5 diagnostic codes `openspec_{specs,changes,archive}_missing` were retired (only `*_not_directory` variants survive), and `store register` gained two refusal codes (`invalid_store_pointer`, `store_root_pointer_declared`). The plugin's store surface (`StoreEntry`, `CoordinationService` doctor/register parsing, `CoordinationPanel` rendering) was built and contract-tested against 1.5.0 fixtures. Both CLI generations must remain supported: existing users are on 1.5.x while npm `latest` installs 1.6.0.

Current state audited: the parsing layer reads `openspec_root.healthy` verbatim and the uniform `status[]` envelope generically (no `*_missing` code is special-cased in production source); the CLI-less fallback leaves doctor fields `null` (unknown) rather than computing health from disk; `CoordinationPanel` renders an error marker only when `openspecRootHealthy == false`. One contract test (`StoreWorksetWriteContractTest`) asserts the 1.5-only register refusal as if it were the general behavior.

## Goals / Non-Goals

**Goals:**
- Store-health presentation is correct under both CLI generations, with "healthy-empty" (1.6 fresh store) rendered as healthy.
- Register outcomes — 1.6 fresh-root success and the two new refusal codes — parse and surface correctly, with `fix` remediation verbatim.
- Contract coverage per CLI generation: new fixtures captured from the real 1.6.0 CLI under `src/test/resources/fixtures/cli/1.6.0/`; the existing 1.5.0 fixture set and its expectations are retained but re-scoped as explicitly 1.5-generation coverage.
- User docs describe the 1.6 store-health semantics.

**Non-Goals:**
- The full 1.6.0 fixture re-capture sweep across all commands (separately tracked).
- Any new UI affordance for creating the missing planning directories, or any "initialize this store" write action (not modeled by the plugin today; would be its own proposal).
- Changes to the store/workset write gating (the 1.5.0 CLI floor stands — 1.6 raises no floor).
- The 1.6 validator-parity, skills-surface, and archive-exit-code work (separately tracked).

## Decisions

1. **Health is read, never computed.** `openspecRootHealthy` continues to come solely from the doctor report's `healthy` flag; the plugin adds no plugin-side directory checks. This is why the 1.6 semantic change lands almost entirely in tests and presentation: the parser already trusts the CLI. Alternative rejected: mirroring the CLI's per-directory `present` detail into `StoreEntry` and deriving a tri-state (healthy/healthy-empty/unhealthy) — that duplicates upstream's judgment and would silently diverge again on the next upstream change. If an empty-state hint is shown at all, it is presentation-only and derived from the same doctor payload at render time.
2. **Per-generation fixtures, side by side.** New captures go to `fixtures/cli/1.6.0/` mirroring the `1.5.0/` naming (`store-register-fresh-root.json`, `store-doctor-healthy-empty.json`, `store-register-pointer-declared.json`, `store-register-invalid-pointer.json`). The 1.5.0 set is untouched; `StoreWorksetWriteContractTest`'s `store_register_root_unhealthy` expectation is renamed/scoped as 1.5-generation behavior rather than deleted, since 1.5 remains supported. Capture procedure per the contract-test discipline: real CLI 1.6.0, isolated `XDG_DATA_HOME`, sanitized paths.
3. **No refusal-code special-casing.** The two new register codes flow through the existing generic `status[]` → message/`fix` path. Contract tests pin that they parse and surface; production code changes only if the capture reveals the generic path mishandles them. This keeps the plugin resilient to codes appearing/disappearing across generations (the spec's "SHALL NOT special-case" clause).
4. **Presentation stays boolean at the marker level.** `CoordinationPanel` keeps rendering an error marker only for `openspecRootHealthy == false`. The healthy-empty scenario is pinned by a test asserting no error marker for a healthy-empty doctor payload, plus javadoc updates on `StoreEntry` (which currently documents 1.5-era assumptions).
5. **The end-to-end verification is an automated uiSmoke journey, not a manual sandbox walkthrough.** The action→service→renderer wiring gap (the only layer unit/contract tests can't see) is covered by a sixth Starter/Driver journey. Two enablers: (a) a register-store test seam — the file chooser cannot be driven over the remote Driver SDK, so `onRegisterStore` honors the `openspec.uismoke.register.store.root` system property, flipped per journey stop via a remote `System.setProperty` call; (b) registry isolation — the Starter IDE process gets a journey-scoped `XDG_DATA_HOME` (`applyVMOptionsPatch { withEnv(...) }`), which both keeps the journey inside the suite's no-durable-state-mutation rule and pre-seeds one registered fresh store via the real host CLI so the Coordination tab has state to render (an empty registry hides the tab). The journey requires a 1.6+ host CLI and skips (not fails) below it. Alternative rejected: driving the real file-chooser dialog — the flakiest possible surface, and the chooser is platform UI, not plugin wiring.

## Risks / Trade-offs

- [Captured 1.6.0 output differs from the guru-session observations (e.g. envelope wording)] → Fixtures are captured fresh from the installed 1.6.0 CLI at implementation time; tests assert against the capture, not this design's prose.
- [Renaming/re-scoping the 1.5 register test weakens existing coverage if done carelessly] → The 1.5 assertion set is kept byte-for-byte against the same 1.5.0 fixture; only test naming/organization changes.
- [A future CLI generation changes shapes, not just semantics] → Out of scope here; the per-generation fixture layout established by this change gives that future work an obvious home.

## Open Questions

None — the upstream behavior was verified against the real 1.6.0 CLI before this proposal.

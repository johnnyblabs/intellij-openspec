## Why

The plugin's built-in validator (editor inspections + CLI-less validation) was last aligned to OpenSpec CLI 1.4 behavior. Upstream's validation stack is byte-identical across 1.4.0/1.4.1/1.5.0, so that alignment silently covered 1.5 — but CLI 1.6.0 changed validator semantics: SHALL/MUST evaluation became word-boundary and fence-aware over the full requirement body (no header fallback), scenario counting became fence-aware, and a new INFO tier flags non-canonical level-3 headers inside ADDED/MODIFIED delta sections (with a `line` field, never affecting validity). The plugin currently diverges on three points that produce wrong editor verdicts against a 1.6 CLI — and one standing pre-1.6 divergence (accepting SHOULD/MAY where every CLI generation requires SHALL/MUST) — so a spec the plugin passes can fail `openspec validate` and vice versa. Separately, the plugin's supported-CLI-versions contract still declares `1.3.x`–`1.5.x`; with 1.6 store-health and fixture work shipped, `1.6.x` support should be declared formally.

Tracker: this change is linked to an existing tracker issue via the gitignored `.tracking.yaml` sidecar (per the repository's tracker-sidecar convention).

## What Changes

- **Keyword family parity**: requirement-keyword presence is satisfied only by `SHALL`/`MUST` (word-boundary), matching every CLI generation's rule; `SHOULD`/`MAY` no longer satisfy the check (they never satisfied the CLI). The existing header-only targeted diagnostic and quick-fix are unchanged.
- **Fence-awareness**: keyword detection and `#### Scenario:` counting ignore fenced code blocks, matching 1.6 (a scenario or keyword that exists only inside a fence no longer satisfies the plugin while failing the CLI).
- **INFO tier**: the delta validator emits an INFO-severity issue for non-canonical level-3 headers inside ADDED/MODIFIED delta sections (two variants mirroring the CLI: nameless `### Requirement:` and non-requirement headers), line-anchored, never affecting the file's verdict. The plugin's INFO severity maps to a weak/info editor highlight.
- **Verdict-parity contract tests**: a corpus of authored spec/delta cases is validated by both the real 1.6.0 CLI (captured as fixtures per the contract-test discipline) and the plugin's validator, asserting per-case verdict agreement (valid/invalid) — semantic parity, not byte-identical message text (the plugin keeps its line-anchored, quick-fix-bearing IDE messages).
- **1.6.x support declaration**: `plugin-core`'s supported-versions contract adds the `1.6.x` line; `coordination-surfaces`' per-version behavior contract adds a `1.6.x` row (store/workset model and tiers as 1.5.x, plus the already-shipped 1.6 register/health semantics); `docs/openspec-support.md` already documents the 1.6.x line and needs only the supported-set statement aligned.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `validation`: spec-format and delta-validation requirements gain 1.6 semantics — SHALL/MUST-only keyword rule, fence-aware keyword/scenario evaluation, and the INFO-tier non-canonical-header hint in delta sections (never verdict-affecting).
- `plugin-core`: the supported-CLI-versions set becomes `1.3.x`–`1.6.x` (floor unchanged at `1.3.0`).
- `coordination-surfaces`: the CLI-version behavior contract gains the `1.6.x` row.
- `ui-smoke-journeys`: the editor validator-parity journey gains a fence-masking stop (a seeded spec whose only keyword sits inside a fenced code block draws the missing-keyword diagnostic in the sandbox IDE).

## Impact

- **Code**: `BuiltInValidator` (keyword pattern, fence masking, scenario counting, INFO emission); severity mapping for INFO in the inspection layer if not already rendered.
- **Tests**: verdict-parity corpus + captured 1.6.0 CLI verdict fixtures; unit tests for fence masking and the SHOULD/MAY tightening; existing validator tests updated where they encoded the looser keyword family.
- **Docs**: `docs/openspec-support.md` supported-set statement; user-facing CHANGELOG entry (validator now agrees with the CLI's verdicts on 1.6 semantics; SHOULD-only requirements now correctly flagged).
- **No new IntelliJ Platform APIs**; no store/workset or coordination code changes (the 1.6.x coordination row declares already-shipped behavior).

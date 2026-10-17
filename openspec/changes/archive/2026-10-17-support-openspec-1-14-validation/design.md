## Context

See proposal.md — Why. Facts from the analysis, run against real 1.12.0, 1.13.2, 1.14.1 and 1.14.3 binaries with isolated `HOME`/`XDG_*`; the scratch harness re-runs the captures:

- **Parity corpus (13 items):**
  - 1.14.3 default: 12/13, a subset of the 1.8.0 default anchor.
  - 1.14.3 `--strict`: **8/13**. Every captured generation from 1.6.0 to 1.13.2 is 9/13, identical to the 1.6.0 map. The extra failure is `nameless-change`. 1.14.2's new WARNING fires on it: *"Text before the first requirement in "## MODIFIED Requirements" is not carried into the main spec by archive…"* (at `demo/spec.md` line 4), and `--strict` fails on warnings.
- **The guard today.** `ValidatorVerdictVersionStabilityTest.strictCliVerdictsAreStableAcrossAllCapturedGenerations` asserts each generation's strict map **equals** the 1.6.0 map, so committing 1.14.3 twins as-is fails it. The default arm (`cliDefaultVerdictsAreNeverStricterThanTheLaxestGeneration`) already uses subset-of-anchor and pins the 1.8 relaxation exactly.
- **New 1.14 warnings, from positive controls:**
  - 1.14.0: an unrecognized `.openspec.yaml` key warns (path `.openspec.yaml`; known keys `schema, created, goal, affected_areas, initiative, skip_specs, retire_capabilities`). It also appears in `status`/`instructions` `warnings[]`. 1.14.2 exempts lowercase `x-*` keys.
  - 1.14.1: a main-spec requirement over 500 characters goes from INFO to **WARNING**, and a new `ADDED "X": Requirement text is very long…` warning is added for deltas.
  - 1.14.2: text above the first `## ` section, text before the first requirement in a delta section, and a non-delta `## Section` all warn that archive won't carry them.
- **Additive shapes:**
  - `show --json` adds `requirements[].name`, `scenarios[].name` and `deltas[].requirement(s)[].name`.
  - `status --json` adds an optional `warnings: string[]`.
  - `instructions --json` adds `warnings[]` and `tasks[].sourcePath`/`line`.
  - XDG `registry.yaml` and `worksets.yaml` are byte-identical after normalization.
- **The plugin** writes only `schema` and `created` to `.openspec.yaml`, so its own scaffold never trips the unknown-key warning.

## Goals / Non-Goals

**Goals:** declare 1.14.x supported with real contract coverage, and keep the cross-generation guard both correct (never more restrictive) and non-vacuous.

**Non-Goals:**
- Adopting any 1.14 warning in the built-in validator.
- Reading the new `name` fields. The parsers keep using the fields they read today; adopting `name` would be a separate behaviour change.
- Shortening this repo's over-long requirements. They now fail `--strict` under 1.14.3, which no gate runs. That's a separate docs chore.

## Decisions

**1. Strict arm: subset of the strict anchor, with the divergence pinned.** Replace map-equality with "each generation's strict valid-set ⊆ the 1.6.0 valid-set". This mirrors the default arm, because the safety property (the plugin, which matches the anchor, is never more restrictive) only needs containment.
- **Pinned divergence** (as the default arm does for 1.8): generations ≥ 1.14.2 must equal the anchor's valid-set minus exactly `{nameless-change}`; generations < 1.14.2 must still equal it exactly. That keeps the test from passing on an empty or wrongly widened result.
- **Kept checks:** every corpus must carry the same item-id set, and at least two generations must be discovered.
- *Alternative considered:* re-anchoring strict on 1.14.3. Rejected: that would make the plugin's proven strict verdict match the *strictest* generation, which is backwards for a never-more-restrictive guarantee.

**2. Leave every new warning out of the fallback.** All are warnings that only fail under `--strict`, and each makes the client stricter. The established policy (1.11's placeholder warning, 1.13's errors) is that the fallback MAY stay laxer. A positive control per warning pins the CLI's real shape, so a future decision to adopt one starts from a capture, not a guess.

**3. Tolerance proven by contract fixtures, not by assumption.** Capture `show --json`, `status --json`, `instructions <artifact> --json` and `instructions apply --json` from 1.14.3 on a small project carrying the new keys. Run each through the plugin's real parser and assert it reads what it reads today: change deltas, artifact DAG / `isComplete`, instruction fields. If a parser rejects an unknown key, fix it in this change; it's the one plausible `src/main` touch.

**4. Couple the target bump to the corpus.** `openspecTargetVersion` becomes `1.14.3` in the same change as the `1.14.3/` twins and `version.txt` (`TargetVersionSingleSourceTest`). The Forgejo ui-smoke workflow installs this version, and its journeys don't depend on strict verdicts.

## Risks / Trade-offs

- **Risk: the subset guard is too permissive and hides a real regression.** → **Mitigation:** the exact pinned divergence (≥ 1.14.2 equals anchor minus `{nameless-change}`; earlier generations equal the anchor) plus the shared id-set check. Verify by mutation: delete `nameless-change` from the expected divergence, and add a second strict failure to a copied twin. Both must fail.
- **Risk: the CLAUDE.md wording change is misread as allowing tracker IDs in `.openspec.yaml`.** → **Mitigation:** the new wording says unknown keys now *warn* (and `x-*` keys are exempt), and restates that tracker IDs stay in the gitignored sidecar because `.openspec.yaml` is published with the change.
- **Risk: the local CLI upgrade surprises other projects on this machine.** → **Mitigation:** pin exactly `@1.14.3` and do it as the adopt-sync task, the same pattern as 1.13.2.

## Migration Plan

Additive, fixtures and tests only. Rollback means reverting the fixtures, guard edits, target bump and docs.

## Why

OpenSpec CLI **1.16.0** (released 2026-10-30) is the new npm-latest line, and the plugin should declare it supported and target it. A capture against the real 1.16.0 CLI shows that nothing the plugin parses has changed shape:
- Over the parity corpus, the `validate --all --json` default and `--strict` documents are byte-identical to 1.15.0, apart from `durationMs`.
- The workflow ids, the AI-tool registry, `init --help` and the detection cases are identical.
- The design instructions are identical. The specs instructions add one bullet: every scenario needs a WHEN/GIVEN step and a THEN step.

Unlike 1.15, though, 1.16 changes two validate behaviours:
- A scenario missing its trigger or outcome step now gets a WARNING. It passes in default mode and fails under `--strict`.
- An unparseable or unreadable `openspec/config.yaml` now makes `validate` return an item-less error response (`unparseable_config` / `unreadable_config`).

This change captures both as contract fixtures and pins how the plugin reads them today. Mirroring them is separate work.

Tracked in the linked tracker entry.

## What Changes

- **Declare `1.16.x` a supported generation.** The floor stays `1.3.0`, with no ceiling. Bump the plugin's target version from `1.15.0` to `1.16.0`.
- **Capture real 1.16.0 contract fixtures** under `fixtures/cli/1.16.0/`:
  - the parity-corpus twins and `version.txt`;
  - the AI-tool registry and `init --help`;
  - the workflow ids;
  - the standalone binary's version output;
  - the design and specs instructions;
  - a change whose only issue is the scenario-step warning, in default and `--strict` mode, with its input project committed beside it;
  - the two configuration error responses.
- **Contract-test the new captures at the parse level:**
  - the step-warning change reads as passing in default mode and failing, with the warning, under `--strict`;
  - each configuration error response reads as an error response, never as a passing report.
- **Re-point the workflow contract tests** to the target version read from `gradle.properties`, so the target-scoped fixture can't drift from the target again.
- **Extend the version guards:** add `1.16.0` to the verdict-stability floor and the at-least assertions, and add ordering checks.
- **Verdict-parity anchors stay put.** Default stays `1.8.0`, strict stays `1.6.0`, and the archive-drop divergence stays pinned from `1.14.2`.
- **Adopt-sync:** with the local CLI at 1.16.0, run `openspec update` so the managed skills and commands carry the new stamp.
- **Out of scope:** each of these is its own tracker entry.
  - mirroring the scenario-step check in the built-in validator;
  - treating a broken `config.yaml` as a validate failure instead of falling back to the built-in verdict;
  - the inline triple-backtick fence fix;
  - requirement headers with no space after `###`;
  - extending the parity corpus with step-check cases;
  - install-hint wording;
  - the CLI version notes beyond the support line.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`: in "Supported CLI versions and capability preservation", add `1.16.x` to the declared lines and add a "1.16.x line is a supported generation" scenario.

## Impact

- **Code (`src/main`):** none expected. The existing parsers already read every 1.16.0 output, and the contract fixtures prove it.
- **Tests and fixtures:**
  - a new `src/test/resources/fixtures/cli/1.16.0/` directory;
  - a new versioned error-capture case pair in `scripts/capture-error-fixtures.py`;
  - `ValidatorVerdictVersionStabilityTest.FLOOR`, `CliVersionAtLeastTest` and `CliVersionTest` extended;
  - `WorkflowParityContractTest` and `CoreWorkflowsContractTest` read the target version;
  - `openspecTargetVersion` bumped to `1.16.0`, which `TargetVersionSingleSourceTest` and the UI smoke workflow read.
- **Docs (vendor-neutral):** `openspec-support.md`, README, fixtures README and CHANGELOG. The `plugin vX.Y.Z` restatements are bumped at the release cut, not here.
- **Platforms:** no change to IntelliJ 2024.2+ compatibility.

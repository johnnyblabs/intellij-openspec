## Why

The plugin's stated purpose is to be a faithful companion to the OpenSpec client, but there is no single place that states *what it covers* versus *what the client offers* — and coverage is **version-relative**. A user can't tell whether a missing capability is unsupported by the plugin or just needs a newer CLI, and contributors have no shared north-star for parity work.

A direct comparison of OpenSpec CLI 1.3.1 ↔ 1.4.0 confirms the version axis matters: every change-lifecycle workflow (including `verify-change`) and the `status` / `instructions` / `templates` / `schemas` / `validate` / `show` commands already exist at the 1.3 floor, while the `workspace-planning` schema and the `workspace` / `context-store` / `initiative` / `set` commands are 1.4 additions. That distinction should be visible, not implicit.

## What Changes

- Publish a vendor-neutral **OpenSpec client coverage matrix** at `docs/openspec-support.md`: support status (supported / partial / divergent / planned / plugin-original) for each client capability, grouped by area, each annotated with the minimum CLI version it requires, plus the plugin's CLI-version support contract, a lifecycle diagram, and a dependency-ordered roadmap.
- Link the matrix from the README.
- Add a `plugin-documentation` requirement so the matrix is a maintained, contract-bound artifact (including a guardrail that it stays vendor-neutral on the public mirror).
- Frame the broader **OpenSpec workflow-fidelity** effort and its phase decomposition in `design.md`; concrete child changes (schema-aware Verify, etc.) are proposed separately.

## Capabilities

### Modified Capabilities
- `plugin-documentation`: add a requirement for a published, version-aware OpenSpec client coverage matrix that stays vendor-neutral on the public mirror.

## Impact

- New file: `docs/openspec-support.md`; a README link.
- Spec: `plugin-documentation` gains the coverage-matrix requirement.
- No code changes. Establishes the parity north-star and the phase plan that subsequent fidelity changes (schema-aware Verify first) build on.

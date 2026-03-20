## Why

The plugin has grown quickly and now risks drifting from the OpenSpec workflow contract (artifact DAG, validation rules, and spec-sync correctness). We need an explicit compliance change now so upcoming features build on consistent, testable OpenSpec behavior instead of divergent IDE-specific behavior.

## What Changes

- Add pre-flight compliance gate to archive and post-merge validation to spec-sync.
- Enforce stricter spec-validation rules for RFC 2119 keywords, scenario structure, and delta operation sections.
- Add compliance visibility so users can see what is compliant, what failed, and what action is required.
- **BREAKING**: Validation will reject spec content that previously passed with incomplete requirement/scenario or delta-operation structure.

## Capabilities

### New Capabilities
- `compliance-observability`: Surface OpenSpec compliance status and actionable remediation in the workflow UI and notifications.

### Modified Capabilities
- `workflow`: Update workflow requirements to enforce OpenSpec-compliant phase transitions and archive outcome handling.
- `validation`: Tighten requirement/scenario/delta validation to match OpenSpec spec-driven rules.
- `plugin-core`: Standardized compliance checks in core services.
- `spec-sync`: Clarify sync behavior and constraints so delta-to-main reconciliation remains OpenSpec-compliant.

## Impact

- Affected code: workflow actions and orchestration services, validation services/inspections, spec sync services, notification and tool window status components.
- Affected specs: delta specs for `workflow`, `validation`, `plugin-core`, `spec-sync`, plus new `compliance-observability` spec.
- Affected behavior: stricter validation outcomes, clearer archive compliance reporting.

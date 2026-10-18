## ADDED Requirements

### Requirement: Store health reflects every doctor diagnostic

When a store's doctor check fails to run or its output cannot be read, the store's health SHALL be reported as unknown, and SHALL NOT be reported as healthy. Error diagnostics the CLI reports inside a store's own `status` SHALL count towards that store's health even when the top-level `status` is empty and the command exits 0.

#### Scenario: Doctor run fails
- **WHEN** the store doctor command times out, or prints output that is not JSON
- **THEN** the store SHALL be shown with health unknown, not as healthy

#### Scenario: Nested errors with exit 0
- **WHEN** the store listing's doctor output is the captured 1.14.3 `store doctor` response for a store whose root was removed (top-level `status` empty, the error in the store's own `status` and its `openspec_root.status`, exit 0)
- **THEN** that store's diagnostics SHALL include the error exactly once, with its message

#### Scenario: Root-level envelope from the Store Doctor action
- **WHEN** the Store Doctor action's `doctor --store <id> --json` returns the captured 1.14.3 `unhealthy_store_root` response (exit 1)
- **THEN** the action SHALL show that diagnostic with its `fix` text, and SHALL NOT say the store is healthy

#### Scenario: Healthy store is unchanged
- **WHEN** the doctor output reports no error or warning diagnostics at any level
- **THEN** the store SHALL be shown as healthy as before

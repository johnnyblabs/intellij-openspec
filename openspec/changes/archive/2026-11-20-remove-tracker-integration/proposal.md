## Why

The `tracker-integration` capability describes automation that lives outside the plugin: local project-management tooling that mirrors OpenSpec changes into external trackers. The plugin implements none of it, and the tooling itself is never published. Keeping the description in the public specs exposes internal workflow details and conflicts with the rule that tracker identifiers live in a gitignored local sidecar, not in published artifacts.

## What Changes

- Remove the `tracker-integration` capability from the main specs.
- Document the local tracker workflow in internal project documentation, not in the public specs.

## Capabilities

### Removed Capabilities

- `tracker-integration`: removed. It describes no plugin behavior.

## Impact

- No plugin behavior changes. No code, test or user-visible feature depends on this capability.
- The public specs no longer describe tracker automation.

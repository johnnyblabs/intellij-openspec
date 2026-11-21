## Why

The workflow panel's Archive button was enabled only when every artifact was complete and no tasks remained. The OpenSpec CLI only warns about open tasks, and `--yes` archives anyway. So the button was stricter than the client it wraps, which breaks the rule that the plugin is never stricter than the CLI. The Verify surface already offers an "Archive anyway" action for in-progress changes.

One sentence in the Pre-archive verification requirement also said open `[~]` tasks block archive. That contradicts the in-progress rule in the same capability.

## What Changes

- The Archive button is enabled when all artifacts are complete, even when tasks remain.
- With tasks remaining, Archive routes through the Verify action, so the user sees the in-progress state and chooses "Archive anyway".
- The Pre-archive verification requirement states that open `[~]` tasks are reported as not done but do not block archive.

## Capabilities

### Modified Capabilities

- `verify-workflow`: the Pre-archive verification requirement no longer treats open tasks as a block.

## Impact

- The panel's Archive button is enabled in more states. That's the point: it now matches the CLI.
- No change to the Archive gate for validation errors. A BLOCKED change still cannot archive.
- Tests that mirrored the old gate are replaced by tests that call the helper the panel uses.

## Why

Please check the workflow so that we have archive and sync functionality

## What Changes

The ipenspec workflow has archive and sync functionality

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `actions`: Add explicit archive-and-sync action behavior and failure handling requirements.
- `workflow-panel`: Add archive/sync outcome visibility and recovery guidance requirements.
- `issue-tracking`: Add idempotent post-archive sync lifecycle reconciliation requirements.

## Impact

- `src/main/java/com/johnnyb/openspec/actions/` — archive action flow and sync invocation
- `src/main/java/com/johnnyb/openspec/services/` — sync orchestration and state reconciliation
- `src/main/java/com/johnnyb/openspec/toolwindow/` — workflow panel status and recovery guidance
- `src/main/java/com/johnnyb/openspec/tracking/` — tracker lifecycle sync/update retry behavior
- `src/test/java/**` — archive/sync success, partial-failure, and idempotency tests

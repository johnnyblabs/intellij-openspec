## Why

The plugin registers all 16 menu actions unconditionally. The OpenSpec CLI uses a profile system (`core` vs `custom`/expanded) to control which workflow commands are available — `core` enables only propose, explore, apply, archive while expanded adds continue, ff, verify, sync, bulk-archive. The plugin ignores this entirely: a user on the `core` profile sees Continue, Fast-Forward, Verify, Sync Specs, and Bulk Archive in the menu even though their profile says those shouldn't be available. This creates a confusing mismatch between the CLI experience and the IDE experience.

## What Changes

- **Add a `WorkflowProfileService`** that resolves the active workflows list from the global OpenSpec config (via CLI or settings fallback) and caches the result for efficient action `update()` checks.
- **Add a `getWorkflowId()` method to `OpenSpecBaseAction`** returning `null` by default. Workflow-bound actions override it with their workflow ID (e.g., `"ff"`, `"continue"`, `"verify"`, `"sync"`, `"bulk-archive"`).
- **Modify `OpenSpecBaseAction.update()`** to check the workflow ID against the active workflows. If the workflow is not enabled: the action remains visible but is disabled, with a tooltip explaining how to enable it.
- **Core actions** (propose, explore, apply, archive) also declare their workflow IDs but will always be enabled since every profile includes them.
- **Utility actions** (Init, Validate, List, Refresh, Update, ManageTools, SetupWizard) return `null` for workflow ID and are always enabled — they are not workflow commands.

## Capabilities

### New Capabilities
- `profile-visibility`: Profile-aware action enablement — disabling expanded workflow actions when the active profile doesn't include them, with tooltip guidance.

### Modified Capabilities
_None — existing workflow and plugin-core specs are unaffected. The change is additive: actions gain a new enablement check but their behavior when enabled is unchanged._

## Impact

- **Actions**: All `OpenSpecBaseAction` subclasses gain the `getWorkflowId()` hook. Nine actions override it with their workflow ID.
- **Services**: New `WorkflowProfileService` reads the global config workflows list.
- **Settings**: `OpenSpecSettings` already stores the profile string. The new service resolves the workflows list from CLI or a cached fallback.
- **plugin.xml**: Register `WorkflowProfileService` as a project service.
- **No breaking changes**: Actions that were enabled remain enabled when their workflow is in the active profile. Only expanded actions get disabled for `core`-profile users.

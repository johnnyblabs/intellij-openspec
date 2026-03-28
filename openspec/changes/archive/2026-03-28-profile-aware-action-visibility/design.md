## Context

The plugin's `OpenSpecBaseAction.update()` currently checks only whether the project is an OpenSpec project. All 16 menu actions are visible and enabled for any initialized project. The OpenSpec CLI uses a global config (`~/.config/openspec/config.json`) with a `profile` field and a `workflows` array that controls which commands are available. The plugin's settings panel already reads and displays the profile and its workflows via `ConfigProfileDetail`, but no action uses this information for enablement.

The `update()` method runs on the background thread (`ActionUpdateThread.BGT`) every time IntelliJ evaluates action state — this happens frequently (menu open, toolbar render, etc.), so the workflows lookup must be fast.

## Goals / Non-Goals

**Goals:**
- Disable (but keep visible) workflow actions whose workflow ID is not in the active profile's workflows list.
- Show a descriptive tooltip on disabled actions guiding the user to enable them via Settings.
- Cache the workflows list efficiently so `update()` checks are O(1) lookups against an in-memory set.
- Refresh the cache when the profile changes in settings.

**Non-Goals:**
- Implementing `/opsx:new` or `/opsx:onboard` actions (backlog).
- Changing the WorkflowActionPanel pipeline chips visibility — this change only affects menu actions.
- Supporting per-project profile overrides — the profile is global, matching CLI behavior.

## Decisions

### 1. New `WorkflowProfileService` with cached `Set<String>`

A project-level service that holds the active workflows as a `Set<String>`. On first access it resolves the workflows via:
1. CLI: `openspec config list --json` → parse the `workflows` array
2. Fallback: if CLI unavailable, use a hardcoded core default (`propose, explore, apply, archive`)

The set is cached and exposed via `isWorkflowEnabled(String workflowId)`. A `refresh()` method re-reads from CLI/fallback.

**Why:** `update()` runs frequently. A service with a cached `Set<String>` makes the check a constant-time `contains()` call. The fallback ensures the plugin works without the CLI installed — core workflows are always enabled.

**Alternative considered:** Read from `OpenSpecSettings.getProfile()` and resolve workflows in `update()` itself. Rejected because resolving workflows requires either CLI execution or mapping logic, both too expensive for every `update()` call.

### 2. `getWorkflowId()` hook on `OpenSpecBaseAction`

Add a `protected String getWorkflowId()` method returning `null` by default. Each workflow-bound action overrides it:

| Action | `getWorkflowId()` |
|---|---|
| `OpenSpecProposeAction` | `"propose"` |
| `ExploreContextAction` | `"explore"` |
| `OpenSpecApplyAction` | `"apply"` |
| `OpenSpecArchiveAction` | `"archive"` |
| `OpenSpecFfAction` | `"ff"` |
| `OpenSpecContinueAction` | `"continue"` |
| `OpenSpecVerifyAction` | `"verify"` |
| `OpenSpecSyncAction` | `"sync"` |
| `OpenSpecBulkArchiveAction` | `"bulk-archive"` |

Utility actions (Init, Validate, List, Refresh, Update, ManageTools, SetupWizard) keep the default `null` → always enabled.

**Why:** Clean opt-in pattern. Existing actions don't need modification unless they map to a workflow. New actions just override one method.

### 3. Modify `update()` with a two-tier check

```
isOpenSpecProject?
  → no:  hidden
  → yes: getWorkflowId() == null?
           → yes: enabled (utility actions)
           → no:  isWorkflowEnabled(id)?
                    → yes: enabled
                    → no:  visible but disabled, tooltip set
```

The disabled tooltip: `"Requires expanded profile. Change in Settings → Tools → OpenSpec."`

**Why:** Visible-but-disabled is the standard IntelliJ pattern for actions that exist but aren't currently applicable. The tooltip tells users exactly how to fix it.

### 4. Refresh cache on profile change in settings

`OpenSpecConfigurable.apply()` already calls `applyProfileChange()` when the profile changes. After the CLI call succeeds, call `WorkflowProfileService.refresh()` to update the cached workflows.

**Why:** Profile changes are rare (settings apply). Refreshing only then keeps the hot path (every `update()` call) cheap.

### 5. Eager initialization on project open

`WorkflowProfileService` loads the workflows list on first access (lazy init). This happens naturally when the first action `update()` fires. If the CLI is slow, the fallback kicks in immediately and the CLI result is used on the next refresh.

**Why:** Avoids blocking project startup. The worst case is that expanded actions appear disabled for one UI cycle until the cache populates.

## Risks / Trade-offs

- **CLI unavailable → only core workflows enabled by default** → Acceptable. Users without CLI are likely on `core` profile anyway. The settings panel shows a message about installing CLI for full profile support.
- **Cache staleness if user changes profile outside the IDE** (e.g., `openspec config profile core` in terminal) → Mitigated by refreshing when the settings panel opens. Not a common case.
- **WorkflowActionPanel pipeline chips not affected** → Intentional for v1. The panel has its own enablement logic based on artifact state. Profile-based chip visibility could be a follow-up.

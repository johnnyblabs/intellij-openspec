## Context

`WorkflowActionPanel` has two paths to refresh the pipeline for a given change:

1. `refreshForChange(changeName)` — wraps the work in `executeOnPooledThread()` (correct)
2. `refreshForChangeOnPool(changeName)` — does the actual work: calls `orchestration.getArtifactStatus()` which may invoke CLI

`setActiveChange()` is called from the EDT (tree selection handler) and directly calls `refreshForChangeOnPool()`, bypassing the thread dispatch. This blocks the UI.

## Goals / Non-Goals

**Goals:**
- Fix the EDT violation in `setActiveChange()` by routing through `refreshForChange()`.

**Non-Goals:**
- Refactoring the broader `refreshForChange`/`refreshForChangeOnPool` pattern. The existing separation is fine — `refreshForChangeOnPool` is a valid internal method for callers already on a background thread.

## Decisions

### 1. Replace `refreshForChangeOnPool` with `refreshForChange` in `setActiveChange()`

One-line fix: change `refreshForChangeOnPool(changeName)` to `refreshForChange(changeName)` in `setActiveChange()`. This routes through the existing `executeOnPooledThread()` wrapper.

**Why not wrap inline?** `refreshForChange()` already exists for exactly this purpose. Duplicating the `executeOnPooledThread` wrapper would be redundant.

## Risks / Trade-offs

- **Pipeline update becomes async** — The pipeline display will update a moment after `setActiveChange()` returns instead of synchronously. This is actually the correct behavior and matches how `refresh()` already works.

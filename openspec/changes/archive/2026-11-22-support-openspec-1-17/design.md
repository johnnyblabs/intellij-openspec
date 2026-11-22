## Context

The plugin targets OpenSpec CLI 1.16.1. CLI 1.17.0 is a minor release. A comparison of its published package with 1.16.1 shows that only the `update`, `init` and legacy-cleanup code changed. The workflow templates, profiles, AI-tool registry, validation, store, workset, change-metadata, project-config, update-plan and command code are byte-identical. That comparison is static. This change confirms it against real CLI output.

## Goals / Non-Goals

**Goals:**
- Target 1.17.0 and declare the 1.17.x line supported.
- Pin the unchanged surfaces with real 1.17.0 captures, so a later drift is caught by the existing contract tests.

**Non-Goals:**
- Any change to how the update preview presents 1.17's new cleanup. That has its own tracker entry and builds on these fixtures.
- Any 2.x support.

## Decisions

- **Capture everything the target-scoped tests read, then diff against 1.16.1.** The tests that resolve fixtures by target version fail without a full set, so a partial capture is not an option. An identical result is the expected outcome and the evidence that the bump is safe. Any non-version difference stops the change for a decision rather than being absorbed into it.
- **Keep the existing update-dry-run cases as they are.** Their seeded legacy files are hand-written, so they do not match a released file fingerprint, and 1.17 should report them the same way 1.16.1 did. Re-capturing them confirms that. New cases for 1.17's unasked cleanup belong to the follow-up change.
- **Keep the verdict-parity anchors.** Validation code is unchanged, so default stays `1.8.0` and strict stays `1.6.0`.
- **Run captures through the installed CLI package, not `npx`,** so no package-cache paths leak into fixtures.

## Risks / Trade-offs

- [The static comparison misses a runtime difference] → The real captures are diffed against 1.16.1, and any difference stops the change.
- [The adopt-sync `openspec update` deletes a tracked file, now that 1.17 removes unedited legacy files without asking] → Review `git status` after the update and restore anything unexpected. A dry run beforehand showed only the skills' version stamp changing.

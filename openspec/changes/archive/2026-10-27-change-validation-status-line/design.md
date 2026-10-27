# Design

## Context

See proposal.md for why. Today, selecting a change runs `openspec show <change> --type change --json` off the EDT (`OpenSpecToolWindowPanel.renderChangeDeltasPreview`), renders it with `SpecPreviewRenderer.renderChangeDeltas`, and caches the fragment per change name. Every VFS event under `openspec/` bumps `cacheEpoch` and clears that cache. The Validate action already validates one change with `validate <id> --type change --json` (`OpenSpecValidateAction.cliArgs`) and reads the output with `CliOutputParser.parseJsonOutput`. That parser takes the verdict from each item's `valid` field and surfaces issues only from invalid items.

Upstream (CLI 1.15.0) validates a change as one unit. `show`, `view`, `list` and `status` report no validity, so the line fills a gap rather than restating anything. The CLI's verdict also reads the main specs: MODIFIED blocks must not drop scenarios.

## Goals / Non-Goals

**Goals:**
- the pane's verdict is the CLI's, and it is identical to what Validate reports for the change;
- no extra process run on a cached selection;
- never stale.

**Non-Goals:**
- strict mode in the pane;
- listing issues inside the pane;
- reusing the tree's archive-ready cue.

## Decisions

**D1. The CLI decides; the built-in check only without a CLI.** The pane runs the same change-scoped validate as Validate and parses it with `CliOutputParser.parseJsonOutput`. Showing the built-in check by default was rejected: it can disagree with the CLI on its CLI-only warnings, and the `spec-viewer` spec requires change state to come from the CLI. The built-in check is shown only in the "needs the CLI" placeholder, labelled "Built-in check". The spec's "Client-owned state only" requirement is modified to allow exactly that.

**D2. Cache the verdict with the deltas.**
- **How:** the validate run happens in the same off-EDT render as `show`, and the status line goes into the fragment before it is cached.
- **Why that is enough:** the existing invalidation (any VFS event under `openspec/`) already covers the main specs the verdict depends on.
- **Rejected:** caching the verdict separately, or running validate on every selection.
- **Cache rule:** the combined fragment is cached only when `show` was cacheable, the validate run gave a verdict, and no invalidation raced in (the existing `cacheEpoch` check).
- **When validate gives no verdict:** `CliException` or `CliErrorResponseException`. The deltas render without the line and are not cached, so the next selection tries again.

**D3. One wording for both sources.** A pure `SpecPreviewRenderer.validationLine(label, ValidationResult)`:
- passed → `"<label>: passed"`, plus `", M warnings"` when the result carries warnings;
- otherwise → `"<label>: N errors, M warnings"`;
- the label is "Validate" for the CLI and "Built-in check" for the fallback.

The CLI parser surfaces no issues from valid items, so a valid change reads "Validate: passed", exactly as Validate reports it. Changing the parser to surface those warnings would also change the Validate action's output, so it is out of scope.

**D4. "Show issues" runs a change-scoped Validate.**
- The line carries an `openspec-validate:<change>` link.
- The pane's hyperlink listener resolves it to the Validate action's existing `runValidation(project, ValidateTarget.change(name))`, through a small public entry point on `OpenSpecValidateAction`. It is not dispatched as `OpenSpec.Validate`, which validates the whole project.
- Results appear in Validate's usual console and notification.
- The link is not shown on the built-in line, because Validate without a CLI is that same built-in check.

**D5. Legend in the delta render.** `renderChangeDeltas` adds a one-line legend (CSS class `openspec-delta-legend`) after the summary counts, only on the non-empty path. The empty state never has it. Wording follows upstream's archive behaviour (`specs-apply.js`):
- ADDED adds the requirement to the main spec;
- MODIFIED replaces the whole requirement, so keep its scenarios;
- REMOVED deletes it;
- RENAMED renames it and keeps its text.

**D6. Placement and states.**

| State | Line |
|---|---|
| CLI shows deltas, or a no-deltas empty state | Validate line, with "Show issues" |
| CLI unavailable placeholder | Built-in check line, no link |
| CLI rejected | no line |

In every state the line sits after the header and after the open-in-editor link, inserted the way `withOpenLink` inserts its link. The accessible names of the pane states are unchanged.

## Risks / Trade-offs

- [The first selection of a change now starts two CLI processes in a row] → Validate takes about 0.1–0.3 s and runs off the EDT behind the existing generation check. Cached selections run nothing.
- [Hand-written validate JSON would encode assumptions] → New fixtures are captured from the real CLI for one valid change, one with an error and one with only warnings, with root paths sanitized. The contract tests read those.
- [The CLI and the built-in check can disagree] → The two never appear together. Each is labelled with its source.

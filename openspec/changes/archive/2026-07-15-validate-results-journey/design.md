## Context

`OpenSpecValidateAction` runs the built-in validator, merges in the CLI's `validate --all --json` when available, and renders results on two surfaces: a summary notification (`Validation failed (N errors, M warnings)`) and the full per-issue report in the OpenSpec Console tab (one line per issue, `[SEVERITY] path(:line) — message [rule]`). CLI-parsed issues carry path `type/id` (e.g. `spec/missing-shall`) and rule `cli`; built-in issues carry absolute file paths and plugin rule ids. The Console panel registers when the tool window contents are built; until then the action falls back to a summary-only notification (journey run 1 discovered this empirically — the report needs its surface shown first). The 1.6 bracket-path parsing bug dropped CLI errors from exactly this rendered report.

## Goals / Non-Goals

**Goals:** one journey proving a CLI-reported validate error survives parse + merge and reaches the rendered notification.

**Non-Goals:** asserting built-in-validator rendering (unit/platform-covered); any new results surface (the notification is the existing, correct home); running on pre-1.6 CLIs.

## Decisions

1. **Assert the CLI-parsed line, not just the message.** The built-in validator now emits the same "must contain SHALL or MUST" wording, so a message-only assertion would pass even if the CLI path broke again. The journey asserts the summary notification (`Validation failed (`) plus the Console rendering `spec/missing-shall` — the CLI parser's `type/id` path form, satisfiable only by the CLI-parsed issue. The tool window is shown before the action so the Console panel exists (its real usage precondition, not a test artifice: the report's surface is the tool window).
2. **Reuse journey mechanics.** Seeding into the copied demo project before IDE boot (journey 4's pattern), `invokeAction("OpenSpec.Validate")` (journey 2/5's pattern), notification assertion via `notificationContents` (journey 2's pattern), 1.6+ CLI assumption guard (journey 6's pattern). No new remote stubs.

## Risks / Trade-offs

- [The demo project's other seeded content changes validate totals] → the assertion targets the specific seeded issue line, not counts or overall pass/fail text.

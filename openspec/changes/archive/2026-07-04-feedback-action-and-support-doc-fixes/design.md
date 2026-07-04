# Design — Feedback action + support-doc accuracy fixes

## Context

Two tails from the 1.4.x audit. The feedback action is a deliberately small, self-contained delegation — `openspec feedback` is one of upstream's "Human-Only Commands" with no `--json`, so the plugin's job is collection UX + invocation + notification, nothing more. The doc fixes correct three verified inaccuracies in the support matrix and close the feature-delta doc's open questions with facts already confirmed against the installed 1.4.1 CLI in this audit.

## Goals / Non-Goals

**Goals:**
- One-dialog feedback path from the IDE, standard notifications, hidden without a CLI.
- Support matrix cells match verified code behavior; feature-delta doc §5 carries answers, not questions.
- A spec-level accuracy clause so mechanism-classification drift is caught by review against the spec.

**Non-Goals:**
- No feedback drafts/history/attachments; no telemetry of our own.
- No re-audit of every matrix row — only the three rows the audit flagged (broader re-verification belongs to the per-CLI-version analysis cadence).
- No `--json` parsing or contract fixtures (`feedback` has none).

## Decisions

1. **Modal-less collection.** A small dialog (message field + optional body area) launched from Tools → OpenSpec and the tool-window overflow; submission runs on a background task, success/failure lands as a notification carrying the CLI's stderr on failure. Rationale: matches every other CLI delegation in the plugin; no new UX vocabulary.
2. **Visibility follows CLI availability, not version.** `feedback` predates the plugin's floor; the action hides exactly when the CLI is undetected (same predicate as other CLI-only actions). No version gate.
3. **Doc fixes ride this change, not a standalone commit.** They share provenance with the audit that found them and the spec clause that prevents recurrence; bundling keeps the paper trail in one archived change.
4. **Accuracy clause is scoped to mechanism classification.** The modified requirement mandates that delegated/built-in/indirect labels match code behavior — it does not attempt to mandate general "docs are correct" (unverifiable); it names the one classification axis the audit caught drifting.

## Risks / Trade-offs

- [Feedback misuse/empty messages] → dialog validates non-empty message; CLI errors surface verbatim.
- [Doc fixes could themselves drift from code later] → that is exactly what the new spec clause + per-CLI-version analysis cadence exist to catch.

## Migration Plan

None; additive action + doc edits. Ships next minor release.

## Open Questions

None.

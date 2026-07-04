# Design — Complete the schema tooling surface

## Context

`SchemaService` today delegates three operations (`schemas --json` listing with caching, `schema fork`, `schema init`) and the Settings panel renders a schema list with fork/create actions behind the `MIN_CLI_VERSION` guard. Upstream documents the schema system as the standard customization mechanism, with a three-step authoring loop the IDE currently truncates after step one: create (fork/init) → **validate** (`schema validate`) → **edit templates** (paths from `templates --json`), plus **provenance debugging** (`schema which`) when project/user/package copies shadow each other. The 1.4.x release-scoping audit identified these as three of the four durable CLI coverage gaps.

## Goals / Non-Goals

**Goals:**
- Complete the authoring loop inside Settings: validate a schema, see where it resolves from, open its templates in the editor.
- Contract-test every new `--json` parse against captured real CLI output.
- Preserve the existing degradation contract (CLI missing/below floor → hidden or disabled with guidance).

**Non-Goals:**
- A template *editor* UI (templates open as ordinary markdown files in the IDE editor — that IS the edit experience).
- Schema *authoring* wizards beyond the existing fork/init dialogs.
- Any change to schema-name validation in the config validator (already runtime-driven).
- Surfacing `templates` outside schema management (e.g. per-change template views) — out of scope until a concrete need appears.

## Decisions

1. **All three commands parse via captured fixtures.** New fixtures under `src/test/resources/fixtures/cli/` captured from the real 1.4.1 CLI (isolated `XDG_DATA_HOME` where state is needed, paths sanitized), with contract tests following `CliContractTest`. This is the repo's contract-testing rule; the Phase-3 incident is the precedent for why.
2. **Provenance is displayed, not polled.** `schema which <name> --json` runs when the schema list refreshes (same cache lifecycle as the listing — invalidated on fork/init), not per-selection, keeping Settings snappy. Provenance renders as a short origin tag (project / user / built-in) on each list row or detail line.
3. **Validate is on-demand, results inline.** `schema validate <name> --json` runs from an explicit action on the selected schema (and is offered as a follow-up toast after fork/init). Results render in the schema section (message list with severity), not a modal wall of text; failure of the CLI call itself reports stderr through the existing error surface.
4. **Templates open via resolved paths.** `templates --schema <name> --json` returns artifact→path; the action opens each existing file in the editor (`FileEditorManager`), skipping and reporting paths that don't exist rather than creating them — creation is the CLI's job (`schema init` scaffolds them).
5. **Reuse the existing guard.** No new version constant: actions appear exactly when the current schema section appears (`SchemaService.MIN_CLI_VERSION`). During implementation, verify on a 1.3.x CLI whether `schema which/validate` and `templates` exist; if any is missing there, disable that action below 1.4.0 with the standard "upgrade recommended" hint — a one-line gate using the existing `CliVersion` helper, decided by empirical check, not assumption.

## Risks / Trade-offs

- [`templates` output shape may differ between 1.3.x and 1.4.x] → fixtures captured on 1.4.1; the 1.3.x check in decision 5 covers presence, and parsers tolerate missing fields by degrading to "path unavailable".
- [Which-per-schema could mean N CLI calls on refresh] → batch behind the existing background refresh, cap to the listed schemas, and reuse the cache; if measured cost is noticeable, fall back to provenance-on-selection only.
- [Settings panel growth] → the three additions are one action row + one origin tag; no new top-level sections.

## Migration Plan

No migration; purely additive UI. Ships in the next minor release with the support-matrix rows flipped.

## Open Questions

- Whether `schema which/validate` and `templates` exist on 1.3.x CLIs — resolved empirically during implementation (decision 5 defines both outcomes).

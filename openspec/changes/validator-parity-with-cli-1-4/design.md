# Design — Validator parity with OpenSpec CLI 1.4

## Context

OpenSpec CLI 1.4.0 shipped two permanent parser/validator behaviors (upstream PR #1154, per `docs/cli-versions/1.4.md`): requirement headers parse regardless of capitalization, and `openspec validate` gives a targeted "move the keyword onto the requirement body line" hint when a requirement's RFC 2119 keyword appears only in its header. The plugin validates and parses specs **built-in** (it does not shell out for inline checks), and all five header matchers are case-sensitive today:

- `validation/BuiltInValidator.java:30` — `^### Requirement:\s*.+`
- `validation/DeltaSpecInspection.java:25` — same pattern
- `validation/SpecFormatInspection.java:14` — `### Requirement:`
- `services/SpecParsingService.java:27` — `^###\s+Requirement:\s+(.+)$`
- `services/SpecSyncService.java:39,385,402` — literal `### Requirement:` in three patterns

A spec written `### requirement: …` is valid to the CLI but invisible or flagged in the IDE — a direct disagreement between the two validators the user sees.

## Goals / Non-Goals

**Goals:**
- Recognize the `### Requirement:` header token case-insensitively everywhere the plugin parses it, matching CLI 1.4+.
- Report the keyword-in-header-only condition with the CLI's targeted guidance, and offer an inspection quick-fix that moves/adds the keyword placement correctly.
- Keep validator and CLI verdicts aligned on the same file.

**Non-Goals:**
- Case-insensitive matching of requirement *names* (RENAMED FROM/TO and MODIFIED name lookups keep exact-name comparison; only the header token's casing is relaxed, mirroring upstream).
- Any change to scenario (`#### Scenario:`) parsing — upstream's 1.4.0 note covers requirement headers only.
- New settings/toggles.

## Decisions

1. **Single shared pattern constant.** Introduce one `Pattern.CASE_INSENSITIVE` requirement-header pattern (e.g. in a small `SpecPatterns` holder or on `BuiltInValidator`) and reuse it across the five files rather than flipping flags in five places — the incident class here is exactly "matchers drift apart."
2. **Preserve canonical casing on write.** `SpecSyncService` matches case-insensitively but continues to *write* `### Requirement:` canonical casing for new/renamed headers. Rationale: sync output should not propagate non-canonical casing even when tolerating it on read.
3. **Keyword check evaluates the requirement body, not the header line.** Today the RFC-keyword scan runs over the whole requirement section, so a keyword appearing only in the header can satisfy it — the CLI errors on that same file. Split the check: body-contains-keyword → OK; header-only → new diagnostic `spec-rfc-keyword-in-header` (ERROR, message: "Move the RFC 2119 keyword from the requirement header into the requirement body"); nowhere → existing `spec-rfc-keywords`. This is a strictness increase but matches the CLI's verdict, which is the point of parity.
4. **Quick-fix scope.** In `DeltaSpecInspection`/`SpecFormatInspection`, the quick-fix for `spec-rfc-keyword-in-header` rewrites the first body line to start with "The <subject> SHALL …" only when it can do so mechanically — otherwise it inserts a template body line and leaves the header untouched. No AI involvement; deterministic text edit under a `WriteAction`.

## Risks / Trade-offs

- [Strictness increase from decision 3 flags previously-quiet files] → the diagnostic carries the CLI's own remediation text, and the CLI already errors on these files — the plugin was under-reporting, not the CLI over-reporting.
- [Case-insensitive header matching could double-match inside code fences] → patterns stay line-anchored (`^###`), same as today; fence handling is unchanged and out of scope.
- [Shared-pattern refactor touches five files] → mechanical, covered by existing validator/inspection/sync test suites plus new casing fixtures.

## Migration Plan

No data or config migration. Ship in the next minor release; `docs/openspec-support.md` rows for the two 1.4.0 items flip from Partial to supported.

## Open Questions

None — upstream behavior is confirmed against the CHANGELOG and the delta analysis; no CLI-output parsing is added, so no new contract fixtures are needed.

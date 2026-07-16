## Context

Upstream facts (verified against the installed 1.6.0 package source and live CLI this session): the validation stack is byte-identical across 1.4.0/1.4.1/1.5.0, so all divergence is 1.6-specific except the keyword family. At 1.6: the SHALL/MUST rule moved from a Zod refine (dot-path, substring, first-line-with-header-fallback) to an imperative rule — word-boundary `\b(SHALL|MUST)\b` over the full fence-masked multi-line body, no header fallback, with a targeted "move it to the body line" variant when the keyword sits only in the header; scenario counting became fence-aware; a new INFO rule (one of exactly two INFO rules in the product) flags skipped level-3 headers inside ADDED/MODIFIED delta sections, line-anchored (1-based, pointing at the header line), never affecting `valid` (strict mode included); `openspec validate <change>` validates delta specs only (proposal.md is validated solely by `openspec archive`, non-blocking).

Plugin side: `BuiltInValidator` already checks the full requirement body (multi-line), uses word boundaries, and ships the header-only targeted diagnostic + quick-fix (anticipated at the 1.4 alignment). Divergences: (1) its keyword pattern accepts `SHALL NOT|SHOULD NOT|SHALL|SHOULD|MAY` — a SHOULD-only requirement passes the plugin but fails every CLI generation; (2) keyword and scenario matching see inside fenced code blocks, which 1.6 masks; (3) non-canonical level-3 headers in delta sections are silently ignored — no INFO hint.

## Goals / Non-Goals

**Goals:**
- The plugin's verdict (valid/invalid) agrees with the 1.6 CLI's on the same content, for the rule classes the plugin implements.
- The INFO tier exists in the plugin with 1.6 semantics: advisory, line-anchored, never verdict-affecting.
- `1.6.x` is a formally declared supported CLI line in the spec contracts.

**Non-Goals:**
- Byte-identical message text, issue paths, or issue ordering with the CLI JSON — the plugin's messages are line-anchored IDE diagnostics with quick-fixes, deliberately richer than the CLI's item-level report. Parity is semantic (same accept/reject, same tier).
- Mirroring rules the plugin has never implemented (purpose-length WARNING, requirement-text-length INFO, proposal.md archive-preflight rules, dot/bracket double-reporting quirk).
- The Validate-action results rendering journey (tracked separately as the journey-7 item).
- Any `VersionSupport` enum change — that axis models the config-file format (pinned at 1.2.0), not the CLI line (standing decision).

## Decisions

1. **Parity means verdicts, not bytes.** The contract is: for a corpus of authored cases, plugin-valid ⇔ CLI-valid per case. Enforced by a verdict-parity contract test whose CLI side is a captured `validate --all --json` run of the real 1.6.0 CLI over the committed corpus (contract-test discipline; re-capture on CLI change). Alternative rejected: cloning CLI messages/paths verbatim — throws away line anchoring and quick-fixes, and chains the plugin to upstream's wording quirks (e.g. the dot/bracket double-report).
2. **Tighten the keyword family to SHALL/MUST.** The plugin's `SHOULD`/`MAY` acceptance was always laxer than the CLI; under verdict parity it must go. The diagnostic message keeps mentioning only SHALL/MUST. `SHALL NOT` still satisfies via the word-boundary `SHALL` match — same as the CLI's regex.
3. **Fence masking as a shared preprocessing step.** A single utility masks fenced code blocks (``` and ~~~, tilde/backtick, matching the CLI's fence detection) before keyword/scenario matching, applied in both the spec and delta paths. Line numbers are preserved by masking content rather than removing lines.
4. **INFO emission mirrors the CLI's two variants** — nameless `### Requirement:` header, and any other non-canonical level-3 header — in ADDED/MODIFIED sections only (REMOVED/RENAMED have no such sink upstream), anchored to the header's line, with plugin rule id `delta-skipped-header`. INFO issues never flip the plugin's verdict, matching upstream (strict included). The inspection layer maps INFO to a weak-warning-tier highlight.
5. **Support declaration is spec-level prose, not code.** `plugin-core` and `coordination-surfaces` deltas add the 1.6.x line/row; no gating code changes (the 1.5.0 store floor and schema-management gates are unchanged; 1.6 raises no floor).

## Risks / Trade-offs

- [Tightening SHOULD/MAY flags existing user specs that previously passed] → Correct behavior: those specs fail the real CLI today; the diagnostic message names the accepted keywords. CHANGELOG entry states it plainly.
- [Fence masking diverges from upstream's exact fence rules] → The verdict-parity corpus includes fence cases (keyword-only-in-fence, scenario-only-in-fence, fenced pseudo-header in a delta); disagreement fails the contract test.
- [INFO highlights could annoy in editors] → INFO maps to the weakest problem tier and never gates anything; it mirrors what `openspec validate --json` reports.

## Open Questions

None — upstream behavior verified against the 1.6.0 package source and live CLI before this proposal.

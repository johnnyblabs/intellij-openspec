# Delta — feedback-action

## REMOVED Requirements

### Requirement: Send feedback via the CLI

**Reason**: Product decision (2026-07-04): a feedback button inside the plugin invites channel confusion — `openspec feedback` reaches the upstream framework maintainers, but in-IDE placement reads as plugin feedback, routing complaints to the wrong audience in both directions. The feature was removed before its first release; no user ever saw it.

**Migration**: None required (never released). Users who want to reach the upstream framework maintainers run `openspec feedback` in a terminal; feedback about the plugin belongs on the plugin's own issue tracker (linked from the README).

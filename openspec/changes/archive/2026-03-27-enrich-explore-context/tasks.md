## 1. Config Context Enrichment

- [x] 1.1 Add `context` field to Project Config section in `appendConfigSummary()`
- [x] 1.2 Add `rules` as bulleted list to Project Config section

## 2. Spec Requirement Summaries

- [x] 2.1 Replace domain-name-only listing with requirement extraction — parse `### Requirement:` headers and description text from each `spec.md`
- [x] 2.2 Show requirement name and description (stop before first `#### Scenario:`)

## 3. Full Change Artifacts

- [x] 3.1 Replace truncated proposal with full artifact reading — read proposal.md, design.md, tasks.md, and delta specs for each active change
- [x] 3.2 Present each artifact under a sub-heading within the change section
- [x] 3.3 Remove `PROPOSAL_SUMMARY_MAX_LENGTH` constant and `appendProposalSummary()` method

## 4. Testing

- [x] 4.1 Test: config context and rules appear in assembled output
- [x] 4.2 Test: spec domains show requirement names and descriptions
- [x] 4.3 Test: active changes include full artifact content
- [x] 4.4 Test: missing artifacts are silently skipped

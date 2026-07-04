# Tasks — Expand the automated UI walkthroughs

## 1. Fixture

- [x] 1.1 `scripts/seed-lifecycle-demo.sh`: add `openspec/specs/keyword-in-header/spec.md` (RFC keyword only in the requirement header); regenerate and commit the testData fixture from the script
- [x] 1.2 Walkthrough template: note which manual stops are covered by which automated journey

## 2. Journeys

- [x] 2.1 Resolve the 242 `DaemonCodeAnalyzer` driver-facade signatures from the jars (round-one discipline: jars over docs)
- [x] 2.2 Editor validator-parity journey: lowercase header clean; keyword-in-header spec shows the targeted diagnostic (matched by its unique message fragment)
- [x] 2.3 ~~Feedback dialog guard journey~~ DROPPED: the feedback feature itself was withdrawn by product decision (change remove-feedback-action) — no journey for a removed feature
- [x] 2.4 Archive guard journey: confirmation surface present on the 1/4-complete change, cancel, change directory unmoved (filesystem check on the temp fixture copy)
- [x] 2.5 All five journeys verified green locally (the suite's own standard)

## 3. Documentation

- [x] 3.1 Testing-layers section: journey list updated to five; spec delta synced at archive

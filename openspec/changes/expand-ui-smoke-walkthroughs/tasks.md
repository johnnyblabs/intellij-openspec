# Tasks — Expand the automated UI walkthroughs

## 1. Fixture

- [ ] 1.1 `scripts/seed-lifecycle-demo.sh`: add `openspec/specs/keyword-in-header/spec.md` (RFC keyword only in the requirement header); regenerate and commit the testData fixture from the script
- [ ] 1.2 Walkthrough template: note which manual stops are covered by which automated journey

## 2. Journeys

- [ ] 2.1 Resolve the 242 `DaemonCodeAnalyzer` driver-facade signatures from the jars (round-one discipline: jars over docs)
- [ ] 2.2 Editor validator-parity journey: lowercase header clean; keyword-in-header spec shows the targeted diagnostic (matched by its unique message fragment)
- [ ] 2.3 Feedback dialog guard journey: dialog present, empty message blocks OK, Escape out, nothing sent
- [ ] 2.4 Archive guard journey: confirmation surface present on the 1/4-complete change, cancel, change directory unmoved (filesystem check on the temp fixture copy)
- [ ] 2.5 All six journeys verified green locally (the suite's own standard)

## 3. Documentation

- [ ] 3.1 Testing-layers section: journey list updated to six; spec delta synced at archive

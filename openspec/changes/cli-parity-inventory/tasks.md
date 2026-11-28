## 1. Inventory

- [ ] 1.1 `docs/cli-parity.md` (Maintenance: Living), with rows for every port the merged changes touched (D1), linked from `docs/openspec-support.md` and `docs/README.md`.
- [ ] 1.2 The inventory test (D2), with the "not a port" list.

## 2. Rules and triggers

- [ ] 2.1 openspec-guru and test-engineer definitions (D4).
- [ ] 2.2 `CLAUDE.md`: the sweep as an agent-routing trigger point, and in the release gates.
- [ ] 2.3 The clone-local `release-prep` skill lists the sweep with its BLOCKER gates.

## 3. First sweep

- [ ] 3.1 Run the sweep over every row and fill in the table. File each finding as its own tracker entry. None is fixed in this change.

## 4. Verification

- [ ] 4.1 `./gradlew test` (the inventory test), `DocumentationHygieneTest`, leak check, and `openspec validate --all --strict`.

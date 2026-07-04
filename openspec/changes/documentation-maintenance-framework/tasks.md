## 1. Per-doc maintenance classification

- [x] 1.1 Define the four maintenance classes (Living / Snapshot / Reference / Retired) and their contracts in the `docs/README.md` index (see task 2).
- [x] 1.2 Add a one-line `Maintenance: <class>` header near the top of every doc under `docs/` and the top-level README / CHANGELOG / CONTRIBUTING. Classify each: coverage matrix, feature reference, README, CHANGELOG = Living; competitive comparison matrix = Snapshot; getting-started guides + marketplace page + CONTRIBUTING = Reference.

## 2. Documentation index / map

- [x] 2.1 Create `docs/README.md` as the documentation map: a table listing every doc with its purpose, primary audience, and maintenance class, plus the four-class legend. Disambiguate the two "matrix" docs (coverage vs competitive) explicitly.
- [x] 2.2 Link the index from the top-level README so it is discoverable.

## 3. Single source of truth for version/support facts

- [x] 3.1 Designate the "Version support" block in `docs/openspec-support.md` as the canonical statement of current plugin version, minimum/baseline/supported CLI versions, and mark it as such with an anchor (`#version-support`) plus an explicit current-plugin-version bullet.
- [x] 3.2 Replace hard-coded version restatements in other docs with a link to that canonical block, except where a version is intrinsic to the doc's content (e.g. a CHANGELOG heading). The README's minimum-CLI restatement now links to the canonical block; feature-reference and the comparison matrix link to it as well. Conservative — narrative context retained, only the duplicated version list points home.

## 4. Documentation-tier canonicality

- [x] 4.1 State in `docs/README.md` that repo markdown is canonical and the wiki / knowledge base are published mirrors that SHALL NOT be hand-edited to diverge. (The process-side CLAUDE.md documentation-fidelity guidance already carries the same rule at the workflow layer; not duplicated into the public repo CLAUDE.md.)

## 5. Currency enforcement

- [ ] 5.1 Extend the release readiness checklist (`release-prep`) with two doc-currency checks (stale `Snapshot` doc; Living doc not updated with its code area). Deferred: the `release-prep` skill is a local-only, gitignored surface not present in this worktree — to be extended in a local session, not this docs change.
- [ ] 5.2 Extend the "documentation fidelity per change" guidance (CLAUDE.md) to cover Snapshot/orphan docs and the canonicality tier. Deferred: process/CLAUDE.md surface, out of scope for this docs-only change; the canonicality tier is stated in `docs/README.md` (task 4.1).

## 6. Documentation

- [x] 6.1 Update the top-level README to link the new documentation index.
- [x] 6.2 Add a CHANGELOG entry under the unreleased section noting the documentation-maintenance framework (developer-facing; kept brief).

## 7. Tests

- [x] 7.1 Add a doc-hygiene check asserting that **every** tracked doc under `docs/` and the top-level README/CHANGELOG/CONTRIBUTING carries a recognized `Maintenance:` label; it FAILS if a doc lacks one or uses an unknown class. (`DocumentationHygieneTest.everyDocDeclaresARecognizedMaintenanceClass`)
- [x] 7.2 Add a check asserting the `docs/README.md` index references every doc file under `docs/` (no orphaned or missing entries) and that every index link points to an existing doc. (`DocumentationHygieneTest.indexReferencesEveryDocAndEveryEntryExists`)
- [x] 7.3 Add a check asserting no doc other than the canonical block (and CHANGELOG headings) restates a plugin-version literal that has drifted from the canonical source; it FAILS on a re-introduced stale `plugin vX.Y.Z` restatement. (`DocumentationHygieneTest.noDocRestatesADriftedPluginVersion`)

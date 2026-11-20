## 1. Pilot

- [x] 1.1 Shorten the long requirement in `pipeline-interaction` (Compact icon action bar) to 500 characters or fewer, keeping its normative statements and all seven scenarios.
- [x] 1.2 Confirm the pilot delta validates strictly on its own.

## 2. Batches

- [ ] 2.1 Specs with one long requirement: `bulk-archive`, `changelog-automation`, `documentation`, `schema-management`, `tracker-integration`, `workflow-schema-context`.
- [ ] 2.2 Specs with two long requirements: `cross-platform-verification`, `spec-sync`, `tree-view`, `ui-smoke-journeys`, `verify-workflow`.
- [ ] 2.3 Specs with three long requirements: `cli-update`, `config-profile`, `edt-compliance`, `profile-visibility`, `spec-parsing`, `store-workset-actions`, `store-workset-surface`.
- [ ] 2.4 Specs with four or more long requirements: `ai-integration`, `plugin-documentation`, `ci`, `coordination-surfaces`, `spec-viewer`, `plugin-core`, `workflow`, `validation`.

## 3. Verify

- [ ] 3.1 Each batch validates with `openspec validate shorten-long-requirements --strict`.
- [ ] 3.2 After the last batch, `openspec validate --all --strict` passes.

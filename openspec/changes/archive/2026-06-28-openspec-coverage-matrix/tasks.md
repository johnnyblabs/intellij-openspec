# Tasks

## 1. Publish the coverage matrix
- [x] 1.1 Create `docs/openspec-support.md` — grouped tables (lifecycle workflows, model & CLI surfaces, schemas & profiles, coordination layers, IDE value-add), each row with support status + CLI-version annotation.
- [x] 1.2 Include the version-support contract (floor 1.3.0, baseline 1.4.x, runtime-version-awareness, config-format-vs-CLI-version caveat), verified from a 1.3.1 ↔ 1.4.0 comparison.
- [x] 1.3 Include a lifecycle mermaid diagram and a dependency-ordered roadmap.

## 2. Link + capability
- [x] 2.1 Link the matrix from `README.md` (under Links).
- [x] 2.2 Add the `plugin-documentation` delta requiring a published, version-aware, vendor-neutral coverage matrix.

## 3. Validate
- [x] 3.1 `openspec validate openspec-coverage-matrix --strict` — green.
- [x] 3.2 Leak-grep the published surfaces (`docs/openspec-support.md`, proposal/design/tasks, README) — vendor-neutral, clean.

## 4. Close the loop
- [ ] 4.1 Mirror to the internal tracker; create the epic/phase grouping there (out of the published artifacts).
- [ ] 4.2 Propose Phase-1/Phase-2 child changes (schema-aware Verify first) as separate changes.

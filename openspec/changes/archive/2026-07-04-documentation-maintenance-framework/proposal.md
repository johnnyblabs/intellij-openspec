## Why

The project's docs split cleanly into two camps, and the split is entirely about whether a doc is **wired into a trigger**:

- **Living docs** (coverage matrix, feature reference, CHANGELOG, README) stay accurate — the "documentation fidelity per change" discipline keeps pulling them forward.
- **Orphaned docs** rot the moment nobody is obligated to touch them. The competitive comparison matrix is the live example: it went ~4 months stale (declared v0.2.3 while the plugin shipped 0.3.1) **and** leaked an internal username as its publisher — which slipped past a too-narrow leak-guard pattern.

Three structural gaps let this happen and will let it recur:
1. **No explicit maintenance contract per doc** — nothing declares whether a doc is expected to be per-change-current, periodically reviewed, or stable, so orphans are indistinguishable from living docs until they rot.
2. **Facts are restated, not referenced** — version/CLI numbers appear in 5+ docs independently and drift.
3. **No documentation map** — a reader can't tell which of two similarly-named "matrix" docs is which, or where to view them.

This change establishes a lightweight maintenance framework so every doc has an explicit contract and currency is enforced, not merely disciplined. (The immediate leak scrub + leak-guard hardening ship in the companion change `docs: scrub leaked username, refresh competitive matrix, harden leak guard`; this change is the durable structure.)

## What Changes

- **Per-doc maintenance classification.** Every doc under `docs/` (and the top-level README/CHANGELOG/CONTRIBUTING) SHALL carry a one-line `Maintenance:` label near the top — one of **Living** (updated per relevant change), **Snapshot** (reviewed on a stated cadence, may lag), **Reference** (stable, updated only when the described thing changes), or **Retired** (kept for history, not maintained). This makes each doc's contract explicit and auditable.
- **A documentation index/map** at `docs/README.md` listing every doc with its purpose, audience, and maintenance class — resolving "which doc is which / where do I view it."
- **Single source of truth for version/support facts.** The coverage matrix's version-support block (`docs/openspec-support.md`) is the canonical statement of minimum/baseline/supported CLI versions and current plugin version; other docs SHALL link to it rather than restating specific version numbers.
- **Documentation-tier canonicality.** Repo markdown is the canonical source; any published documentation mirror (a project wiki, a team knowledge base) is generated from it and SHALL NOT be hand-edited to diverge.
- **Currency enforcement in release prep.** The release checklist SHALL flag (a) any `Snapshot` doc whose last change predates the previous release, and (b) a release that changed a tracked code area without touching its declared Living doc — turning discipline into a gate.

## Capabilities

### Modified Capabilities
- `plugin-documentation`: adds a documentation-maintenance framework — per-doc maintenance classification, a documentation index, a single source of truth for version facts, documentation-tier canonicality, and release-time currency enforcement.

## Impact

- **Docs:** a new `docs/README.md` index; a `Maintenance:` header line added to each doc; version restatements replaced with links to the coverage matrix's canonical block.
- **Process:** the release readiness checklist gains two doc-currency checks; the "documentation fidelity per change" rule is extended to cover Snapshot/orphan docs and the canonicality tier.
- **Enforcement:** a lightweight doc-hygiene check (every doc has a maintenance label; the index references every doc) — automatable in CI as a non-blocking report.
- **No code changes.** Vendor-neutral; nothing environment-specific reaches published surfaces.

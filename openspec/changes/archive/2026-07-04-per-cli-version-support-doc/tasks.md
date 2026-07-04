## 1. Per-version analysis artifact + index

- [x] 1.1 Produce `docs/cli-versions/1.4.md` — the 1.3→1.4 feature-delta & plugin-impact analysis, cited to Fission upstream, with introduced/modified/deprecated/removed → mechanics → supportability + UI → consumer how-to → decisions → open questions → sources. (First instance + template; produced via an explore research pass.)
- [x] 1.2 Create `docs/cli-versions/README.md` — an index of the per-version analyses (Maintenance: Reference), linked from `docs/README.md`.

## 2. plugin-documentation spec — the enforcement requirement

- [x] 2.1 Apply the `plugin-documentation` delta: ADD "Per-CLI-version feature-delta analysis grounds support decisions" (exists before/with support; cites upstream, quarantines unverified; per-feature supportability + UI assessment; decisions reference it; kept current; produced via explore).

## 3. Fixes the 1.3→1.4 analysis surfaced (openspec-support.md)

- [x] 3.1 Correct the `RENAMED` delta attribution: it was introduced upstream in 1.0.0 (all four delta types together), not a "1.4 client addition."
- [x] 3.2 Qualify the `set` command as unverified against upstream (no upstream confirmation found; pending check on a real 1.4.x CLI) rather than asserting it as a 1.4 addition.

## 4. Follow-ups recorded (NOT in scope here)

- [ ] 4.1 Track: mirror the two durable 1.4 validator improvements (case-insensitive requirement headers, SHALL/MUST-in-header hint) into the built-in validator.
- [ ] 4.2 Track: produce the 1.4→1.5 analysis (`docs/cli-versions/1.5.md`) next.
- [ ] 4.3 Track: verify the `openspec set` command against a real 1.4.x CLI.

## 5. Verification

- [x] 5.1 `openspec validate per-cli-version-support-doc --strict` passes.
- [x] 5.2 Anti-leak scan of the new/changed docs (public mirror): clean.

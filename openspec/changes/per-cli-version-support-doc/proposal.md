## Why

The plugin commits to mirroring the OpenSpec CLI faithfully across supported versions, but nothing captures **what actually changed between CLI versions** — features introduced, modified, deprecated, or removed — or forces that understanding to be grounded in Fission's upstream documentation. The 1.5.0 work was reactive: the coordination-command removal was discovered by tripping over it, not by reading a per-version analysis first. Without such an analysis:

- Support decisions are made from assumption rather than cited fact (the plugin's own docs carried at least two: an incorrect "RENAMED is a 1.4 addition" attribution, and an unverified `openspec set` command).
- There is no consumer-facing explanation of how each version's features work, whether the plugin surfaces them, and how to use them.
- The capability-preservation contract has no epistemic base — you cannot faithfully mirror a client version you have not analysed.

## What Changes

- **A per-CLI-version feature-delta & plugin-impact analysis** lives under `docs/cli-versions/`, one per version, produced via an OpenSpec **explore** over upstream docs and **cited**. Each catalogs introduced/modified/deprecated/removed features, their mechanics, a plugin-supportability + UI-component assessment, and a consumer how-to; unverifiable claims are quarantined, not asserted. `docs/cli-versions/1.4.md` (the 1.3→1.4 analysis) is the first instance and template.
- **`plugin-documentation` gains a requirement** that such an analysis grounds support decisions: it SHALL exist before/with support for a version, cite upstream, assess plugin impact + UI per feature, drive decisions (not assumption), and stay current. This is the epistemic base of the capability-preservation contract.
- **Two fixes the 1.3→1.4 analysis surfaced** land in `docs/openspec-support.md`: the `RENAMED` delta type is corrected (introduced upstream in 1.0.0, not a "1.4 client addition"), and the `set` command is qualified as unverified against upstream (pending confirmation on a real 1.4.x CLI).
- **A `docs/cli-versions/` index** lists the per-version analyses.

## Capabilities

### Modified Capabilities
- `plugin-documentation`: adds the per-CLI-version feature-delta analysis requirement (cited, decision-grounding, kept current) as the epistemic base for version support.

## Impact

- **Docs:** new `docs/cli-versions/1.4.md` (the 1.3→1.4 analysis) + `docs/cli-versions/README.md` index; two corrections in `docs/openspec-support.md` (RENAMED attribution, `set` qualification).
- **Spec:** one ADDED requirement in `plugin-documentation`.
- **Process:** targeting a new CLI version begins with an explore that produces the analysis; the implementing changes reference it. Enforced by review and the docs-currency framework.
- **Follow-ups recorded, not in scope here:** (a) mirror the two durable 1.4 validator improvements (case-insensitive requirement headers, SHALL/MUST-in-header hint) into the built-in validator; (b) produce the 1.4→1.5 analysis next; (c) verify the `set` command against a real 1.4.x CLI.
- **No code changes.** Vendor-neutral (publishes to the public mirror).

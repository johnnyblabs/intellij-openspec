# OpenSpec CLI — Per-Version Feature-Delta Analyses

> **Maintenance: Reference** — index of the per-CLI-version feature-delta & plugin-impact analyses. Updated when a new analysis is added.

Each document here analyses one OpenSpec CLI version relative to the prior supported version: what the upstream client **introduced, modified, deprecated, or removed**, how each feature works in practice, whether the plugin can and should surface it (and whether a UI component exists), and how a consumer uses it. Every upstream claim is **cited to Fission's OpenSpec documentation**; unverifiable claims are quarantined, not asserted.

These analyses are the **epistemic base** for the plugin's version support — support decisions reference them rather than assumption (see the `plugin-documentation` capability, "Per-CLI-version feature-delta analysis grounds support decisions", and the `plugin-core` capability-preservation contract). They are produced via an OpenSpec **explore** research pass when a new CLI version is targeted.

| Version | Analysis | Covers |
|---|---|---|
| 1.4 | [1.4.md](1.4.md) | 1.3 → 1.4 delta (parser/validation fixes; the beta coordination model + `workspace-planning` schema; new skills-only assistants) |

*Planned: `1.5.md` (1.4 → 1.5 — stores/worksets replace the coordination model).*

For the plugin's at-a-glance support matrix (capability × support status), see [OpenSpec Client Coverage](../openspec-support.md). This directory is the reasoned analysis behind those support decisions.

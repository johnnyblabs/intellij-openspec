# OpenSpec Client Coverage

How this plugin maps to the [OpenSpec](https://github.com/fission-ai/openspec) client — what's supported, what's partial, and what's on the roadmap. The plugin's goal is to be a **faithful companion to the OpenSpec client**, not a divergent reimplementation, so this matrix is the project's parity north-star.

**Status:** ✅ Supported · 🟡 Partial · ⚠️ Divergent (reimplemented, being aligned) · 🔜 Planned · 🧩 Plugin-original (no client equivalent)

**CLI column** — the relationship to the installed OpenSpec CLI:
- `built-in` — the plugin implements this itself; works regardless of CLI (even with no CLI installed)
- `1.3+` — needs the CLI floor
- `1.4+` — needs a 1.4-line CLI
- `delegated` — runs against the CLI at runtime; degrades to a built-in path / guidance below the floor

## Version support

- **Minimum CLI: 1.3.0.** Below the floor, the plugin shows a one-time upgrade nudge and degrades gracefully to its built-in paths (project detection, init, spec browser, tool window, validation).
- **Baseline: the 1.4.x line** (tested against 1.4.1).
- The plugin is **runtime-version-aware**: recognized schema names are the union of its built-in set and the live `openspec schemas` list, and version-sensitive behavior is gated on the detected CLI version.
- **Independent axis:** the checked-in config format (`openspec/config.yaml` `version: 1.2.0`) is *not* the CLI version. It is unchanged across CLI 1.2.x / 1.3.x / 1.4.x.

> Verified by comparing CLI 1.3.1 ↔ 1.4.0: all change-lifecycle workflows (incl. `verify-change`) and the `status` / `instructions` / `templates` / `schemas` / `validate` / `show` commands exist at the 1.3 floor. The `workspace-planning` schema and the `workspace` / `context-store` / `initiative` / `set` commands are 1.4 additions.

## Change-lifecycle workflows

| Workflow | Status | CLI | Notes |
|----------|--------|-----|-------|
| propose / new | ✅ | built-in | Built-in scaffolding (full artifact set) + AI skill |
| explore | ✅ | built-in · `instructions` | Thinking-space panel + AI skill |
| apply-change | ✅ | built-in · `status` | Implement-tasks action + AI skill |
| continue-change | ✅ | built-in · `status` | Resume action |
| ff-change | ✅ | built-in | Fast-forward action |
| sync-specs | ✅ | built-in | Built-in delta→main spec sync + AI skill |
| archive-change | ✅ | `delegated` | Archive action (applies deltas + moves) + AI skill |
| bulk-archive | ✅ | built-in | Bulk archive action |
| **verify-change** | ⚠️ | `1.3+` (target: `delegated`) | Currently a local, schema-blind heuristic. Being rebuilt to drive off `openspec status` / `instructions` so it matches the real workflow across schemas. |
| onboard | 🟡 | built-in | Plugin's own Setup Wizard, not the OpenSpec `onboard` workflow |
| feedback | 🔜 | `1.3+` | No surface yet for `openspec feedback` |

## Model & CLI surfaces

| Surface | Status | CLI | Notes |
|---------|--------|-----|-------|
| init / update / list / show | ✅ | built-in · `delegated` | Actions + tool-window tree |
| validate | ✅ | built-in | Built-in delta-spec + config validation, aligned to the client's rules |
| delta specs (ADDED/MODIFIED/REMOVED/RENAMED) | ✅ | built-in | Create / inspect / diff / sync. `RENAMED` support tracks the 1.4 client addition but is implemented built-in (works at the floor). |
| status | 🟡 | `1.3+` | Used by apply/continue/list; not yet by Verify |
| instructions | 🟡 | `1.3+` | Used by Explore; not yet broadly |
| view (dashboard) | 🟡 | built-in | Plugin tool-window tree rather than the CLI `view` dashboard |
| templates | 🔜 | `1.3+` | Not surfaced |

## Schemas & profiles

| Capability | Status | CLI | Notes |
|------------|--------|-----|-------|
| `spec-driven` schema | ✅ | `1.3+` | Fully supported |
| `workspace-planning` schema | 🟡 | `1.4+` | Recognized & validated; workflow surfaces now detect and reflect the active `actionContext.mode` (foundation landed) — full per-mode UX (incl. Verify) still in progress |
| custom / forked schemas | 🟡 | `1.3+` | A forked schema name is recognized rather than flagged unknown |
| profiles / config | ✅ | `delegated` | CLI-aligned profile picker; config validation |

## Coordination layers

> Entirely **1.4 client additions** — not yet surfaced by the plugin. The largest frontier.

| Capability | Status | CLI | Notes |
|------------|--------|-----|-------|
| workspace | 🔜 | `1.4+` | Local state is readable; no plugin surface |
| context-store | 🔜 | `1.4+` | Local state is readable; no plugin surface |
| initiative | 🔜 | `1.4+` | No support |

## IDE value-add (plugin-original)

| Capability | Status | Notes |
|------------|--------|-------|
| Spec syntax highlighting | ✅ 🧩 | RFC-2119 + scenario keyword highlighting |
| Delta-spec inline inspection | ✅ 🧩 | Real-time structural checks + quick-fixes |
| Delta-spec diff viewer | ✅ 🧩 | Side-by-side delta vs main spec |
| Tool-window workflow panel | ✅ 🧩 | Change tree + workflow actions |
| Setup wizard | ✅ 🧩 | Guided onboarding & CLI detection |

## Lifecycle at a glance

```mermaid
stateDiagram-v2
    [*] --> Proposed: propose / explore
    Proposed --> InProgress: apply / continue / ff
    InProgress --> Verified: verify-change
    Verified --> Archived: archive (sync deltas → specs)
    Archived --> [*]
    InProgress --> Proposed: revise
```

## Roadmap

The frontier, in dependency order:

1. **Foundation — schema/version awareness.** Make workflow surfaces drive off `openspec status` / `instructions` (the schema + `actionContext.mode`) instead of assuming a `spec-driven` layout. Unblocks faithful Verify and correct behavior on non-default schemas.
2. **Workflow-surface fidelity.** Rebuild **Verify** as a faithful `verify-change` surface (semantic, language-agnostic, schema-aware); fill remaining workflow gaps (`feedback`, an `onboard`-aligned path).
3. **Coordination layers (1.4).** Surface `workspace` / `context-store` / `initiative` for cross-area / multi-repo coordination.

> Each row's "delivered by" history lives in [`CHANGELOG.md`](../CHANGELOG.md) and the archived OpenSpec changes under `openspec/changes/archive/`.

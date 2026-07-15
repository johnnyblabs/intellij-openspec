# OpenSpec Client Coverage

How this plugin maps to the [OpenSpec](https://github.com/fission-ai/openspec) client — what's supported, what's partial, and what's on the roadmap. The plugin's goal is to be a **faithful companion to the OpenSpec client**, not a divergent reimplementation, so this matrix is the project's parity north-star.

> **Maintenance: Living** — updated as part of every relevant change (see the [documentation index](README.md)). This doc's [Version support](#version-support) block is the canonical source of truth for CLI/plugin version facts.

**Status:** ✅ Supported · 🟡 Partial · ⚠️ Divergent (reimplemented, being aligned) · 🔜 Planned · 🧩 Plugin-original (no client equivalent)

**CLI column** — the relationship to the installed OpenSpec CLI:
- `built-in` — the plugin implements this itself; works regardless of CLI (even with no CLI installed)
- `1.3+` — needs the CLI floor
- `1.4+` — needs a 1.4-line CLI
- `1.4.x` — exists only in the `[1.4.0, 1.5.0)` window (the command was removed in 1.5.0)
- `delegated` — runs against the CLI at runtime; degrades to a built-in path / guidance below the floor

<a id="version-support"></a>
## Version support

This section is the **single source of truth** for the plugin's per-CLI-version coordination behavior and version facts (current plugin version, minimum/baseline/supported CLI versions). Other docs SHALL link here rather than restating these numbers. Each supported line has an explicit, test-enforced contract (see the per-version behavior contract in `coordination-surfaces`); a change that alters a line's behavior must update this block and its per-version tests.

- **Current plugin version: 0.3.1** (authoritative source: `build.gradle.kts` / the JetBrains Marketplace listing; restated here so other docs have one place to link).
- **Minimum CLI: 1.3.0.** Below the floor, the plugin shows a one-time upgrade nudge and degrades gracefully to its built-in paths (project detection, init, spec browser, tool window, validation).
- **`1.3.x`:** coordination is below its floor — the Coordination tab is read-only (Awareness) only if legacy on-disk state exists, else Hidden; **no coordination write actions**.
- **`1.4.x` (baseline, tested against 1.4.1):** live coordination reads (`workspace` / `context-store` / `initiative`) **plus IDE write actions** at the Full tier — New Initiative, Set Up Context Store, Set Up Workspace — gated to the `[1.4.0, 1.5.0)` window. These write actions are **self-retiring**: CLI 1.5.0 removed the underlying commands, so they disappear on a 1.5 upgrade.
- **`1.5.x` line:** CLI 1.5.0 replaced the 1.4 coordination commands with the **store / workset** model. The plugin surfaces that model above a `1.5.0` store floor (evaluated from the detected CLI version), with a built-in fallback that reads the global data dir directly. At the Full tier it exposes CLI-delegated **store/workset write actions** (store setup/register/unregister/remove, workset create/open/remove) and a `store doctor`-driven health strip. The legacy 1.4 write actions are not offered here.
- **`1.6.x` line:** the store/workset model and all JSON shapes are unchanged from 1.5, but **store-health semantics changed**: a fresh/config-only store root (no `openspec/specs`, `openspec/changes`, or `openspec/changes/archive` yet) now **registers successfully** and `store doctor` reports it **healthy** with per-directory `present: false` detail — 1.5 refused the same root at register (`store_register_root_unhealthy`, a code 1.6 no longer emits, along with the retired `openspec_{specs,changes,archive}_missing` diagnostics). Registering a root whose `openspec/config.yaml` declares `store:` is refused with new codes (`store_root_pointer_declared`, `invalid_store_pointer`), and registering a never-registered root asks for identity confirmation. The plugin reads health solely from the CLI's own `healthy` flag, so healthy-empty stores list without any error marker, and register refusals surface the CLI's message and `fix` verbatim on either generation. No new gate: the `1.5.0` store floor is unchanged.
- The plugin is **runtime-version-aware**: recognized schema names are the union of its built-in set and the live `openspec schemas` list, and version-sensitive behavior is gated on the detected CLI version.
- **Independent axis:** the checked-in config format (`openspec/config.yaml` `version: 1.2.0`) is *not* the CLI version. It is unchanged across CLI 1.2.x / 1.3.x / 1.4.x.

> Verified by comparing CLI 1.3.1 ↔ 1.4.0: all change-lifecycle workflows (incl. `verify-change`) and the `status` / `instructions` / `templates` / `schemas` / `validate` / `show` commands exist at the 1.3 floor, as do the `schema which` / `schema validate` subcommands (re-verified empirically on 1.3.1, 2026-07-04 — the schema tooling surface needs no gate beyond the 1.3.0 floor). The `workspace-planning` schema and the `workspace` / `context-store` / `initiative` commands are 1.4 additions (see [`cli-versions/1.4.md`](cli-versions/1.4.md) for the cited analysis). The `openspec set` command is **confirmed on the 1.4 line** (verified on a real 1.4.1 CLI, 2026-07-04): `set change <name> --initiative <id> [--store <id> | --store-path <path>] [--json]` links a repo-local change to an initiative — coordination-beta machinery removed in CLI 1.5.0, deliberately given no plugin surface.
>
> **CLI 1.5.0 removed the `workspace` / `context-store` / `initiative` commands and the `workspace-planning` schema** (replaced by the `store` / `workset` model). The plugin's built-in schema set is therefore `spec-driven` only, and coordination is gated to the `[1.4.0, 1.5.0)` window — on a 1.5.0+ CLI the plugin never invokes the removed commands and the Coordination tab stands down (read-only Awareness if legacy on-disk state exists, Hidden otherwise).

## Change-lifecycle workflows

| Workflow | Status | CLI | Notes |
|----------|--------|-----|-------|
| propose / new | ✅ | built-in | Built-in scaffolding (full artifact set) + AI skill |
| explore | ✅ | built-in · `instructions` | Thinking-space panel + AI skill |
| apply-change | ✅ | built-in · `status` | Implement-tasks action + AI skill |
| continue-change | ✅ | built-in · `status` | Resume action |
| ff-change | ✅ | built-in | Fast-forward action |
| sync-specs | ✅ | built-in | Built-in delta→main spec sync + AI skill |
| archive-change | ✅ | built-in | Archive action (applies deltas + moves via VFS, no CLI call) + AI skill |
| bulk-archive | ✅ | built-in | Bulk archive action |
| **verify-change** | 🟡 | `1.3+` · `delegated` | Rebuilt to be schema/mode-aware (drives off `openspec status` — the `actionContext.mode` gate *and* the artifact-level completeness check, which reads the status DAG's own artifact set instead of assuming a fixed spec-driven layout; task-checkbox granularity stays locally parsed from `tasks.md`, and a filesystem fallback covers CLI-less environments) and **language-agnostic**: semantic correctness/coherence is delegated to the AI bridge. Non-default modes (e.g. `workspace-planning`) explain and stop. The old Java-only code heuristic is retired. |
| onboard | 🟡 | built-in | Plugin's own Setup Wizard, not the OpenSpec `onboard` workflow |
| feedback | — | n/a | **Deliberately not surfaced** (decision 2026-07-04): `openspec feedback` reaches the upstream framework maintainers, but an in-IDE button reads as plugin feedback — channel confusion in both directions. Terminal users run `openspec feedback` directly; plugin feedback belongs on the plugin's issue tracker. Do not re-propose without revisiting the recorded decision. |

## Model & CLI surfaces

| Surface | Status | CLI | Notes |
|---------|--------|-----|-------|
| init / update / list / show | ✅ | built-in · `delegated` | Actions + tool-window tree. `update`'s skills-migration "legacy files pending" outcome (exit 0 + `--force`/interactive ask) gets a graceful in-IDE resolution: consented per-file removal via undoable VFS ops, terminal handoff, no-nag dismissal, and truthful loop detection when the CLI regenerates the files it flags. The plugin never runs `update --force` on the user's behalf. |
| validate | ✅ | built-in | Built-in delta-spec + config validation, aligned to the client's rules — including the two permanent CLI 1.4.0 parser/validator behaviors: requirement headers are recognized **case-insensitively** everywhere the plugin parses them, and an RFC 2119 keyword appearing only in a requirement's header gets the CLI's targeted "move the keyword onto the requirement body line" diagnostic (`spec-rfc-keyword-in-header`) with a quick-fix |
| delta specs (ADDED/MODIFIED/REMOVED/RENAMED) | ✅ | built-in | Create / inspect / diff / sync. All four delta types (incl. `RENAMED`) were introduced upstream in **1.0.0**, well below the plugin's floor; implemented built-in (works at the floor). |
| status | ✅ | `1.3+` | Used by apply/continue/list and Verify (mode gate + artifact-level completeness, with a filesystem fallback below the floor) |
| instructions | 🟡 | `1.3+` | Used by Explore; not yet broadly |
| view (dashboard) | 🟡 | built-in | Plugin tool-window tree rather than the CLI `view` dashboard |
| templates | ✅ | `1.3+` · `delegated` | Open Templates action in Settings resolves each artifact's template via `templates --json` and opens it in the editor |

## Schemas & profiles

| Capability | Status | CLI | Notes |
|------------|--------|-----|-------|
| `spec-driven` schema | ✅ | `1.3+` | Fully supported |
| `workspace-planning` schema | 🟡 | `1.4+` | Recognized & validated; workflow surfaces detect and reflect the active `actionContext.mode`, and Verify mode-gates it (explains repo-local verify N/A). Full per-mode authoring UX still in progress |
| custom / forked schemas | ✅ | `1.3+` | A forked schema name is recognized rather than flagged unknown; full authoring loop in Settings — fork/create, **Validate** (`schema validate --json`, problems rendered inline), resolution provenance (`schema which --json`, origin tag incl. shadowing), and Open Templates |
| profiles / config | ✅ | `delegated` | CLI-aligned profile picker; config validation |

## Coordination layers

> **1.4-line client commands** (`workspace` / `context-store` / `initiative`), surfaced by the plugin's **Coordination** tab — CLI-sourced (`list`/`doctor`) within the window `[1.4.0, 1.5.0)`, with a built-in fallback that reads the global data dir directly. **CLI 1.5.0 removed these commands** (replaced by the store/workset model), so on a 1.5.0+ CLI the plugin never invokes them: the tab stands down to read-only Awareness when legacy on-disk state exists, and Hidden otherwise. The tab is shown only when coordination state or a coordination mode is detected.

| Capability | Status | CLI | Notes |
|------------|--------|-----|-------|
| workspace | ✅ | `1.4.x` | Listed with resolution health; set-up action (Full tier); read-only fallback from the on-disk registry. Removed in CLI 1.5.0 → tab stands down. |
| context-store | ✅ | `1.4.x` | Listed with id/root and doctor health; set-up action (Full tier — the 1.4 line has no register action in the plugin; register/unregister/remove exist only for 1.5 stores); read-only fallback. Removed in CLI 1.5.0 → tab stands down. |
| initiative | ✅ | `1.4.x` | Listed with lifecycle status badge; artifacts open in the editor; create action (Full tier); read-only fallback from `initiative.yaml`. Removed in CLI 1.5.0 → tab stands down. |

## Stores & worksets (1.5)

> **1.5-line client model** (`store` / `workset`), replacing the 1.4 coordination layer. Surfaced by the Coordination tab when the detected CLI is at or above the `1.5.0` store floor — CLI-sourced (`store list` / `store doctor` / `workset list`) with a built-in fallback that reads the global data dir directly (`stores/registry.yaml`, `worksets/worksets.yaml`). The store registry is byte-identical in shape to the 1.4 context-store registry, so the same backend-local-path reader serves both. At CLI ≥ `1.5.0`, stores/worksets are the lead model and any surviving pre-1.5 state is demoted to a muted, read-only "Legacy (pre-1.5)" group. At the **Full tier** the tab exposes CLI-delegated **write actions** (New/Register/Unregister/Remove store — Remove is guarded as destructive; New/Open/Remove workset) plus a `store doctor`-driven health strip surfacing each diagnostic's `fix`. The plugin performs no migration.

| Capability | Status | CLI | Notes |
|------------|--------|-----|-------|
| store | ✅ 🧩 | `1.5+` · `built-in` | Listed with id/root and `store doctor` health (metadata present/valid, git repository, openspec-root healthy). Health is read solely from the CLI's `healthy` flag — on CLI 1.6+ a fresh store whose planning directories don't exist yet is healthy ("healthy-empty") and shows no error marker. CLI-sourced above the `1.5.0` floor; `built-in` on-disk fallback reads `stores/registry.yaml`. Full-tier actions: New (needs a path picker), Register (1.6 refusal codes for `store:`-pointer roots surface the CLI's fix verbatim), Unregister, and Remove (guarded — deletes local files). |
| workset | ✅ 🧩 | `1.5+` · `built-in` | Listed with members (`name` + `path`) as child rows. CLI-sourced above the `1.5.0` floor; `built-in` on-disk fallback reads `worksets/worksets.yaml`. Full-tier actions: New (member list), Open (opens members as attached folders), Remove (member folders untouched). |

## IDE value-add (plugin-original)

| Capability | Status | Notes |
|------------|--------|-------|
| Spec syntax highlighting | ✅ 🧩 | RFC-2119 + scenario keyword highlighting |
| Delta-spec inline inspection | ✅ 🧩 | Real-time structural checks + quick-fixes |
| Delta-spec diff viewer | ✅ 🧩 | Side-by-side delta vs main spec |
| Tool-window workflow panel | ✅ 🧩 | Change tree + workflow actions |
| Coordination tab (1.4) | ✅ 🧩 | Tiered Hidden/Awareness/Full surface for workspaces, context stores, initiatives |
| Store/workset surface (1.5) | ✅ 🧩 | Tab presentation of 1.5 stores (with `doctor` health) and worksets, gated at the `1.5.0` floor, with legacy pre-1.5 state demoted; at the Full tier, CLI-delegated write actions (store setup/register/unregister/remove, workset create/open/remove) plus a `store doctor` health strip |
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
2. **Workflow-surface fidelity.** Rebuild **Verify** as a faithful `verify-change` surface (semantic, language-agnostic, schema-aware); fill remaining workflow gaps (an `onboard`-aligned path; `feedback` was deliberately declined — see its row).
3. **Coordination layers (1.4).** ✅ Shipped — the Coordination tab surfaces `workspace` / `context-store` / `initiative` for cross-area / multi-repo coordination (read-only without a 1.4 CLI; actions and artifact navigation with one). Remaining polish: per-mode authoring UX and richer initiative editing.

> Each row's "delivered by" history lives in [`CHANGELOG.md`](../CHANGELOG.md) and the archived OpenSpec changes under `openspec/changes/archive/`.

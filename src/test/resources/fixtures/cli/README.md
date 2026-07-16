# CLI contract fixtures — provenance manifest

Every file here is **real output captured from the OpenSpec CLI** (never hand-authored), per
the project's contract-test discipline: captures run under an isolated environment
(`HOME`/`XDG_DATA_HOME`/`XDG_CONFIG_HOME`/`XDG_STATE_HOME` pointed at fresh temp dirs,
telemetry off) and machine-specific absolute paths are sanitized to the `/fixture/...`
convention before commit.

## Layout rules

- **Version-named directories** (`1.5.0/`, `1.6.0/`, …) hold captures whose CLI generation is
  known exactly. All **new** captures go into the directory matching the capturing CLI version.
- **The versionless root is frozen.** Its files are legacy-generation captures with mixed
  provenance (recorded below). They are retained verbatim as parse coverage for the CLI
  generations the plugin still supports, and are never moved, re-pointed, or re-captured in
  place. A root file may be deleted only when the plugin drops support for its generation
  (a spec-level change).
- **Pinned fixtures** are captures of commands that no longer exist in the current CLI. They
  can never be refreshed and remain the only coverage for the generation that provided the
  command. Their consuming tests carry the same note.

## Root (frozen legacy captures)

| File | Capturing CLI | Recapturable | Notes |
|---|---|---|---|
| `status.json` | ≈1.3 era (committed 2026-03-07) | yes — 1.6.0 twin exists | Verify completeness-gate contract |
| `status-with-context.json` | 1.5.0 (committed 2026-07-04) | yes — 1.6.0 twin exists | `actionContext` + `missingDeps` shape |
| `instructions-{proposal,specs,tasks}.json` | ≈1.3 era (committed 2026-03-07) | yes — 1.6.0 twins exist | staged artifact-DAG states |
| `validate.json` | ≈1.3 era (committed 2026-03-07) | yes — 1.6.0 twin exists | mixed valid/invalid items |
| `schema-validate-{clean,broken,missing-template}.json` | 1.4.1 | yes — 1.6.0 twins exist | per `SchemaToolingContractTest` javadoc |
| `schema-which-{builtin,project,shadowing}.json` | 1.4.1 | yes — 1.6.0 twins exist | |
| `templates-builtin.json` | 1.4.1 | yes — 1.6.0 twin exists | |
| `update-{clean,legacy-pending,legacy-pending-regenerated}.txt` | 1.4.1 (noted byte-identical on 1.5.0) | yes — 1.6.0 twins exist | legacy project initialized by CLI 1.3.1 |
| `coordination-{workspace-list,initiative-list,context-store-list,context-store-doctor}.json` | 1.4.x | **NO — PINNED** | `workspace`/`context-store`/`initiative` commands were removed upstream at 1.5.0; these are the only parse coverage for the still-supported 1.4.x line |

## `1.5.0/` — store/workset generation set

Captured from CLI 1.5.0 (store/workset surface work; `stores-registry.yaml`/`worksets.yaml`
are on-disk state files, the rest is command output). Retained as 1.5-generation coverage:
1.5 register refuses a fresh/config-only root (`store_register_root_unhealthy`), which 1.6
no longer does. See `StoreWorksetContractTest`/`StoreWorksetWriteContractTest` javadoc for
per-file recipes. `store-list-native-paths.json` is a real Windows capture
(cross-platform-verification); unrecapturable without a Windows host but the command still
exists — re-capture on Windows if refreshed.

## `1.6.0/` — current-generation set

Captured from CLI 1.6.0. Store family (5 files) captured by the store-health change; the
rest by the fixture-sweep change. Recipes live in the capturing change's `design.md`
(archived under `openspec/changes/archive/`) and the consuming tests' javadoc. Highlights
of what this generation changed:

- `validate --all --json`: new top-level `root`/`summary`/`version` keys; missing-SHALL issue
  path is `requirements[0]` with reworded message; new INFO-level issue (with a `line` field)
  for non-canonical level-3 headers inside change deltas, emitted on `valid: true` items.
- `status --json` / `instructions --json`: additive `planningHome`/`changeRoot`/
  `artifactPaths`/`nextSteps` keys; existing keys unchanged.
- `update`: legacy-migration block unchanged but gains a `Migrated: custom profile ...`
  preamble and profile-note trailers. At 1.6.0, `init --tools junie` still creates legacy
  `.junie/commands/` files that `update` immediately flags (captured as-is); `--force`
  regenerates them without adding `opsx-sync.md` (the migrated "custom profile" preserves
  the old workflow set). The `update-clean.txt` capture uses `--tools claude` (skills-only
  delivery, no migration block).
- `validate-parity-corpus.json` + `parity-corpus/` — verdict-parity pair: the corpus
  (committed markdown, exercising keyword/fence/scenario/skipped-header rule classes) is
  materialized into a test project and judged by the plugin's built-in validator, while the
  fixture is the real 1.6.0 CLI's `validate --all --json` over the same corpus (isolated
  env, minimal proposals present so both sides see identical content). Re-capture: re-run
  `validate --all --json` over `parity-corpus/` seeded into a fresh `openspec init` project.
  See `ValidatorVerdictParityTest`.
- Store family: fresh/config-only roots register successfully and doctor reports
  `healthy: true` with per-directory `present: false`; new refusal codes
  `invalid_store_pointer`, `store_root_pointer_declared`; new
  `store_register_identity_confirmation_required` envelope.

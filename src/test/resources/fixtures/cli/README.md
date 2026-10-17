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

## `1.3.0/` — floor-version parity guard

Captured from CLI **1.3.0** (the plugin's supported floor) by the `builtin-validator-cli-parity`
change. `validate.json` is a real `validate --all --json` over a two-spec corpus — one valid spec
whose only issue is a WARNING (`good`, `valid:true`) and one requirement-missing-`SHALL` spec
(`bad`, `valid:false`, one ERROR + one WARNING). It exists to lock in the cross-version stability of
the `validate --json` shape the CLI-authoritative merge depends on: the item's `valid` field,
`issues[].level/message`, `type`, and `id` are byte-identical across 1.3.0 → 1.6.0 (only a top-level
`root` key was added in 1.5, which the parser ignores). Consumed by
`CliContractTest.FloorVersionValidateContractV13`, which asserts the parser derives the per-item
verdict from `valid` on this older, `root`-less shape. Re-capture from a real 1.3.x CLI if the floor
shape ever changes.

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
- `validate-single-{spec,change,change-invalid}.json` — real single-item
  `openspec validate <id> --type spec|change --json` (the Project-View scoped Validate's CLI call).
  The single-item envelope carries the same `items[]` element shape as `--all` but a one-element
  array plus top-level `summary`/`version`/`root`; `parseJsonOutput` handles it unchanged.
  `-spec` is a valid spec whose only issue is a WARNING (skipped on a `valid:true` item);
  `-change` is a clean change (no issues); `-change-invalid` is a delta-less change (one ERROR).
  Consumed by `CliContractTest.SingleItemValidateContractV16`. `root.path` sanitized to `/fixture`.
- `1.7.0/validate-single-change-no-proposal.json` — a change with a valid ADDED delta but **no
  `proposal.md`** (and no `.openspec.yaml`) validates `valid:true` / `issues:[]`: the CLI resolves
  changes by directory existence, not by requiring a proposal (upstream #1182). Locks that fact behind
  the plugin's `change-proposal-required` ERROR→WARNING demotion — a future CLI that starts requiring
  `proposal.md` breaks `CliContractTest.SingleItemValidateContractV17.proposalLessChangeReadsAsValid`.
  Standalone single-item capture; deliberately NOT added to the parity corpus (its distinct filename
  is invisible to the version-stability guard's exact-filename glob, so the 1.6.0 anchor is untouched).
  Re-capture (per CLI generation): under an isolated `HOME`/`XDG_*` env, `openspec init`, author a
  spec under `openspec/specs/<cap>/spec.md`, a valid change and a delta-less change under
  `openspec/changes/`, then run `validate <id> --type spec|change --json` and `sed` the root path.
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
  See `ValidatorVerdictParityTest`. The `align-spec-parser-with-cli` change grew the corpus
  with five adversarial specs (`indented-code`, `setext-header`, `table-keyword`,
  `html-comment-req`, `nested-list-scenario`) — each a valid spec plus a structural distractor
  (an indented/fenced/tabulated/commented/setext marker the CLI does not treat as structure) —
  so the corpus is now 13 items (11 specs + 2 changes).
- `spec-structure/<id>.show.json` — structure-parity captures: the real 1.6.0 CLI's
  `openspec show <id> --json --type spec` for every spec in `parity-corpus/openspec/specs/`,
  one file per spec. `SpecParserCliStructureContractTest` asserts the plugin's
  `SpecParsingService` recovers the same `requirementCount` and per-requirement scenario count.
  `root.path` is the only sanitized field (→ `/fixture`); note the CLI's `title` field is the
  spec **id**, not the markdown H1. Re-capture (per CLI generation): seed `parity-corpus`'s
  specs into a fresh `openspec init --tools claude` project under an isolated
  `HOME`/`XDG_*` env, run `show` per spec id, and `sed` the project root path to `/fixture`.
- Store family: fresh/config-only roots register successfully and doctor reports
  `healthy: true` with per-directory `present: false`; new refusal codes
  `invalid_store_pointer`, `store_root_pointer_declared`; new
  `store_register_identity_confirmation_required` envelope.
- `change-deltas/{mixed,rename-only,empty}.show.json` — the real 1.6.0 CLI's
  `openspec show <change> --type change --json` (stdout only; the command also prints a
  spurious `Warning: Ignoring flags not applicable to change: scenarios` to stderr on
  success, which is discarded). Consumed by `ChangeDeltasContractTest`. `mixed` spans two
  capabilities (`auth`, `billing`) with all four operations — ADDED+MODIFIED under `auth`,
  REMOVED+RENAMED under `billing` (`deltaCount: 4`); `rename-only` isolates the
  requirement-less RENAMED branch; `empty` is `deltaCount: 0`. Verified shape: every
  non-RENAMED delta carries BOTH a singular `requirement{text,scenarios[].rawText}` and a
  one-element `requirements[]` mirror (so `requirement == requirements[0]`); REMOVED carries
  `requirement.text` with `scenarios: []`; only RENAMED is requirement-less (`rename{from,to}`).
  Re-capture (per CLI generation): under an isolated `HOME`/`XDG_*` env, `openspec init`, seed
  main specs for the MODIFY/REMOVE/RENAME targets under `openspec/specs/<cap>/spec.md`, author a
  change with the four delta operations across two capabilities, then run `show` per change id
  and `sed` the project root path to `/fixture` (`root.path` is the only sanitized field).

## `1.7.0/change-metadata/` — change `.openspec.yaml` shapes (for the tolerant-parse contract)

Real 1.7.0-CLI change-metadata files, consumed by `ChangeMetadataContractTest` /
`ChangeMetadataParserToleranceTest` / `ChangeServiceMetadataToleranceTest`. This dir was the first
(change-metadata-scoped) slice of the 1.7 corpus; the broader 1.7 status/validate/instructions/
schema/store/change-deltas/spec-structure/update capture and the `…V17` contract nests now exist
(see the `1.7.0/` current-generation section below). `.openspec.yaml` files carry
no absolute paths or hostnames, so nothing is sanitized — only the capture-day `created:` value
is fixed (`2026-08-05`) and asserted verbatim.

- `baseline-schema-created.openspec.yaml` / `new-change-goal.openspec.yaml` — **verbatim** output of
  `openspec new change <n>` and `openspec new change <n> --goal "…"` under an isolated
  `HOME`/`XDG_*` env after `openspec init --tools none`. `--goal` is the only `new change` flag that
  writes a metadata field. Note `created:` is **unquoted** — the untyped SnakeYAML resolver turns a
  bare `2026-08-05` scalar into a `java.util.Date`, so the reader must accept String OR Date.
- `rich-all-fields.openspec.yaml` / `skip-specs-only.openspec.yaml` — **derived** (the CLI has no
  writer flag for `affected_areas`/`initiative`/`skip_specs`), shaped per the CLI's Zod schema
  (`core/change-metadata/schema.js`: `affected_areas` array, `initiative` `{store,id}` object,
  `skip_specs` boolean) and **proven reader-accepted**: `openspec validate <n> --strict` → *valid*
  (with `skip_specs: true` making the delta-less change valid).
- `initiative-as-string.openspec.yaml` — **negative control**: same session, `initiative` as a
  string. `openspec validate <n> --strict` → *invalid* with `initiative: Invalid input: expected
  object, received string`. Proves the acceptance oracle actually discriminates field shape (and
  confirms unknown *keys* — vs wrong-shaped known keys — validate clean, i.e. the strip contract).
- `legacy-status-proposed.openspec.yaml` — a **copy** of the archived on-disk
  `openspec/changes/archive/2026-03-18-config-yaml-viewer/.openspec.yaml` (authentic legacy
  `schema: openspec-change` / `status: proposed` / quoted `created`). The backward-compat anchor.
- `malformed.openspec.yaml` — a **deliberately corrupt** file (a user typo, not a CLI shape),
  labeled as such; exercises only the warn branch (a `MarkedYAMLException` at load).

## `1.7.0/` — current-generation set (status/instructions/validate/schema/store/deltas/spec/update)

Real CLI **1.7.0** captures, the twin of the `1.6.0/` set, asserted by the `…V17` contract nests
alongside their `…V16` twins (`CliContractTest`, `SchemaToolingContractTest`,
`StoreWorksetContractTest`, `StoreWorksetWriteContractTest`, `ChangeDeltasContractTest`,
`SpecParserCliStructureContractTest`, `UpdateOutputParserContractTest`) plus the platform-free
`ValidatorVerdictVersionStabilityTest`. Every file was captured under a fresh isolated
`HOME`/`XDG_*` env with `OPENSPEC_TELEMETRY=0`; the only edits are path sanitizations matched to the
`1.6.0/` twins' tokens (`/fixture`, `/fixture/demo-project`, `/fixture/parity-corpus`,
`/fixture/node_modules`, `/fixture/<store-leaf>`). No `archive` was run. **1.6 → 1.7 is a strictly
additive superset** — verdict-parity was explicitly confirmed (the 13-item `validate-parity-corpus`
id→valid map + summary are byte-identical to 1.6.0). The observable differences, all captured
faithfully:

- **status** (`status{,-with-context,-complete}.json`) — 1.7 adds a per-artifact `requires: string[]`
  and reorders `artifacts[]`/`missingDeps[]` to schema order `proposal, specs, design, tasks`. The
  `…V17` status nest keys assertions by artifact **id** (never index) so the reorder is inert, and
  adds one JSON-level check that the `requires` edges are present (`ArtifactInfo` has no `requires`
  field, so Gson drops them — the parser is unaffected). Statuses/`isComplete` are unchanged.
- `status-skipped.json` — a 1.7-only capture of a `skip_specs: true` change's status: the specs
  artifact reports `skipped` and the change is `isComplete: true`. The plugin's `ArtifactStatus`
  enum has no `SKIPPED`, so it degrades to `UNKNOWN` (graceful — completeness is read from the CLI's
  `isComplete`, never re-derived); the nest locks that contract. Recipe: isolated env, `init`, a
  `new change` whose `.openspec.yaml` sets `skip_specs: true` with proposal/design/tasks present and
  no `specs/` delta, then `status --json`, root → `/fixture/demo-project`.
- **instructions** (`instructions-{proposal,specs,tasks}.json`) — 1.7 reorders `unlocks[]` to schema
  order (proposal now unlocks `["specs","design"]`, was `["design","specs"]`) and rewrites the
  advisory `instruction`/`template` prose (adds `skip_specs` guidance); all structural fields
  (`dependencies`, dep paths/done flags) are unchanged. The `…V17` nest asserts the reordered
  `unlocks` and the unchanged structure.
- **update** (`update-clean.txt`, `update-legacy-pending{,-regenerated}.txt`) — 1.7 dropped the
  `Migrated: custom profile` preamble from a clean `--tools claude` update, and `init --tools junie`
  now scaffolds **six** legacy `opsx-*` command files (adds `opsx-sync.md` + `opsx-update.md`), so
  both the pending and the post-force *regenerated* "Files to remove" lists carry six entries (1.6's
  regenerated list stayed at four). ANSI spinner escapes were stripped post-capture to match the
  ESC-free 1.6 presentation; the version-pin for the legacy-pending capture was reproduced by editing
  the scaffolded skills' `generatedBy` to an older value (no real 1.3.1 CLI on hand).
- **validate** (`validate.json`, `validate-single-{spec,change,change-invalid}.json`,
  `validate-strict-warning-only.json`) — verdicts/counts/paths identical to 1.6.0; the delta-less
  change ERROR gained a trailing `skip_specs` sentence (the asserted `Change must have at least one
  delta` prefix is intact), and `durationMs` is per-run timing noise (asserted by nothing).
- **byte-identical families** — `schema-validate-*`, `schema-which-*`, `templates-builtin.json`, the
  `store-{doctor-healthy-empty,register-*}` set, `change-deltas/{mixed,rename-only,empty}.show.json`,
  and `spec-structure/*.show.json` (11) are unchanged from 1.6.0; their `…V17` nests are verbatim
  twins (forward tripwires). `spec-structure` + `validate-parity-corpus.json` were re-captured by
  **reusing the `1.6.0/parity-corpus` markdown as capture input** (the corpus is authored once);
  `SpecParserCliStructureContractTest` runs the same structure-parity check against both generations'
  `spec-structure/` captures, and `ValidatorVerdictVersionStabilityTest` now **auto-discovers every
  committed `fixtures/cli/*/validate-parity-corpus.json`** (and its `-strict` twin) and asserts each
  carries the anchor (`1.6.0`) `id→valid` map — so a future generation is covered with no test edit.
  Pre-1.6 generations are deliberately NOT in this set: their CLI rules predate the corpus dialect and
  legitimately verdict it differently (fence-aware scenario counting @1.4, multi-line requirement-body
  keyword reading @1.6), so `1.3.0` shape-stability is locked by its own `validate.json` instead.

## `1.7.0/` — CLI-parser fixture-gap closures (schemas / config list / --version / config profile)

Real 1.7.0 output for four parsers that previously shipped with **no captured fixture** — tested only
against inline hand-authored JSON, which encodes the author's assumed shape so the test passes while
the parser can be wrong. Captured under an isolated `HOME`/`XDG_*` env; no machine paths to sanitize.

- `config-list.json` — real `openspec config list --json` after `config profile core`
  (`{featureFlags, profile, delivery, workflows[]}`). Backs `WorkflowProfileServiceTest.ConfigListContract`
  for `WorkflowProfileService.parseSnapshot`. The `workflows` array is present only once a profile has
  been set (a fresh `init` omits it, exercising the parser's `CORE_DEFAULTS` fallback); this capture
  has it, and the test asserts `update` is present — `update` is in the real workflows but NOT in
  `CORE_DEFAULTS`, proving the real array parsed rather than the fallback.
- `version.txt` — real `openspec --version` (bare `1.7.0`). Backs `CliDetectionServiceTest`'s
  version-strip contract (feeds every numeric floor gate) and the floor-no-cap assertion.
- `schemas --json` is backed by the existing `config-validation/schemas-list-with-fork.json` (a project
  fork + the built-in package schema), now also consumed by `SchemaServiceTest` for the full
  `parseSchemaList` contract. Note: real `schemas --json` reports `source` ("package" = built-in,
  "project" = fork) and `artifacts` — the parser previously read the never-emitted `isBuiltIn`/
  `artifactIds` keys (fixed in this change).
- `config-profile-json-rejected.txt` — **pinned, not a JSON fixture.** `openspec config profile --json`
  is rejected on 1.7 (`error: unknown option '--json'`) — the option never existed. Kept as the
  **tombstone** documenting why the Settings Config Profile section does NOT use `config profile --json`:
  it re-sources the active profile name + workflows from `config list --json` (via `WorkflowProfileService`).
  Do not reintroduce a `config profile --json` parser — there is nothing to parse.
- `validate-parity-corpus-strict.json` — real `openspec validate --all --strict --json` over the shared
  `1.6.0/parity-corpus` markdown (13 items, 9 valid). On this corpus no item is valid-with-only-a-CLI
  warning, so the strict `id→valid` map equals the default map. Backs the **strict dimension** of
  `ValidatorVerdictParityTest` (asserts the plugin's built-in strict *fallback* verdict never exceeds
  the CLI's strict verdict, driving the real `applyStrictFallbackVerdict`) and the strict arm of
  `ValidatorVerdictVersionStabilityTest`. `root.path` sanitized to `/fixture/parity-corpus`.

### Durable next-generation capture (both parity twins)

When a new CLI generation ships, capture its parity corpus so the version-agnostic guards pick it up
with **zero test edits** (the discovery globs `validate-parity-corpus.json` and its `-strict` twin per
generation). Install that CLI, then under an isolated env — `HOME`, `XDG_DATA_HOME`, `XDG_CONFIG_HOME`,
`XDG_STATE_HOME` all in a fresh `mktemp -d`, `OPENSPEC_TELEMETRY=0`:

```
cd "$(mktemp -d)" && openspec init --tools none
cp -R <repo>/src/test/resources/fixtures/cli/1.6.0/parity-corpus/openspec/specs/.   openspec/specs/
cp -R <repo>/src/test/resources/fixtures/cli/1.6.0/parity-corpus/openspec/changes/. openspec/changes/
P=$PWD
openspec validate --all --json          | sed "s#$P#/fixture/parity-corpus#g" > validate-parity-corpus.json
openspec validate --all --strict --json | sed "s#$P#/fixture/parity-corpus#g" > validate-parity-corpus-strict.json
```

Reuse the `1.6.0/parity-corpus` markdown as the capture INPUT — the corpus is authored **once**; never
re-author it per generation (that is what reintroduces era mismatch). Commit both under
`fixtures/cli/<gen>/`. On macOS the project path resolves under `/private`, so confirm `root.path` is
exactly `/fixture/parity-corpus` (strip any leftover `/private` prefix). A differing `id→valid` map is
a **real tightened-verdict signal** — investigate the CLI change; never edit a committed fixture to
force a guard green.

## `1.7.0/config-validation/` — 1.7 config-field recognition (operations / rules / defaultStore)

Real 1.7 config shapes the plugin's tolerant reader must ignore without false-flagging (it is never
stricter than the CLI). No code change was needed — captured to *lock* the no-false-flag behavior.

- `operations-and-rules.config.yaml` — a project `openspec/config.yaml` carrying 1.7's optional
  `operations: {apply?/archive?: {guidance: string[]}}` and `rules: {<artifactId>: string[]}` (the
  real record-of-arrays shape). **Proven CLI-accepted**: `openspec validate --all --json` on a clean
  project with this config exits 0 (the CLI parses the config on load, so acceptance = a valid shape).
  `ConfigServiceTest` asserts `fromMap` reads `schema` and skips the list-valued `rules` without
  crashing (it models only String-valued rules); `BuiltInValidatorTest` + `ConfigValidationInspection
  Test` assert neither raises an ERROR or a plugin-invented WARNING on it.
- `global-config-defaultstore.json` — the machine-level global config after `openspec config set
  defaultStore <id>` (`{featureFlags, profile, delivery, defaultStore}`), the same shape
  `config list --json` returns once a default store is set. `defaultStore` is out-of-model
  (machine-level store routing); `WorkflowProfileServiceTest` asserts `parseSnapshot` ignores it
  (reads only `profile`/`workflows`). Paths sanitized to `/fixture`.

## `1.8.0/` — missing-SHALL demotion + duplicate-requirement parity

Real CLI **1.8.0** captures. 1.8 demoted the missing-`SHALL`/`MUST` rule from ERROR to a default
WARNING for a body-carrying requirement (strict re-promotes) and added a main-spec
duplicate-requirement ERROR. These fixtures lock both against the real CLI. Recipes: isolated
`HOME`/`XDG_*`, telemetry off, `root.path` sanitized to `/fixture`.

- `validate-parity-corpus.json` / `validate-parity-corpus-strict.json` — real `validate --all --json`
  (default and `--strict`) over the **existing `1.6.0/parity-corpus` markdown**, re-run through 1.8.0.
  Default = 12 valid / 13, strict = 9 valid / 13 (= the 1.6.0 default anchor). The three items that
  flip valid under 1.8's default (fenced-keyword, header-only-keyword, should-only) are the demotion
  in action. Consumed by `ValidatorVerdictParityTest` (default oracle re-anchored 1.6→1.8) and
  `ValidatorVerdictVersionStabilityTest` (subset-of-laxest default arm, anchor 1.8.0).
- `validate-single-spec-no-body.json` — a requirement with a scenario but **no body prose** stays an
  `ERROR` (`path: requirements[0]`, message "must contain SHALL or MUST"): 1.8's demotion is
  body-conditional. Consumed by `CliContractTest.ValidateContractV18`.
- `validate-single-spec-duplicate-requirement.json` — a main spec that declares `### Requirement:
  Works` **twice**. 1.8's new main-spec duplicate rule (upstream #1484) → one `ERROR`, `path:"file"`
  (a literal string, not the spec path), `line` = the **second** occurrence (15), message naming the
  **first** occurrence's line (8): *Requirement header "### Requirement: Works" duplicates the
  requirement declared on line 8…*. The duplicate emission carries **no machine rule-id** (freeform
  message only), so the parser keys off `level`+`path`+message content. Consumed by
  `CliContractTest.ValidateContractV18`; the CLI-absent fallback mirrors it as rule
  `spec-duplicate-requirement`. Match is case-sensitive and scoped to `## Requirements`; N occurrences
  yield N−1 errors (verified against the real CLI). Delta specs use a **different** CLI rule
  (`Duplicate requirement in ADDED: "…"`, `path: <cap>/spec.md`, no line) that the fallback does not
  reimplement — so the fallback never over-reports on deltas.
- `validate-single-spec-requirement-outside-section.json` — the section-scoping negative, captured to
  lock (against real output, not inference) that a name appearing **once inside `## Requirements` and
  once outside it is NOT a duplicate**: the CLI routes the out-of-section occurrence to a separate
  `requirement-outside-requirements` ERROR (*"…appears outside the main ## Requirements section…"*) and
  never dedups it. Guards the one direction the fallback could over-report; consumed by
  `CliContractTest.ValidateContractV18`.
- `version.txt` — real `openspec --version` (bare `1.8.0`).
- `config-validation/github-copilot.{config.yaml,validate.json}` — 1.8's optional `githubCopilot`
  config block the tolerant reader ignores; `validate` exits 0 on it.
- `validate-single-change-{misplaced-delta,no-deltas,skip-specs,nested-delta}.json` — the
  change-delta **discovery** shapes (the misplaced-delta rule shipped in 1.7.0 #1392/#1385; 1.8 only
  reworded its message). Captured to lock the CLI-absent fallback's discovery parity:
  `misplaced-delta` = a regular file at the change's `specs/` root → ERROR `path:"spec.md"` (*"Delta
  spec found at specs/spec.md…must live under a capability path…"*); `no-deltas` = a change with no
  delta `spec.md` → ERROR `path:"file"` (*"Change must have at least one delta…"*); `skip-specs` = a
  `skip_specs: true` change with no deltas is **valid** with an INFO (not a no-deltas ERROR) — the
  gate the fallback must honor; `nested-delta` = `specs/<area>/<capability>/spec.md` is **valid**
  (a `spec.md` at any depth ≥ 1 is a delta), so the fallback must recurse and must not flag it
  misplaced. Consumed by `CliContractTest`; no machine rule-id on any (freeform, keyed by
  level+path+message). Two boundary locks accompany them:
  `validate-single-change-misplaced-plus-valid.json` (a misplaced root `spec.md` coexisting with a
  valid capability delta → the **sole** misplaced ERROR, never a no-deltas co-fire — "misplaced counts
  as found") and `validate-single-change-misplaced-valid-content.json` (a root `spec.md` whose content
  is a valid delta → still misplaced, proving detection is path-based, not content-based). The
  no-deltas shape is a 1.8 parity twin of the older `1.7.0/validate-single-change-invalid.json`, not a
  novel shape.

## `1.9.0/` — parity corpus (strict additive superset of 1.8)

Real CLI **1.9.0** captures. Verified empirically (run the corpus, don't diff the dist): 1.9 is a
**strict additive superset of 1.8** — `validate --all --json` default (12/13 valid) and `--strict`
(9/13 valid) over the shared `1.6.0/parity-corpus` markdown are **byte-identical** (modulo per-run
`durationMs`) to the `1.8.0` twins. Committed as a forward tripwire the version-stability guard
auto-discovers; `1.9.0` is in `ValidatorVerdictVersionStabilityTest.FLOOR`, so a dropped future
capture fails the vacuity guard loudly. The default verdict-parity anchor stays `1.8.0` (1.9 is not
laxer).

- `validate-parity-corpus.json` / `validate-parity-corpus-strict.json` — real `validate --all --json`
  (default and `--strict`) over the existing `1.6.0/parity-corpus` markdown, re-run through 1.9.0.
  Consumed by `ValidatorVerdictVersionStabilityTest` (both arms).
- `version.txt` — real `openspec --version` (bare `1.9.0`).

**Deliberately not captured** (1.9's real changes are either CLI-stricter-only or not reachable
through the plugin, and are not adopted this cycle): the new `validate --archived` flag and the
task-numbering WARNING (both make the CLI stricter → the lenient fallback stays safe by being laxer),
and the `no_openspec_root` error envelope returned when `validate`/`list --json` run outside a root
(the plugin gates CLI invocation on the on-disk `openspec/` root). Optional fixture locks for these are
a separate follow-up. Re-capture recipe: the generic "Durable next-generation capture" block above,
with `<gen>` = `1.9.0`.

## `1.10.0/` — parity corpus (strict additive superset of 1.9)

Real CLI **1.10.0** captures. Verified empirically (run the corpus, don't diff the release notes) and
corroborated by a source diff of the validation engine (byte-identical 1.9→1.10): 1.10 is a **strict
additive superset of 1.9** — `validate --all --json` default (12/13 valid) and `--strict` (9/13 valid)
over the shared `1.6.0/parity-corpus` markdown are **content-identical** (only per-item `durationMs`
timing differs) to the `1.9.0` (and `1.8.0`) twins. Committed as a forward tripwire the version-stability
guard auto-discovers; `1.10.0` is in `ValidatorVerdictVersionStabilityTest.FLOOR`, so a dropped future
capture fails the vacuity guard loudly. The default verdict-parity anchor stays `1.8.0` and the strict
anchor stays `1.6.0` (1.10 is not laxer).

- `validate-parity-corpus.json` / `validate-parity-corpus-strict.json` — real `validate --all --json`
  (default and `--strict`) over the existing `1.6.0/parity-corpus` markdown, re-run through 1.10.0.
  Consumed by `ValidatorVerdictVersionStabilityTest` (both arms). Capture-time discipline: after
  sanitizing, diff each twin against its `1.9.0` counterpart with
  `del(.durationMs, .items[].durationMs)` and confirm the only differences are timing — anything else is
  a real 1.10 behavior change (stop; do not edit the fixture to force a guard green).
- `version.txt` — real `openspec --version` (bare `1.10.0`). Also asserted by `TargetVersionSingleSource
  Test` (must equal `openspecTargetVersion` in `gradle.properties`).

**1.10 is also the first two-digit minor version.** A lexical version compare would misorder `1.10.0`
below `1.9.0`; the plugin's `CliVersion.compare` is numeric (segment-wise), and
`CliVersionTest.twoDigitMinor_ordersNumericallyNotLexically` pins the `1.9`/`1.10` boundary so a
regression to lexical comparison fails loudly.

### `1.10.0/` — store register confirmation-gate captures

Two real `store register` envelopes that pin the confirm-then-`--yes` register flow (the identity
confirmation gate the CLI raises when turning a healthy OpenSpec root into a store). Consumed by
`StoreWorksetWriteContractTest` (parser twins + `identityConfirmationRequired()` coverage) and
`CoordinationPanelRegisterFlowTest` (the probe→confirm→retry orchestration). The prior register
fixtures (`1.6.0`/`1.7.0`) exercised the *parser* but no test asserted the register *argv*, so a
missing `--yes` on the retry — the dead-end this change fixes — slipped past CI; these two lock the
outcome shapes for both phases against the real 1.10.0 CLI.

- `store-register-confirmation-required.json` — the **probe** refusal: `store register <root> --json`
  (no `--yes`) on a healthy OpenSpec root that does not yet carry `.openspec-store/store.yaml`.
  `status[0].code == "store_register_identity_confirmation_required"`, `store`/`registry`/`git` all
  null, `created_files: []`, and a `fix` mentioning `--yes`. The probe leaves the root store-less.
- `store-register-yes-success.json` — the **retry** success: `store register <root> --yes --json` on
  the same class of root. `status: []`, `store.root` set, `registry.already_registered: false`, and
  `created_files` containing `.openspec-store/store.yaml` (the identity metadata the `--yes` retry
  creates).

Capture recipe (isolated env, per the discipline above): under a fresh `HOME`/`XDG_DATA_HOME` with
`OPENSPEC_TELEMETRY=0`, `git init` + `openspec init --tools none` a throwaway root, run
`store register <root> --json` (no `--yes`) FIRST — it reports the confirmation gate and does **not**
write store identity — then `store register <root> --yes --json` on an equivalently fresh root for the
success twin. Sanitize every machine-absolute path: the store root → `/fixture/healthy-root`, the XDG
registry path → `/fixture/registry/openspec/stores/registry.yaml`, and the store id (derived from the
temp folder name) → `healthy-root`. Grep the results for `/Users`, `/var/folders`, `/home/`, and the
temp base before committing — zero machine paths may remain.

**Deliberately not captured / not adopted** (1.10's client-side additions are off-model or inert to the
plugin): the new `init --language` flag and Zed adapter target (`--tools zed`) — off-model AI-tool /
scaffold surfaces; the first-run completion tip relocated to stderr (deferred on `--json` and non-TTY
runs, so it never enters parsed stdout); and the runtime-only `completionTipSeen` global-config field
(the tolerant reader ignores it). Re-capture recipe: the generic "Durable next-generation capture" block
above, with `<gen>` = `1.10.0`.

## `1.11.0/` — parity corpus (safe-direction superset of 1.10) + placeholder-rule positive control

Real CLI **1.11.0** captures. Verified empirically (run the corpus, don't diff the release notes) and
corroborated by a source diff of the validation engine: 1.11 is an **additive, safe-direction superset of
1.10** — `validate --all --json` default (12/13 valid) and `--strict` (9/13 valid) over the shared
`1.6.0/parity-corpus` markdown are **content-identical** (only per-item `durationMs` timing differs) to the
`1.10.0` (and `1.8.0`) twins. Unlike 1.9/1.10 the validation-engine source is **not** byte-identical (1.11
added a `PURPOSE_IS_PLACEHOLDER` rule via a new `purpose-placeholder.js`), but the one functional change
makes the CLI *stricter*, so the default verdict-parity anchor stays `1.8.0` and the strict anchor stays
`1.6.0` (1.11 is not laxer). `1.11.0` is in `ValidatorVerdictVersionStabilityTest.FLOOR`, so a dropped
future capture fails the vacuity guard loudly.

- `validate-parity-corpus.json` / `validate-parity-corpus-strict.json` — real `validate --all --json`
  (default and `--strict`) over the existing `1.6.0/parity-corpus` markdown, re-run through 1.11.0.
  Consumed by `ValidatorVerdictVersionStabilityTest` (both arms). Capture-time discipline: after
  sanitizing, diff each twin against its `1.10.0` counterpart with `del(.durationMs, .items[].durationMs)`
  and confirm the only differences are timing — anything else is a real 1.11 behavior change (stop; do not
  edit the fixture to force a guard green).
- `version.txt` — real `openspec --version` (bare `1.11.0`). Also asserted by
  `TargetVersionSingleSourceTest` (must equal `openspecTargetVersion` in `gradle.properties`).
- `validate-purpose-placeholder.json` / `validate-purpose-placeholder-strict.json` — **positive control
  for the new `PURPOSE_IS_PLACEHOLDER` rule** (the only new 1.11 validation behavior). Real
  `openspec validate <spec> --type spec --json` (default and `--strict`) over a one-spec input whose
  `## Purpose` is the sentence `openspec archive` writes for a new capability (`TBD - created by archiving
  change …`). Default → `valid:true` with **one** `WARNING` on `overview` (the archive sentence is >50
  chars, so the pre-existing "Purpose too brief" WARNING does not co-fire); strict → `valid:false`, level
  stays `WARNING`. Consumed by `CliContractTest.ValidatePurposePlaceholderContractV1_11`: a raw-JSON arm
  pins the captured level/path/valid (the parser drops warnings on valid items and overwrites the issue
  path, so level/path are read from the raw JSON, as in `ValidateContractV18`), and a parser arm asserts
  `parseJsonOutput` reports the default item clean and the strict item failing from the CLI-authoritative
  `valid` field. Capture recipe: isolated `HOME`/`XDG_*`, `OPENSPEC_TELEMETRY=0`, `openspec init --tools
  none`, write the spec under `openspec/specs/placeholder-purpose/spec.md`, then `validate
  placeholder-purpose --type spec --json` (+ `--strict`); sanitize the project root to `/fixture`.
- `instructions-tasks-specs-done.json` — **regression anchor for GitHub #20** (Windows-only
  `buildPrompt` crash). Real `openspec instructions tasks --change … --json` captured with the
  `specs` dependency **completed** (`done:true`, path `specs/**/*.md`) — the crash trigger. The
  shipped `instructions-tasks.json` only carries `done:false`, so it does not exercise the glob-read
  path; this captures the real `done:true` shape rather than hand-flipping that field. Consumed by
  `CliContractTest.InstructionContractV1_11` (anchors the glob `path`/`done` and a no-throw
  `buildPrompt` regression that bites on the Windows CI leg); it also pins the literal
  `specs/**/*.md` that `ModelTest.buildPrompt_globDependencyPathIsSkippedNotReadAsLiteralFile` plants.
  Capture recipe: isolated `HOME`/`XDG_*`, `OPENSPEC_TELEMETRY=0`, `openspec init --tools none`,
  `new change`, materialize `proposal.md`/`design.md` and a `specs/<cap>/spec.md`, then `instructions
  tasks --change … --json`; sanitize the project root (incl. macOS `/private` prefix) to `/fixture`.

**Deliberately not captured / not adopted** (1.11's other client-side additions are off-model or inert to
the plugin): batch `status --all` (a batch of the existing per-change status shape) and `show <change>
--diff` (a rendering of delta-vs-main data the model already holds) — on-model but inert, the plugin's
single-change parse is untouched; the Antigravity adapter directory rename and shell-completion
generation — AI-tool / CLI-UX surfaces; and the transactional `schema init` that now writes `schema:`
(removing legacy `defaultSchema:`) — a config write path the plugin does not drive. Re-capture recipe for
the parity twins: the generic "Durable next-generation capture" block above, with `<gen>` = `1.11.0`.

## `1.12.0/` — parity corpus (safe-direction superset of 1.11) + archive-blocker positive control

Real CLI **1.12.0** captures. 1.12 is an **additive, safe-direction superset of 1.11**: the validation
verdict logic is byte-identical to 1.11, so `validate --all --json` default (12/13 valid) and `--strict`
(9/13 valid) over the shared `1.6.0/parity-corpus` markdown are **content-identical** to the `1.11.0` twins
**except for exactly one added `INFO`** — the new archive-blocker on `nameless-change` — present in **both**
the default and strict twin (`info-change` gains nothing; its delta is ADDED-only, which trips no blocker).
Per-item INFO totals: `info-change` 1, `nameless-change` 2. `nameless-change` stays `valid:true` in both
(INFO never feeds the verdict). `1.12.0` is in `ValidatorVerdictVersionStabilityTest.FLOOR`.

- `validate-parity-corpus.json` / `validate-parity-corpus-strict.json` — real `validate --all --json`
  (default and `--strict`) over `1.6.0/parity-corpus`, re-run through 1.12.0. Consumed by
  `ValidatorVerdictVersionStabilityTest` (both arms). **Capture-time discipline:** after sanitizing
  (`root.path` → `/fixture/parity-corpus`), diff each twin against its `1.11.0` counterpart with
  `del(.durationMs, .items[].durationMs, .root.path)` and confirm the **only** difference is the single
  added `nameless-change` archive-blocker INFO — anything else is a real 1.12 behavior change (stop; do not
  edit the fixture). This is **not** a durationMs-only copy.
- `version.txt` — real `openspec --version` (bare `1.12.0`). Also asserted by `TargetVersionSingleSourceTest`
  (must equal `openspecTargetVersion` in `gradle.properties`).
- `validate-archive-blocker.json` — **positive control for the new verdict-neutral archive-blocker `INFO`**
  (the only new 1.12 validation behavior). The parity-corpus INFO rides `valid:true` items and
  `CliOutputParser.parseJsonOutput` extracts issues only from `valid:false` items, so the corpus alone can't
  exercise the INFO through the parser. This capture is a **constructed** `valid:false` change carrying BOTH
  a structural `ERROR` (a MODIFIED requirement with no scenario, on `beta/spec.md`) AND the archive-blocker
  `INFO` (a well-formed MODIFIED requirement targeting a spec that does not exist, on `alpha/spec.md`) — on
  **different** delta paths (upstream `findArchiveBlockers` dedupes a blocker against any path that already
  carries an ERROR, so they must be separate specIds). Real `openspec validate archive-blocker --type change
  --json`, project root sanitized → `/fixture`. Consumed by `CliContractTest.ArchiveBlockerInfoContractV1_12`:
  a raw-JSON arm pins the captured level/path/message and the different-paths invariant; a parser arm asserts
  the INFO surfaces at `Severity.INFO` on a `valid:false` item while the failing verdict is preserved (the
  parser overwrites the issue path with `type/id`, so delta paths are asserted off the raw JSON only). This is
  the first fixture to positively exercise the `INFO` → `Severity.INFO` JSON mapping.
- `scaffold-delta/` — **contract for the plugin's delta-spec scaffold** (`TemplateProvider.deltaSpecTemplate`,
  written by the Create Delta Spec action). Two minimal projects, each with main spec `auth`
  (`### Requirement: Login`) and change `c`, whose `specs/auth/spec.md` is a scaffold filled in with the
  requirement in `added-requirement.md`, inserted immediately before the `## MODIFIED Requirements` heading:
  - `new-template/` — the **current** template, generated from the real `deltaSpecTemplate("auth")` output (not
    retyped). `validate-new.json` / `validate-new-strict.json` are real `openspec validate c --type change
    --json` (default and `--strict`), both `valid:true`, with the comment-only MODIFIED/REMOVED/RENAMED
    sections left as scaffolded. `ScaffoldingContractTest.filledTemplate_isExactlyTheInputTheCliValidated`
    asserts the template plus the inserted requirement **equals** this input byte for byte, so the capture is
    provably of the shipped template. If the template changes, regenerate the input from the new template and
    re-capture; don't hand-edit the file. `BuiltInValidatorRulesTest.capturedFilledScaffold_hasNoBuiltInError_matchingTheCli`
    and `DeltaSpecQuickFixTest` hold the built-in fallback and the IDE inspection to the same verdict (the
    empty RENAMED section must not be an ERROR).
  - `old-template/` — a **frozen negative control**: the pre-fix template (bare `## ADDED` / `## MODIFIED` /
    `## REMOVED`, bare-name RENAMED guidance) filled the same way. `validate-old.json` /
    `validate-old-strict.json` are `valid:false` ("No deltas found"). It documents why the template changed.
    **Never re-capture it from current code**: the old template no longer exists there.

  Capture recipe: in each project root, `HOME=$T XDG_CONFIG_HOME=$T/c XDG_DATA_HOME=$T/d XDG_STATE_HOME=$T/s
  OPENSPEC_TELEMETRY=0 openspec validate c --type change --json [--strict]` with a throwaway `$T`. Then
  sanitize `root.path` to `/fixture`. Not named `validate-parity-corpus.json`, so the version-stability guard
  ignores this directory.

## `1.13.2/` — parity corpus (identical to 1.12) + positive controls for the 1.13 behaviors

Real CLI **1.13.2** captures, each run in an isolated environment (`HOME`/`XDG_CONFIG_HOME`/`XDG_DATA_HOME`/`XDG_STATE_HOME` set to a throwaway directory, `OPENSPEC_TELEMETRY=0`) with project roots sanitized to `/fixture`. `1.13.2` is in `ValidatorVerdictVersionStabilityTest.FLOOR`.

- `validate-parity-corpus.json` / `validate-parity-corpus-strict.json`: real `validate --all --json`, default and `--strict`, over `1.6.0/parity-corpus` (`root.path` sanitized to `/fixture/parity-corpus`). Default is 12/13 valid and strict is 9/13. **Capture-time check:** with `del(.durationMs, .items[].durationMs, .root.path)`, both twins are **identical to the `1.12.0` twins**. 1.13's new rules fire on nothing in the corpus.
- `version.txt`: bare `1.13.2`.
- `renamed-star/`, `renamed-plus/`, `renamed-star-unpaired/`: RENAMED written with `*` and `+` bullets, which the client parses since 1.13.0. Each holds a project tree plus `validate-change.json`, from `validate c --type change --json`.
  - The star and plus pairs are `valid:true`. The unpaired lone `* FROM:` is `valid:false`; it is the **liveness anchor** that proves the built-in RENAMED rule actually fires on `*` input.
  - `renamed-star/show-change.json` gives the client's rename names.
  - `renamed-star/archived-spec.md` is the main spec after a real `openspec archive c --yes` on a scratch copy.
  - Consumed by `BuiltInValidatorRulesTest`, `DeltaSpecQuickFixTest` and `SpecSyncServiceTest`.
- `removed-star/`: a bullet-form REMOVED (``* `### Requirement: Works` ``) against a main spec with a second, surviving requirement. The archive would otherwise refuse to empty the spec without `retire_capabilities`.
  - `validate-change.json` is `valid:true`.
  - `show-change.json` is the client's own reading of the delta. Upstream quirk: its REMOVED `text` keeps the `Requirement: ` prefix.
  - `archived-spec.md` is the main spec after a real archive.
  - Consumed by `SpecSyncServiceTest`.
- `task-markers/` and `task-grammar/`: task-progress contract. Each change's `tasks.md` plus one `list --json` per project (`completedTasks`/`totalTasks`).
  - `task-markers` (1/6) covers `-`, `1.`, `+`, `[~]`, `[]` and `*`.
  - `task-grammar` covers ordered markers (`t-ordered`), box variants (`t-boxes`), link look-alikes (`t-links`), fenced, commented and indented lines (`t-fenced`), CRLF (`t-crlf`), a UTF-8 BOM (`t-bom`) and lone-CR endings (`t-cr-only`, one line to the client).
  - **`task-grammar/**` is `-text` in `.gitattributes`.** Its line endings and BOM are the test input and must survive Windows checkouts.
  - Consumed by `TaskLinesContractTest` (all controls, plus a CRLF arm) and `VerificationServiceTest`.
  - Expected counts are read from `list.json`, never typed into tests.
- `validate-invalid-item.json`: the item-less error response from `validate a/b --type change --json` (exit 1, `{"status":[{"severity":"error","code":"invalid_item",…}]}`). Consumed by `CliContractTest.ItemlessErrorResponseContractV1_13` and `OpenSpecValidateCliErrorFallbackTest`. The warning-only and items-plus-error edge cases are derived from this capture in-test, not authored.

To re-capture, regenerate in the same isolated way. Never hand-edit a capture; if the CLI's output changes, re-capture and fix the tests.

## `1.14.3/` — parity corpus (one strict divergence) + positive controls for the 1.14 warnings

Real CLI **1.14.3** captures, each run in an isolated environment (`HOME`/`XDG_CONFIG_HOME`/`XDG_DATA_HOME`/`XDG_STATE_HOME` set to a throwaway directory, `OPENSPEC_TELEMETRY=0`) with project roots sanitized to `/fixture`. `1.14.3` is in `ValidatorVerdictVersionStabilityTest.FLOOR` and is the `openspecTargetVersion`.

- `validate-parity-corpus.json` / `validate-parity-corpus-strict.json`: real `validate --all --json`, default and `--strict`, over `1.6.0/parity-corpus` (`root.path` sanitized to `/fixture/parity-corpus`). Default is 12/13 valid, the same verdicts as `1.13.2`. Strict is **8/13**: the one difference from every earlier generation is `nameless-change`, which 1.14.2's new WARNING ("Text before the first requirement in "## MODIFIED Requirements" is not carried into the main spec by archive…") fails under `--strict`. `ValidatorVerdictVersionStabilityTest` pins exactly this divergence for generations ≥ 1.14.2.
- `version.txt`: bare `1.14.3`.
- One positive control per new 1.14 warning. Each holds a project tree plus `validate-all.json` and `validate-all-strict.json`, from `validate --all --json [--strict]`. The change item `c` is valid by default and invalid under `--strict`, with the level staying WARNING:
  - `unknown-meta/`: unrecognized `.openspec.yaml` keys (`skip_design`, `status`) beside an `x-tracker` key. The warning is on path `.openspec.yaml` and names only the two unknown keys (1.14.0).
  - `too-long/`: a delta requirement and a main-spec requirement over 500 characters. The delta draws the new `ADDED "…": Requirement text is very long` warning, and the main spec's rule is now a WARNING instead of INFO, so the `demo` spec item also fails under `--strict` (1.14.1).
  - `archive-dropped-text/`: a note before the first requirement in a delta's `## ADDED Requirements` section, which archive won't carry into the main spec (1.14.2).
  - `xkey-only/`: only an `x-*` key in `.openspec.yaml`. Valid with no issues in both modes, pinning the 1.14.2 exemption.
  - Consumed by `CliContractTest.NewWarningControlsContractV1_14` (CLI shape and parser verdicts) and `BuiltInValidatorWarningControlsV1_14Test` (the built-in fallback adopts none of the warnings).
- `additive-shapes/`: one project whose `.openspec.yaml` carries unknown keys, so the new optional `warnings` list is populated. It holds `show-change.json` (`show c --type change --json`, with requirement and scenario `name`), `status.json` (`status --change c --json`, with `warnings`), `instructions-proposal.json` (`instructions proposal --change c --json`, with `warnings`) and `instructions-apply.json` (`instructions apply --change c --json`, with each task's `sourcePath` and `line`). Consumed by `CliContractTest.AdditiveShapesToleranceContractV1_14` and `ChangeDeltasContractTest.ChangeDeltasV1_14`. The plugin has no `instructions apply` reader, so that capture is cross-checked against the plugin's own task count of the same `tasks.md`.

To re-capture, regenerate in the same isolated way. Never hand-edit a capture; if the CLI's output changes, re-capture and fix the tests.

## `1.3.0/schema-commands/` and `1.14.3/schema-commands/` — schema fork/init/list output

Real captures from CLI **1.3.0** (run through `npx @fission-ai/openspec@1.3.0`, which reported `1.3.0`) and the installed **1.14.3**, each in a fresh scratch project (`openspec/config.yaml` with `schema: spec-driven`) with an isolated `HOME`/`XDG_*` and `OPENSPEC_TELEMETRY=0`. Stdout only: the "Schema commands are experimental" note goes to stderr. Project roots are sanitized to `/fixture`, and fork's `sourcePath` (the CLI's own install location, which the plugin never reads) to `/cli-install/schemas/spec-driven`.

- `schema-fork.json`: `schema fork spec-driven my-fork --json`. The plugin reads `destinationPath`.
- `schema-init.json`: `schema init my-new --json --no-default`. The plugin reads `path`.
- `schema-init-desc-artifacts.json`: the same, with `--description "A custom flow" --artifacts proposal,tasks`.
- `schemas.json`: `schemas --json` after the three commands above, so it lists the package schema plus three project schemas. The settings-panel dialog test uses it.
- `schema-fork.txt` / `schema-init.txt`: the same fork and init **without** `--json`. This is the human-readable output the plugin used to treat as a file path; the tests assert no path is read from it.

All files are byte-identical between the two versions; the key sets were also compared at capture time. Consumed by `SchemaServiceTest` and `OpenSpecSettingsPanelModalityTest`.

## `1.13.2/update-legacy-pending.txt` and `1.14.3/update-legacy-pending.txt` — legacy-cleanup wording change

Real `openspec update` stdout from CLI **1.13.2** (run through `npx @fission-ai/openspec@1.13.2`) and the installed **1.14.3**. Each was captured in a fresh scratch project with isolated `HOME`/`XDG_*` and `OPENSPEC_TELEMETRY=0`, after `init --tools cursor .` and three hand-placed legacy command files (`.cursor/commands/openspec-{proposal,apply,archive}.md`), with stdin closed. The output contains no absolute paths.

The two captures differ only in the block's guidance line and the version on the "up to date" line. 1.13.2 prints `No user content to preserve:`; from 1.14.0 the CLI prints `These files will be deleted entirely. Back up any custom content before proceeding:`. Unlike the Junie-based 1.6.0 and 1.7.0 captures, these use Cursor, whose old command files current CLIs still report as legacy. Consumed by `UpdateOutputParserContractTest`, `UpdateLegacyCleanupFlowTest` and the cleanup dialog test.

## Context

The versionless fixtures at `src/test/resources/fixtures/cli/` have mixed, partly undocumented provenance: `status.json`/`instructions-*.json` date to the earliest fixture commits (≈1.3 era), `schema-*` and `update-*.txt` are documented as 1.4.1 captures, `status-with-context.json` is ≈1.5.0, and `coordination-*.json` are 1.4-generation captures of commands upstream removed at 1.5.0. The per-generation layout (`fixtures/cli/1.5.0/`, `fixtures/cli/1.6.0/`) was established by the store-health change, which already re-captured the store/register/doctor family at 1.6.0.

Live probes of the real 1.6.0 CLI (performed before this proposal) established: `schema which|validate`, `templates`, `instructions`, `status`, `validate`, and `update` all still exist and are recapturable; `workspace`/`context-store`/`initiative` do not exist; `validate --all --json` keeps a key order compatible with `CliOutputParser`'s regexes but adds a machine-path-bearing top-level `root` object, dual issue-path formats (`requirements[0]` and `requirements.0.scenarios` coexist), reworded messages, and an INFO-level issue carrying a `line` field on `valid: true` items; `status --json` adds `planningHome`/`changeRoot`/`artifactPaths`/`nextSteps` (additive; Gson ignores unknowns); the legacy-migration `update` output is reproducible by initializing a project with CLI 1.3.1 and updating with 1.6.0, and gains a `Migrated: custom profile ...` preamble ahead of the unchanged `Files to remove` block.

## Goals / Non-Goals

**Goals:**
- A committed 1.6.0-generation fixture set covering every recapturable versionless family, captured from the real CLI with isolation and sanitization.
- Explicit per-generation contract tests: 1.6 coverage added, legacy coverage preserved verbatim.
- A provenance manifest making every fixture's origin and recapturability auditable.

**Non-Goals:**
- Declaring 1.6.x a supported CLI generation in `plugin-core`/`coordination-surfaces` (the separately tracked validator-parity change owns the support declaration).
- Re-capturing the `1.5.0/` store/workset set or touching its consumers (already per-generation).
- Any new UI, CHANGELOG entry, or uiSmoke journey (test-infrastructure only).
- Fixing the `openspec update` skills-surface count change (separately tracked).

## Decisions

1. **Freeze the versionless root; never relabel by guess.** Root fixtures stay exactly where they are as legacy-generation coverage, because their provenance is mixed and partly unprovable — moving them into version-named directories would manufacture false version confidence. New captures go only to `fixtures/cli/1.6.0/`, mirroring root filenames. Root files are deleted only when their generation's support is dropped (a future spec-delta change). Alternative rejected: re-pointing existing tests at 1.6 fixtures — that silently deletes the only 1.3–1.5 parse coverage while those generations remain supported.
2. **Provenance manifest over folklore.** `fixtures/cli/README.md` records, per file: capturing CLI version (or best-evidence era), capture recipe pointer, and recapturable yes/no. The `coordination-*.json` files are marked pinned-forever (source commands removed upstream at 1.5.0); `CoordinationContractTest` javadoc says the same.
3. **Per-generation nested test classes, not parameterization.** Cross-generation `@ParameterizedTest` would squeeze assertions to the common denominator of two generations whose values differ (reworded messages, new keys, different ordering) — the vacuous-test failure mode. Instead: nested V16 classes with exact-value assertions against the 1.6 captures, following `StoreWorksetContractTest`'s pattern. Legacy assertion sets are untouched.
4. **Seed captures to make assertions non-vacuous.** The validate seed deliberately exercises every issue class: a fully valid spec, a valid spec with a WARNING, a missing-SHALL spec (`requirements[0]` ERROR), a delta-less change (ERROR), and a valid change whose delta has a non-canonical level-3 header (the new INFO+`line` issue). Without the warning-on-valid and INFO-on-valid seeds, the V16 twin of the `warningCount()==0` regression test would assert nothing.
5. **Update captures use a real legacy project.** `update-*.txt` at 1.6.0 is captured against a project initialized by CLI 1.3.1 (`npx -y @fission-ai/openspec@1.3.1 init --tools junie .`) under isolated `HOME`/`XDG_*`, then updated by the installed 1.6.0 CLI — the proven recipe. If 1.6 no longer regenerates the pending block after `--force`, that is recorded as a behavioral finding, not papered over with a fabricated fixture.
6. **Capture isolation is universal.** Every capture — including project-local ones — runs under `HOME`/`XDG_DATA_HOME`/`XDG_CONFIG_HOME`/`XDG_STATE_HOME` pointed at fresh temp dirs (registry/config/telemetry all touch global dirs), with absolute paths sanitized to `/fixture/...` and a final grep for machine-specific segments before commit.
7. **No `show --json` fixture.** The 1.6 change to requirement `text` (full multi-line body) has no consumer in `src/main/java` — verified by grep — so no fixture or test is added; the finding is recorded here instead.

## Risks / Trade-offs

- [1.6 captures expose a real parser gap despite the tolerant-parser probe — residual risk is `CliOutputParser`'s key-order-sensitive regexes] → The fix lands in this change together with the failing contract test, per the contract-test discipline; probes indicate 1.6 key order is compatible.
- [The frozen root ages into confusion as generations accumulate] → The manifest makes the freeze explicit and auditable; the exit path (delete on support-drop) is documented in the modified `ci` requirement.
- [Seeded projects drift from what real users produce] → Seeds are authored only to reach CLI-emitted states (the CLI's own output is what's captured); assertions target whatever the real CLI returned, never forced to match pre-1.6 expectations.

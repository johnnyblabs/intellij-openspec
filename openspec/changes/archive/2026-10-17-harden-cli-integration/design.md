## Context

The plugin parses CLI `--json` output in about eight places, each with its own rules. See proposal.md (Why) for the failures this causes. Today:
- **Payload extraction:** `CliJson.extractJsonPayload` is applied by `parseJsonOutput` and the coordination readers. `parseChangeStatus`, `parseArtifactInstruction`, `ArchiveJson.object`, `WorkflowProfileService.parseSnapshot` and `SchemaService.parseSchemaList` parse raw stdout.
- **The error envelope** is recognised in two places only: `parseJsonOutput`, for any error `status` entry when there are no `items`, and `ArchiveJson`, which reads the first status entry.
- **Exit codes** are checked inconsistently. `cliOrFallback` ignores the exit code on the JSON path. `getArtifactStatus` treats any non-zero exit like a CLI that did not run, and returns its cache.
- **Environment:** `CliRunner` sets only `OPENSPEC_TELEMETRY=0` and the login-shell PATH.

**Upstream contract (CLI 1.14.3, verified by openspec-guru with isolated `XDG_*`):**
- **Envelope:** most `--json` failures exit 1 and print `{<the command's empty shape>, status:[{severity, code, message, target?, fix?}]}` on stdout, with empty stderr.
- **Exit 1 is not always a failure.** `validate` exits 1 with a normal `items` report when an item is invalid, and `schema validate` does the same with `{valid:false,…}`.
- **Exceptions to the envelope:**
  - `schema which/validate` use a legacy `{error, available}` shape.
  - `templates` and commander argument errors print text on stderr only.
  - `store doctor` exits 0 with its errors nested in `stores[].status` and `openspec_root.status`.
  - In `list --json`, `changes[].status` is a string, not the envelope.
- **Noise:** none on stdout in `--json` runs. `OPENSPEC_NO_UPDATE_CHECK=1` suppresses the update notice, which `update` prints to stdout. `NO_COLOR` removes ANSI from stderr.

## Goals / Non-Goals

**Goals:**
- Parse once, in one helper, with one failure model.
- Only the CLI's answer decides between "the CLI said no" and "the CLI didn't answer", and callers treat the two differently.
- Every failure shape the plugin reads is pinned by a captured fixture.

**Non-Goals:**
- Process cancellation and process-tree cleanup.
- Login-shell PATH detection.
- Loading schemas on the UI thread (all separate follow-ups).
- The upstream inconsistencies themselves: a broken `.openspec.yaml` validates as valid, `status`/`archive` resolve the root from the current directory, `store doctor` exits 0. These are recorded internally and not raised upstream.

## Decisions

### D1. One response reader: `CliJson.read(CliResult)`

The reader returns a small sealed result.
- **`Ok(JsonElement payload, int exitCode)`:** the payload parsed and carries no error-severity top-level `status` entry.
- **`Failed(String message, @Nullable String fix, @Nullable String code, Kind kind)`:**
  - `kind = ENVELOPE` when an error `status` entry is present, whatever its `code`, and whatever the exit code;
  - `kind = UNREADABLE` when no JSON parses. Its message is stderr with ANSI stripped, or "no JSON output".

The exit code alone never turns a parsed payload into a failure, because `validate`'s exit-1 items report and `schema validate`'s `{valid:false}` are real answers. Each caller decides what a non-zero `Ok` means, and only `validate` and `schema validate` accept one. For every other caller the rule is: `Ok` with exit ≠ 0 and no envelope reads as `UNREADABLE`.

`CliRunner.CliException` (could not start, timed out) stays separate. That is the CLI giving no answer, not an answer of "no".

*Alternative:* fix each parser in place. Rejected: that is how eight call sites drifted apart, and the next new call site would drift again.

### D2. Payload extraction is line-anchored

`extractJsonPayload` currently cuts at the first `{` or `[` anywhere in stdout, so a preamble such as `[WARN] …` gives the wrong slice. The new rule: the payload starts at the first line whose first non-blank character is `{` or `[`.

`OpenSpecValidateAction` currently routes to the text parser unless stdout *starts* with `{`. It changes to "JSON when `read` returns `Ok` with a payload or `Failed(ENVELOPE)`; text otherwise". A banner in front of the JSON no longer drops every issue detail.

### D3. Callers

| Call site | `Ok` | `Failed(ENVELOPE)` | `Failed(UNREADABLE)` / `CliException` |
|---|---|---|---|
| `cliOrFallback` (validate) | `items` present: verdict from items. No `items`: built-in fallback | built-in fallback | built-in fallback |
| `getArtifactStatus` | parse the DAG and cache it | evict the cache, record `statusError(change)`, return null | keep today's behaviour: return the cached DAG |
| `getInstruction` | parse | throw `CliException` with the envelope message | throw with the stderr message |
| `ArchiveJson.parse` | unchanged: it already gates on the exit code and reads the envelope's message and `fix`; its stdout now goes through `extractJsonPayload` | refusal with the envelope message and `fix` (unchanged) | unchanged |
| `UpdateLegacyCleanupService` re-run | exit 0: parse the text | n/a (text command) | exit ≠ 0 or `CliException`: "couldn't verify" |
| `storeDoctor` (Store Doctor action, `doctor --store <id>`) | highest-severity top-level diagnostic, or null when there is none (healthy) | that diagnostic | `Diagnostic.unknown()` (health unknown) |
| `parseStoreDoctor` (store listing, `store doctor <id>`) | per store: diagnostics from the store's own `status` (unchanged; it already repeats the root's errors) | n/a | entry unchanged (today's behaviour) |
| `SchemaService.listSchemas` | parse and cache | log, return empty, **do not cache** | as for ENVELOPE |
| `WorkflowProfileService` (`config list`) | parse | log, use the core defaults | as for ENVELOPE |

The profile fallback stays cached until the next `refresh()` (project open, tool-window open, Settings, a profile switch), unlike the schema list: action `update()` reads the active workflows and must never run the CLI.

`statusError` reaches the panel the way a null DAG does today. Where the panel currently shows nothing for a change without a DAG, it shows the CLI's message. The exact surface is confirmed with plugin-ui-specialist during apply.

Captured on 1.14.3 (task 1.2): `doctor --store <id>` for a store whose root was removed returns a top-level `unhealthy_store_root` envelope with `fix` (exit 1), which today's parse already reads. Its silent-wrong path is a timeout or non-JSON output, which returns null and makes the action say "healthy". The nested, exit-0 shape belongs to `store doctor <id>`, which the store listing uses: the store's own `status` repeats the root's error, which today's parse already keeps, so the listing needs only a regression lock.

### D4. Environment and ANSI

- **Environment:** `CliRunner` adds `NO_COLOR=1`, `FORCE_COLOR=0` and `OPENSPEC_NO_UPDATE_CHECK=1`. `CliDetectionService`'s version probe gets the same values.
- **Stripping:** `CliText.stripAnsi` (pattern `\u001B\[[0-9;?]*[ -/]*[@-~]`) is applied once, to stdout and stderr as `CliRunner` captures them, so every message shown (the workflow panel, Manage AI Tools, the profile switch, the console) and every pattern match sees plain text. A raw escape byte can't be part of valid JSON, which encodes it as `\u001b`. `UpdateOutputParser` also strips its input, for text that didn't come through `CliRunner`.
- **Why both:** the variables are the first layer. Stripping is the second, because a user's shell can still force colour through a node wrapper.

### D5. Fixtures

All failure fixtures are captured from real 1.14.3 runs by `scripts/capture-error-fixtures.py` into `src/test/resources/fixtures/cli/1.14.3/errors/`, as `<name>.stdout`, `.stderr` and `.exit` files. `archive-unknown` lives there too and is read by its own `ArchiveJsonContractTest` case, because the `archive-1.14.3/<case>/` walker expects the spec-merge layout of `capture-archive-fixtures.py`.
- **Isolation:** each capture uses an isolated `HOME`/`XDG_DATA_HOME`/`XDG_CONFIG_HOME`.
- **Sanitising:** paths are replaced with `/fixture`, the README's existing convention.
- **Provenance:** recorded in the fixtures README, which also loses its "no_openspec_root never captured" line.

The set:
- **Root and item errors:** `validate-noroot`, `list-noroot`, `list-noroot-withstores`, `validate-unknown`, `show-error`;
- **Change errors:** `status-unknown`, `status-badyaml`, `instructions-unknown-artifact`;
- **Archive:** `archive-unknown`;
- **Doctor:** `doctor-store-missing-root`, `store-doctor-missing-root`;
- **Text and other shapes:** `config-list-default` and `config-list-custom`, which replace the hand-written JSON in `WorkflowProfileServiceTest`, `update-fail.stderr`, and `update-legacy-pending-ansi` plus its `-plain` twin, both captured from the same seeded project, with and without `FORCE_COLOR=1`. The `config-list` set covers the default profile and a custom one, since real output omits `workflows` for the default.

Malformed and truncated JSON cases are made by cutting a captured fixture, not written by hand.

## Risks / Trade-offs

- **Evicting the status cache on an envelope could flicker** for a change mid-rename, where the CLI briefly rejects it. → Eviction happens only on an envelope, which is a definite answer, and the next refresh repopulates the cache. Timeouts still keep the cache.
- **"Exit ≠ 0 with JSON and no envelope is unreadable"** could misclassify a future command that reports results with exit 1. → Only `validate` and `schema validate` are allowed; a new caller has to choose explicitly. The contract fixtures catch a shape change.
- **Health unknown is a new store state.** → It is a muted label, not an error colour, so a slow doctor run doesn't alarm users.
- **`NO_COLOR` changes captured text** if any test fixture relied on colour. → Today's `update-*.txt` fixtures contain 0 escape bytes, so this is safe.

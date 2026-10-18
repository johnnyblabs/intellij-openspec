## Why

Several places where the plugin runs the OpenSpec CLI turn a failed run into a confident, wrong answer:
- **Legacy-file cleanup.** It reports "openspec update reports a clean state" when the verification re-run of `openspec update` fails or times out.
- **Validate.** It reports a PASS with zero issues when `validate --json` prints malformed or truncated JSON.
- **Store health.** The coordination panel shows a store as healthy when `doctor --store` fails.
- **Artifact status.** It quietly keeps showing a change's old artifact status when `openspec status` rejects the change, for example after it was renamed or deleted.

Other call sites crash or lose the CLI's message instead:
- `status`, `instructions` and `archive` parse stdout without the payload extraction the other parsers use, so any line printed before the JSON throws, or reads as a refused archive after the archive already happened.
- An `instructions` failure shows "empty response". The CLI's reason was in the stdout envelope, but the plugin read stderr.

A survey of CLI 1.14.3 settled the contract the plugin should hold to. Most `--json` failures exit 1, print one JSON document on stdout and leave stderr empty. Each document carries a `status: [{severity, code, message, target?, fix?}]` envelope.

The envelope is not uniform, though:
- `schema` uses an older `{error, available}` shape.
- `templates` and argument errors print plain text on stderr only.
- `store doctor` exits 0 and puts its errors inside each store's own `status` instead of the top-level one.
- The no-root code is `no_openspec_root`, or `no_root_with_registered_stores` when any store is registered.

The plugin also never asks the CLI for plain output. Colour codes reach messages users see, and they can break the anchored patterns that recognise `update`'s output.

This hardening also covers the dormant no-root parse item that the 1.9 work deferred. Tracked in the linked tracker entry.

## What Changes

- **One way to read a CLI JSON response.** Every call site that parses `--json` output reads it through one shared step. The step takes the payload out of stdout, recognises the `status[]` error envelope under any code, and takes the exit code into account. A failed run is reported as a failure that carries the CLI's own message and `fix`. It is never treated as an empty success, and it is never mistaken for the command's normal result.
- **Validate never passes on unreadable output.** Malformed or truncated JSON, and a JSON response that carries no verdict, make the run count as failed. The built-in validator then gives the verdict, as it already does when the CLI cannot run.
- **Artifact status and instructions report the CLI's reason.** When `status` or `instructions` is rejected, the panel says why, using the envelope's message. It does not fall back to the cached result for a change the CLI no longer accepts. A line printed before the JSON no longer causes an error.
- **Archive reads its result through the same step.** A successful archive is never reported as refused because of output printed before the JSON.
- **Legacy cleanup verifies honestly.** If the verification re-run of `openspec update` fails, the plugin says it could not confirm the cleanup. It no longer says the state is clean.
- **Store health counts nested errors.** A store whose doctor run fails is shown as "health unknown". Errors the CLI reports inside a store's own `status` count towards its health, even when the command exits 0.
- **Quiet, uncoloured CLI runs.** Every CLI run sets `NO_COLOR=1`, `FORCE_COLOR=0` and `OPENSPEC_NO_UPDATE_CHECK=1`, alongside the existing `OPENSPEC_TELEMETRY=0`. Any colour codes still present are stripped before text output is shown or parsed.
- **Not in this change**, as separate follow-ups:
  - cancelling and process-tree cleanup in `CliRunner`;
  - more robust login-shell PATH detection;
  - loading the schema list off the UI thread.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`:
  - CLI JSON responses are read through one contract that recognises the error envelope and the exit code;
  - CLI runs use a plain-output environment;
  - artifact status and instructions failures show the CLI's reason and never a stale result.
- `validation`: malformed, truncated or verdict-less `validate --json` output is a failed run that falls back to the built-in validator.
- `cli-update`: a failed verification re-run after legacy cleanup is reported as unverified, not clean.
- `store-workset-surface`: a failed store doctor run reads as health unknown, and nested per-store errors count towards health.

## Impact

- **Code:**
  - `util/CliRunner`: environment, plus a new shared response reader next to `CliJson`;
  - `util/CliOutputParser`: `parseJsonOutput`, `parseChangeStatus`, `parseArtifactInstruction`;
  - `actions/OpenSpecValidateAction.cliOrFallback`;
  - `services/ArtifactOrchestrationService`;
  - `services/ArchiveJson`;
  - `services/UpdateLegacyCleanupService`;
  - `coordination/CoordinationService.storeDoctor`;
  - `CoordinationPanel`: health-unknown presentation;
  - `services/WorkflowProfileService` and `SchemaService`: list parsing;
  - `UpdateOutputParser`: ANSI stripping.
- **Tests:** contract fixtures captured from the real 1.14.3 CLI for each failure shape:
  - `no_openspec_root` and `no_root_with_registered_stores`;
  - `unknown_item`;
  - `change_error` from `status` and `instructions`;
  - `show_error`;
  - archive refusals;
  - `store doctor` with nested errors;
  - `config list --json`, which today is tested only with hand-written JSON;
  - coloured `update` output.

  Each fixed silent-wrong-answer path also gets a test that would have passed before the fix.
- **Compatibility:** no change to supported CLI versions (1.3.0 minimum, 1.14.3 target). The envelope is read when present, and older shapes keep today's handling. No platform API changes; IntelliJ IDEA 2024.2+ support is unaffected.
- **Docs:** CHANGELOG `### Fixed`, plus `docs/feature-reference.md` where it describes CLI fallback behaviour.

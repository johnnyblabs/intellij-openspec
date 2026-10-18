## 1. Contract fixtures

- [x] 1.1 Add `scripts/capture-error-fixtures.py` and use it to capture the D5 failure set from real CLI 1.14.3 (isolated `HOME`/`XDG_*` in temp dirs, paths sanitised to `/fixture`) into `src/test/resources/fixtures/cli/1.14.3/errors/`. Each case gets `.stdout`, `.stderr` and `.exit` files, plus `config-list-{default,custom}` and `update-legacy-pending-{plain,ansi}` (the latter with `FORCE_COLOR=1`). Record provenance in the fixtures README and remove its "never captured" note for `no_openspec_root`. Verify:
  - `validate-noroot.stdout` carries `"code":"no_openspec_root"`;
  - `list-noroot-withstores.stdout` carries `no_root_with_registered_stores`;
  - the ANSI capture contains escape bytes;
  - the README lists every file.
- [x] 1.2 Capture `doctor --store <id> --json` for a registered store whose root was deleted, and record where its errors sit (top level, `stores[].status` or `openspec_root.status`). Verify: the fixture exists, and the README states the observed nesting, which 3.3 relies on. If errors are nested, 3.3's test first asserts the top-level `status` is empty, so it cannot pass on today's top-level-only parse.

## 2. Shared reader and environment

- [x] 2.1 Make `CliJson.extractJsonPayload` line-anchored (D2). Verify:
  - `CliJsonTest` gains a `[WARN] …` preamble case that fails on the old first-bracket rule;
  - the existing telemetry-banner case still passes.
- [x] 2.2 Add `CliJson.read(CliResult)` returning `Ok`/`Failed(ENVELOPE|UNREADABLE)` (D1). Verify: a contract test over every 1.2 and 1.1 fixture asserts `Failed(ENVELOPE)` with the fixture's `code`, `message` and `fix`. Further cases:
  - a fixture prefixed with a text line reads the same as the plain one;
  - a truncated fixture (cut from a capture) reads `UNREADABLE`;
  - `validate-parity-corpus.json` with exit 1 reads `Ok`.
- [x] 2.3 Add `NO_COLOR=1`, `FORCE_COLOR=0` and `OPENSPEC_NO_UPDATE_CHECK=1` to `CliRunner` and to `CliDetectionService`'s probe, and add `CliText.stripAnsi` (D4). Verify:
  - a test asserts the built command line's environment carries all four variables;
  - `stripAnsi` turns the ANSI capture into byte-equality with `update-legacy-pending.txt`, or with a documented difference if the captures differ in other ways.

## 3. Callers

- [x] 3.1 Route `cliOrFallback` and `parseJsonOutput` through `read` (D3), so that malformed or verdict-less JSON falls back and a prefixed payload stays on the JSON path. Verify: tests in `OpenSpecValidateCliErrorFallbackTest` using `validate-noroot`, a truncated capture and an `{}` payload all yield the built-in verdict. The truncated, `{}` and prefixed cases must fail against the pre-change code (run them on `main` first and note the result); `validate-noroot` already falls back today and is a regression lock. `ValidatorVerdictVersionStabilityTest` and the existing parity tests stay green.
  - Recorded 2026-10-17: against the pre-change parser and routing, `OpenSpecValidateCliErrorFallbackTest` ran 7 tests with 3 failing (truncated, `{}`, prefixed); with the change all pass. Two existing assertions changed deliberately: `CliOutputParserTest` `{}` (was PASS, now no verdict) and `CliContractTest` warning-only status without items (was accepted, now no verdict). An error status beside `items` still takes its verdict from the items.
- [x] 3.2 `ArtifactOrchestrationService`:
  - `getArtifactStatus` evicts the cache and records `statusError` on `Failed(ENVELOPE)`, and keeps the cache on `CliException`/`UNREADABLE`;
  - `getInstruction` throws with the envelope message;
  - `parseChangeStatus`/`parseArtifactInstruction` read via `read`.

  Show `statusError` in the workflow panel where a null DAG shows today; confirm the surface with plugin-ui-specialist. Verify:
  - a test with a cached DAG and then `status-unknown` returns null plus the message;
  - a timeout returns the cached DAG;
  - `instructions-unknown-artifact` produces an exception whose message contains the envelope text;
  - a prefixed status fixture parses.
- [x] 3.3 `parseStoreDoctor` keeps the missing-root error from `store-doctor-missing-root` (store listing; the capture repeats it in the store's own `status`, so this is a lock); `storeDoctor` (the Store Doctor action) returns a health-unknown diagnostic on `UNREADABLE`/`CliException`; the Store Doctor action shows unknown health as an informational dialog, not "healthy" (no tree marker: a failed check is not upstream state; surface settled with plugin-ui-specialist). Verify:
  - `store-doctor-missing-root` yields the root error exactly once, after asserting its top-level `status` is empty (a regression lock: the store's own `status` already carries it);
  - `doctor-store-missing-root` yields the top-level error with `fix` (a regression lock: today's parse reads it);
  - a timeout yields unknown, not null or healthy;
  - existing 1.5.0 doctor fixtures are unchanged.
- [x] 3.4 `UpdateLegacyCleanupService` verification re-run: exit ≠ 0 or `CliException` reports "couldn't verify the cleanup" with the stripped stderr; `UpdateOutputParser` strips ANSI before matching. Verify:
  - a test driving the re-run with `update-fail` asserts the unverified message, and that it fails on the old code;
  - the ANSI capture parses to the same file list as the plain capture.
- [x] 3.5 `ArchiveJson` (payload extraction; see D3), `WorkflowProfileService.parseSnapshot` and `SchemaService.listSchemas` read via `read`; a failed schema list is not cached. Verify:
  - `ArchiveJsonContractTest` gains a prefixed-success case that reports completed;
  - `WorkflowProfileServiceTest` uses the captured `config-list-default` and `config-list-custom` fixtures instead of inline JSON;
  - a test asserts that a `listSchemas` call answered with exit 0 and a truncated cut of the captured schemas output, followed by a good one, returns the second result (a non-zero first call already skips the cache today and would pass vacuously).
- [x] 3.6 Docs: in CHANGELOG `## Unreleased`, add a `### Fixed` entry per user-visible fix:
  - validate no longer passes on unreadable CLI output;
  - the panel shows why the CLI rejected a change;
  - cleanup no longer claims "clean" when it couldn't check;
  - store health no longer reads healthy on a failed doctor run;
  - archive no longer reads as refused because of extra output;
  - no colour codes in messages.

  Update `docs/feature-reference.md` where it describes CLI fallback. Verify: the entries are present, and the pre-commit leak grep from the project's agent conventions is clean over the diff.

## 4. Integration checks

- [x] 4.1 Run test-engineer in AUDIT mode over the diff (new parsers, no inline expected-shape literals) and intellij-code-reviewer over the panel and service changes, and resolve their findings. Verify: both report no open blocking findings.
- [x] 4.2 Run `./gradlew build`, `./gradlew verifyPlugin` and `openspec validate harden-cli-integration --strict`. Verify: all pass, and the JaCoCo floor holds or is ratcheted up.
  - Recorded 2026-10-17: `./gradlew build` green; `verifyPlugin` Compatible on all five IDEs, with only the two documented deprecation exceptions (IU-261: 1, IU-262: 2, unchanged from the last release); `openspec validate harden-cli-integration --strict` valid. Coverage measured 0.5773/0.5471/0.4924 (INSTRUCTION/LINE/BRANCH); floors raised 0.535/0.510/0.448 → 0.567/0.537/0.482.
- [x] 4.3 Sandbox pass (CLI 1.14.3):
  - rename a change's directory while it is selected: the panel shows the CLI's reason, not stale chips;
  - delete a registered store's root: Coordination shows the nested diagnostic.

  Record the results here.
  - Recorded 2026-10-17 (CLI 1.14.3, IDEA 2024.2 sandbox, isolated XDG state):
    - **Rejected change** (`broken-meta`, invalid `.openspec.yaml`): the "Can't read change" card appeared. The first pass found two gaps: an edit showed up only after the IDE's autosave (about 15 s idle), because OpenSpec reads the file on disk and the card's Refresh did not save first; and a rejected change lost its selection, so a later refresh jumped to the first change. Fixed: the card's Refresh and the toolbar Refresh save open documents first, and a rejected change stays selected. Rechecked: Refresh now updates straight away in both directions (fixed → chips, re-broken → card). Without Refresh, the panel follows the IDE's autosave, which is the platform's setting.
    - **Store Doctor** on `demostore` (store root removed): the warning dialog showed OpenSpec's `unhealthy_store_root` message, not "healthy".
    - **Renamed change** (`demo-add-farewell` moved while selected): fine, no stale chips.
    - **Validate**: a normal CLI verdict in the console, no colour codes.


## Workflow follow-up

- Open separate tracker entries for CLI process cancellation and tree-kill, login-shell PATH robustness, and moving `listSchemas` off the UI thread.
- Archive the change after review, then close its tracker entry.

# Tasks

## 1. Help sweep covers every command the plugin runs

- [x] 1.1 Add `doctor`, `workspace`, `context-store` and `initiative` to `COMMANDS` in `scripts/capture-help-sweep.py`, and re-sweep all 18 releases. Verify: `git diff --stat` on `fixtures/cli/*/help/` shows only new files and new `index.json` entries, with every existing help file byte-identical.
- [x] 1.2 Extend `HelpSweepContractTest`: `workspace`, `context-store` and `initiative` exist exactly in 1.4.x, and `doctor` exists from 1.5.0. Verify: the tests pass, and deleting `1.4.1/help/workspace.txt` turns them red.
- [x] 1.3 Add a test-side `CliHelp` reader that takes a release's commands from `index.json` and its `--long` flags from the `Options:` lines only. Give it unit tests on real captures: `archive --json` is absent in 1.4.1 and present in 1.5.0, a description that mentions a flag doesn't count, `--no-default` is read, and value shape is read (`--schema <name>` takes a value, `--json` doesn't). Verify: the tests pass.

## 2. Flag table and argument builder

- [x] 2.1 Add `util/CliFlags`, recording each command path and `(command, flag)` with its `since`, optional `until` and whether the flag takes a value, for every command and flag the plugin passes today, plus `init --scope` since 2.1.0. Add `CliFlagTableContractTest`, which compares the table with every captured release's help through `CliHelp`, with a floor of the 18 releases. Verify: the test passes. Run the mutation checks: each of these turns it red:
  - lowering `archive --json` to 1.3.0;
  - lowering `init --scope` to 2.0.0;
  - raising `archive --json` to 1.6.0;
  - moving `workspace`'s `until` to 1.6.0;
  - marking `--json` as taking a value.
- [x] 2.2 Add `util/CliArgs`: `command(...)`, plus `arg`, `flag(name[, value])`, `optional(name[, value], when)` and `env(k, v)`, all package-private, and the public `resolve(version)` and `display()`. Resolving drops an unsupported optional flag together with its value. It throws `UnsupportedCliArgumentException`, which extends `CliRunner.CliException`, for an unsupported required flag or command, and `IllegalStateException` for a flag or command missing from the table. A null or unparseable version resolves as `PluginVersions.targetCliVersion()`. Give it unit tests for each branch. Verify: the tests pass, and the exception message names the command, the flag and the `since` release.
- [x] 2.3 Change `CliRunner.run` to take `CliArgs`, with timeout and env variants, and remove the `String...` overloads. Resolve against the detected version before starting the process. Before the first detection completes, run `detect()` once, under a lock. Verify with unit tests:
  - a refused `CliArgs` never reaches `buildCommandLine`;
  - the env from `CliArgs` is applied on top of the plain-output environment;
  - a run before any detection triggers exactly one detection and resolves against the version it found;
  - an available CLI with an unreadable version resolves as the target release.

## 3. One catalog of invocations

- [x] 3.1 Add `util/CliCommands` with an `@OfferedOn(since, until)` annotation, and move every invocation into it as a `public static CliArgs` builder:
  - validate, show, archive;
  - schema fork/init/which, schemas, templates;
  - status, instructions;
  - new change, list, context;
  - update (with its dry-run and force forms), init;
  - the store, workset and doctor commands;
  - the 1.4 workspace, context-store and initiative commands.

  Split the generic `runListJson` and `runStoreWrite` helpers into one builder per command. Point each `since`/`until` at the existing service floor constant, making a constant public where needed. Verify: the project compiles.
- [x] 3.2 Migrate the call sites in `ChangeService`, `SchemaService`, `UpdatePreviewService`, `ExploreContextService`, `ArtifactOrchestrationService`, `WorkflowProfileService`, `CoordinationService`, `ScaffoldingService`, `ChangeDeltaModel`, `OpenSpecValidateAction`, `OpenSpecCliAction` and its subclasses, `ManageAiToolsDialog`, `WorkflowActionPanel`, `OpenSpecToolWindowPanel` and `WorkflowProfileSwitchService` to the builders. Derive display strings, such as `OpenSpecValidateAction`'s, from `CliArgs.display`.
  - Rewrite the tests that stub the `String...` overload so they assert `resolve(v)`'s argv equals a literal list, never `any(CliArgs.class)`: `CliRunnerTest`, `OpenSpecValidateStrictTest`, `OpenSpecUpdateActionTest`, `ArchiveDelegationTest`, `BulkArchiveProductionArchiverTest`, `ChangeDeltasContractTest`, `SchemaServiceTest`, `StatusPassThroughContractTest`, `UpdatePreviewServiceTest`, `UpdateCleanupVerificationTest`, `ArtifactOrchestrationServiceTest` and `StoreWorksetWriteServiceTest`.
  - Add argument tests for `ScaffoldingService.initWithCli`, the dialog's Configure/Add/Update commands, the `CoordinationService` reads and `WorkflowProfileSwitchService`.
  - Verify: all of these pass. The service-gate tests (`ArchiveDelegationTest` and the coordination and update-preview gating tests) still fail when their floor check is deleted.
- [x] 3.3 Add `CliArgsVersionMatrixTest`. It reflects over every `CliCommands` builder with sampled parameters (`String`, `@Nullable String`, `boolean`, `List<String>`) and resolves each one against every captured release. Inside `@OfferedOn`, every resolve must succeed and emit only commands and flags in that release's help. Inside the range, no requested flag may be dropped unless its `(builder, flag)` pair is on the test's allow-list. Help is read through `CliHelp`, never `CliFlags`. Verify: the test passes, and every builder is exercised on at least one release. Run the mutation checks; each must turn the test red:
  - widening `archive`'s `@OfferedOn` to 1.3.0, alone or with `--json` made `optional`, turns it red on 1.3.1 and 1.4.1;
  - making `init` pass `--scope` turns it red on every release before 2.1.0;
  - extending `workspace list` to 1.6.0 in both the table and the range turns it red on 1.5.0.
- [x] 3.4 Add `CliFlagLiteralHygieneTest`. It fails on any `"--[a-z][a-z-]*"` literal under `src/main/java` outside `CliCommands` and `CliFlags`, on `--x` inside any other string literal (such as `"init --tools " + id`), on any `CliArgs.command(` outside `CliCommands` or with an argument that isn't a string literal, on `getInstance`/`getService`/`CliVersion` inside `CliCommands`, and on any builder parameter type the sampler can't cover. The one exemption is `CliDetectionService`'s `--version`, listed with its reason. Verify: the test passes. Run the mutation checks; each must turn the test red:
  - adding `"--foo"`, or a `"init --tools " + id` command string, to a service turns it red and names the file and line;
  - building a command from a non-literal word turns it red.

## 4. Per-release workflow contract tests

- [x] 4.1 Make `CoreWorkflowsContractTest` a `@TestFactory` over `FixtureVersions.versionsWith("workflows.json", FLOOR)`, with a FLOOR that includes 1.17.0, 2.0.0, 2.1.0 and 2.1.1. Have `WorkflowParityContractTest` loop over the same releases, reporting every failing release in one assertion. Verify: both pass on every release. Run the mutation checks: deleting `2.0.0/workflows.json` fails the floor, and changing a core id in one release's capture fails that release only.

## 5. Optional real-CLI gate

- [x] 5.1 Add `scripts/install-cli-matrix.py`, which installs the 18 swept releases into `$OSP_CAPTURE_CACHE` through `openspec_capture.package`, and a `cliMatrix` Gradle task that runs tests tagged `cli-matrix`. Exclude that tag from `test`. Verify: `./gradlew test` doesn't run the tag, and `./gradlew cliMatrix` skips with a message when the cache is empty.
- [x] 5.2 Add `CliMatrixRealCliTest`, tagged `cli-matrix`. For each installed release, it initialises a scratch project with `--tools none` under an isolated HOME and XDG, with telemetry off and stdin closed. It then runs every in-range builder through `node <pkg>/bin/openspec.js` and fails on `unknown option`, `unknown command` or `too many arguments`. Verify: `./gradlew cliMatrix` passes locally on all 18 releases. Run the mutation check: a builder that emits `init --scope` on 2.0.0 makes it fail.

## 6. Documentation and verification

- [x] 6.1 Document the flag table, the builder catalog and the guard tests for contributors. Add an "Adding a CLI flag" section to `CONTRIBUTING.md`, or wherever contributor docs live, and the `cliMatrix` gate and install script to `scripts/README.md`. Update `fixtures/cli/README.md` for the new help commands. Verify: `DocumentationHygieneTest` passes, and the grep for homelab identifiers over the changed docs is empty.
- [ ] 6.2 Run `./gradlew build` and `openspec validate --all --strict` on the branch. Verify: both are green, origin CI is green, and after the user mirrors, the GitHub matrix (build, macOS, Windows, verify) is green.

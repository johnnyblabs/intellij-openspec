# Proposal

## Why

The plugin promises to work with every OpenSpec CLI from 1.3.0 to 2.1.x, but nothing checks that the arguments it passes exist in the CLI the user has installed. Flags are string literals handed straight to the runner from about 30 call sites, and each service gates whole commands with its own version floor, if it gates them at all. The 2.x features coming next add flags that only newer releases accept: `init --scope` is new in 2.1.0, and 2.0.0 rejects any option it doesn't know. A flag that leaks to an older CLI fails at runtime, and only for users on that release, which no test covers today. This change adds that check before any 2.x feature lands, so each feature is built against a test that fails when it breaks an older CLI. It is tracked in the linked tracker entry for the CLI version compatibility matrix.

## What Changes

- **One flag table in production code.** A table records, for each CLI command the plugin runs, the release that introduced it and the release that dropped it, if any. It also records the same for each flag the plugin passes to that command.
- **Arguments are built against the detected version.** Every CLI invocation is built in one catalog of argument builders, and the runner accepts only an argument list built there. A gated optional flag is left out on a release without it. A flag the command can't work without is refused with a clear error, before any process starts. The same applies to a command the release doesn't have.
- **The `--help` sweep covers every command the plugin runs.** The sweep gains `doctor`, plus `workspace`, `context-store` and `initiative`, which exist only in 1.4.x. All 18 captured releases are swept again.
- **Three guard tests:**
  - The table must agree with each release's captured `--help`.
  - Each argument builder must emit, for every captured release, only commands and flags that release's help lists.
  - A source scan must find no flag literal and no raw runner call outside the catalog.
- **More contract tests run per release.** The core-workflow and workflow-parity contract tests run over every release that has a `workflows.json` capture, not just the target. Version floors stop the set from shrinking silently.
- **An optional local release gate, `cliMatrix`.** It runs every builder against the real CLI package of each boundary release, in an isolated home. It fails on any "unknown option" or "unknown command". It isn't part of `check`.

No supported release gains or loses an action. Every flag the plugin passes today exists in every release it is passed to, and this change makes that a tested property instead of an accident. Users see two small changes:
- a command the installed CLI can't run now names the CLI release it needs, instead of showing the CLI's "unknown option";
- the Validate console line now shows the exact command, including `--json`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: new requirement that CLI arguments are built against the detected CLI version, using one flag table that is checked against each release's captured help.
- `ci`: new requirement that contract tests and the argument builders run over every captured supported release, with version floors and three guard tests.

## Impact

- **Production code:**
  - new `util/CliFlags`, `util/CliArgs` and `util/CliCommands` (the argument-builder catalog);
  - `CliRunner.run` takes a `CliArgs`;
  - call sites to migrate, in `ChangeService`, `SchemaService`, `UpdatePreviewService`, `ExploreContextService`, `ArtifactOrchestrationService`, `WorkflowProfileService`, `CoordinationService`, `ScaffoldingService`, `ChangeDeltaModel`, `OpenSpecValidateAction`, `OpenSpecCliAction` and its subclasses, `ManageAiToolsDialog`, `WorkflowActionPanel` and `OpenSpecToolWindowPanel`.
  - **Not changed:** CLI detection's own `--version` call. It runs before any version is known.
- **Tests and fixtures:**
  - `scripts/capture-help-sweep.py` gains commands, and all 18 releases' `help/` captures are taken again;
  - new guard tests, plus a `cliMatrix` Gradle task that is excluded from `check`;
  - `CoreWorkflowsContractTest` and `WorkflowParityContractTest` run per release.
- **Platform:** no new IntelliJ Platform API. The 2024.2+ support range is unaffected.
- **Follow-on changes:**
  - init-skills-scope adds `init --scope` as a 2.1.0+ table entry.
  - The 2.x feature changes add their flags the same way, and the guards check them.

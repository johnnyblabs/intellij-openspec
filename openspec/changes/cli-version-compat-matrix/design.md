# Design

## Context

- **How invocations are built today.** About 30 call sites pass `String...` arguments to `CliRunner.run`. Some build them inline, and some through small static helpers: `ChangeDeltaModel.changeShowArgs`, `OpenSpecValidateAction.cliArgs`, `SchemaService`'s list builders. `ManageAiToolsDialog` splits a command string on spaces.
- **How versions are gated today.** Each service checks its own floor constant before running a command:
  - `CoordinationService.COORDINATION_CLI_FLOOR` and `STORE_CLI_FLOOR`;
  - `ChangeService.CLI_ARCHIVE_FLOOR`;
  - `UpdatePreviewService.DRY_RUN_FLOOR`;
  - `ExploreContextService.LIST_SPECS_FLOOR`.

  These checks use `CliVersion`. Nothing ties a flag to a release.
- **The flags in use are safe today.** The #670 help sweep covers 18 releases (the newest patch of each 1.x minor, plus 2.0.0, 2.1.0 and 2.1.1). Measured against it, every flag the plugin passes is listed in every release the plugin passes it to. Three flags exist only from some release on: `archive --json` from 1.5, `update --dry-run`/`--json` from 1.15, and `init --scope` from 2.1, which the plugin doesn't pass yet. The plugin's service floors already keep the first two away from older releases.
- **What the sweep misses.** It has no `doctor`, and none of the 1.4-only `workspace`, `context-store` and `initiative` commands, although the plugin runs all four.
- **What already runs per release.** `FixtureVersions` (`versionsWith`, `atLeast`, `compare`) and `KnownGaps` come from #670. These contract families already run once per captured release: the AI-tool registry, detection, workflow invocation, the update plan, validator verdicts and artifact instructions.

## Goals / Non-Goals

**Goals:**
- A test fails when any argument builder would send a command or flag to a captured release whose help doesn't list it.
- Each invocation is written once, in one place the tests can enumerate, so a new call site can't escape the matrix.
- No runtime behaviour change on any supported release.

**Non-Goals:**
- **Converting `CliContractTest`, `UpdateOutputParserContractTest` and `ScaffoldingContractTest` to per-release runs.** They parse single captures that have no per-release twins. The output families that do differ by release already run per release since #670.
- **Validating argument values,** such as the tool ids in `init --tools`. The AI-tool registry contract covers those.
- **Adding `init --scope` or `OPENSPEC_SKILLS_SCOPE`.** That belongs to init-skills-scope. Here it is only shown to be expressible.
- **The uiSmoke runs against 2.1.1, 2.0.0 and 1.17.0.** They belong to v2.1.0 release prep.

## Decisions

### 1. A hand-written flag table, checked against captured help, not parsed at runtime

`util/CliFlags` maps a command path (`"archive"`, `"schema init"`) to a `since` release and an optional exclusive `until`. It does the same for each `(command path, flag)` the plugin passes.

- **Alternative: run `<cmd> --help` at runtime and parse it.** Rejected. It costs an extra process per command, and the help layout changed in 2.1 (`init`'s tool list moved). A wrong parse would silently drop flags in production.
- **Why a table works.** It is small, reviewable and has no I/O. `CliFlagTableContractTest` holds it to the captured help instead.

### 2. `CliArgs` is a version-free description, resolved by the runner

Builders return an unresolved `CliArgs`:

```java
CliArgs.command("archive").arg(name).flag("--yes").flag("--json").optional("--skip-specs", !sync)
```

`CliRunner.run(project, CliArgs)` resolves it against `CliDetectionService.getDetectedVersion()` just before starting the process. The `String...` overloads are removed.

- **What `resolve(version)` does.** It checks the command, then each flag, against the table:
  - an `optional` flag the release lacks is dropped;
  - a required flag the release lacks raises `UnsupportedCliArgumentException`, which names the command, the flag and the `since` release;
  - a command the release lacks raises the same exception;
  - a flag or command that isn't in the table at all raises `IllegalStateException`. That is a programming error, which the guard tests catch.
- **Why the exception extends `CliRunner.CliException`.** Every existing `catch (CliException)` already turns it into a user message, so no call site needs a new catch.
- **Only the catalog can shape arguments.** `CliArgs`'s mutators (`arg`, `flag`, `optional`, `env`) are package-private to `util`, where `CliCommands` lives. A service therefore can't extend a builder's result, and the compiler enforces it, not a scan.
- **Display text comes from the same object.** Text that shows a command, such as the validate action's "ran: …" line, comes from `CliArgs.display()`, not from a second hand-written string.
- **What `CliArgs` also carries.** Extra environment variables, which replace the `extraEnv` overload. A flag's value (`--schema <name>`) is kept with its flag, so dropping the flag also drops the value.
- **Alternative: builders take the version and resolve eagerly.** Rejected. Every call site would need to fetch the detected version. Keeping the description version-free also makes the matrix test a plain loop of `resolve(v)` over the releases.

### 3. One catalog, `util/CliCommands`, that the tests enumerate by reflection

Every invocation becomes a `public static CliArgs` method on `CliCommands`, for example `archive(String change, boolean syncSpecs)` and `schemaInit(String name, String description, List<String> artifacts)`. Call sites call these methods.

- **How the matrix test samples parameters.** `CliArgsVersionMatrixTest` reflects over every such method and calls it with sampled values:
  - `String` → `"demo"` and `""`, plus `null` when the parameter is `@Nullable`;
  - `boolean` → both values;
  - `List<String>` → empty and one element.

  It then resolves every result against every captured release.
- **What the hygiene test enforces.**
  - Builder parameters are limited to those three types, so the sampler is complete.
  - `CliArgs.command(…)` takes string literals only. The generic `runListJson(command, sub)` and `runStoreWrite(String…)` helpers in `CoordinationService` become one builder per command.
  - `CliCommands` reads nothing but its parameters: no `getInstance`, `getService`, `CliVersion` or settings. Sampling can't see a branch on hidden state.
- **Which releases a builder must work on.** Each builder carries an `@OfferedOn(since = …, until = …)` annotation, which points at the floor constant the service already gates on, for example `@OfferedOn(since = ChangeService.CLI_ARCHIVE_FLOOR)`. This works because annotations take compile-time constants, so the floor can't drift between the service and the test. The matrix test asserts both directions:
  - **inside the range:** every resolve succeeds, every emitted command and flag is in that release's help, and nothing the builder asked for is dropped, unless the `(builder, flag)` pair is on the test's short allow-list of deliberate drops. The check reads help through `CliHelp`, never through `CliFlags`, so the matrix test and the table test can't share one mistake. Turning a required `--json` into an `optional` one would otherwise slip past both;
  - **outside the range:** nothing is asserted. The service doesn't offer the action there, and some invocations stay valid beyond the range the service offers them in. For example, `list --specs` parses on 1.3, but the plugin reads its output only from 1.7. A widened range shows up as a refusal or an unlisted flag inside the new range.

  A builder that would refuse where the plugin offers the action is a user-facing failure, so the test treats it as one.
- **Alternative: keep the argument helpers in each service.** Rejected. A new call site could then skip the matrix.

### 4. Help parsing reads only option lines

`CliHelp` (test code) reads a captured help file.

- **Flags.** It takes each line in the `Options:` block that starts with whitespace and `-`, and collects the `--long` names, including `--no-x` forms, and whether each one takes a value (`<x>` or `[x]`). The block ends at the first blank line or the next `Xxx:` header.
- **Quirks.**
  - A short form can come first (`-y, --yes`).
  - `--no-validate` alone doesn't mean `--validate` is accepted.
  - `--default` and `--no-default` are listed separately.
  - Root-level options such as `--no-color` appear only in `openspec.txt`, and the plugin passes none.
- **Value shape.** The table records whether each flag takes a value. The table test checks that too, so a switch that turns into a valued option fails the test.
- **Commands.** It takes them from `help/index.json`, where a command with `"file": null` is absent.

This avoids false positives from flags that a description merely mentions.

### 5. The runner waits for the first detection, and an unknown version resolves as the target release

Until the first detection completes, `getDetectedVersion()` is null. A tool-window refresh at startup on a 1.4 install would otherwise resolve as the target release.

- **Before the first detection.** If `isDetectedOnce()` is false, `CliRunner.run` runs `detect()` once and synchronously before resolving. A lock stops concurrent first runs from repeating it. The runner already runs off the EDT, and the threading requirement holds it there.
- **After detection, when the CLI is available but `--version` didn't parse.** `resolve` uses `PluginVersions.targetCliVersion()`. The plugin is built and tested against that release, and an unparseable version string usually means a newer CLI rather than an older one.
- **When no CLI was found.** Arguments resolve as the target release too, and the run fails as it does today.

### 6. Guard tests and their mutation checks

| Test | Fails when | Mutation it must catch |
|---|---|---|
| `CliFlagTableContractTest` | For a release where the command exists, the table says the flag is supported but the release's help omits it, or the other way round. Also when a command's `since`/`until` disagrees with `index.json`. | Lowering `archive --json`'s `since` from 1.5.0 to 1.3.0. |
| `CliArgsVersionMatrixTest` | Inside a builder's offered range, the builder emits a command or flag that the release's help doesn't list, refuses, or drops a flag that isn't allow-listed. | Widening `archive`'s `@OfferedOn` to 1.3.0, alone or with `--json` made optional. Making `init` pass `--scope`. Extending `workspace list` to 1.6.0 in both the table and the range. |
| `CliFlagTableContractTest` (reverse direction) | The help lists a flag or command that the table says the release lacks. | Raising `archive --json`'s `since` to 1.6.0, or moving `workspace`'s `until` to 1.6.0. |
| `CliFlagLiteralHygieneTest` | A string literal that is a flag, or ends with one (`"--json"`, `"init --tools "`), appears under `src/main/java` outside `CliCommands` and `CliFlags`. Also when `CliArgs.command(` is called outside `CliCommands`, or its words aren't literals, or the catalog reads settings, services or `CliVersion`. The matrix test fails on a parameter type the sampler can't cover. The compiler already blocks building a `CliArgs` outside `util`. The one exemption is `CliDetectionService`'s `--version` call, which is listed with its reason. | Adding `"--foo"` or `"init --tools " + id` to a service. Building a command from a non-literal word. |

`init --scope` is added to the table as `since = "2.1.0"` under a test-only builder, so the 2.x gate is exercised now. Lowering it to 2.0.0 must turn `CliFlagTableContractTest` red.

### 7. The help sweep gains the missing commands

`scripts/capture-help-sweep.py` adds `doctor`, `workspace`, `context-store` and `initiative` to `COMMANDS`, with their subcommands parsed as before. All 18 releases are swept again. Existing `help/` files must come back byte-identical, and only new files and `index.json` entries may appear. `HelpSweepContractTest` gains assertions that `workspace` exists only in 1.4.x and `doctor` only from 1.5.0.

### 8. `cliMatrix`: an opt-in Gradle test task

- **How it is wired.** A `Test` task runs tests tagged `cli-matrix`. The default `test` task excludes that tag, so `check` never runs it.
- **Where the CLIs come from.** The task needs the boundary releases (the 18 swept releases) installed in `$OSP_CAPTURE_CACHE` by `scripts/install-cli-matrix.py`, which uses `openspec_capture.package`. It never installs anything itself, and it skips with a message when the cache is empty.
- **What a run does.** For each release, it builds a scratch project with `openspec init --tools none` and an isolated HOME and XDG (`isolated_env` semantics, `OPENSPEC_TELEMETRY=0`, stdin closed). It then resolves every builder that release is in range for, and runs it with `node <pkg>/bin/openspec.js`.
- **What counts as a failure.** Only `unknown option`, `unknown command` or `too many arguments` on stderr fails the run. Non-zero exits for semantic reasons, such as no change named `demo`, are expected and ignored.

### 9. More contract tests run per release

- **`CoreWorkflowsContractTest`** becomes a `@TestFactory` over `versionsWith("workflows.json", FLOOR)`.
- **`WorkflowParityContractTest`** extends `BasePlatformTestCase`, so it can't use dynamic tests. It loops over the same releases, collects failures per release and reports them in one assertion.

## Risks / Trade-offs

- **[Risk] Migrating 30 call sites changes arguments by accident.** → For every builder and every release, the matrix test checks the args against help. Each migrated call site's existing unit test (validate, archive, schema, update preview, coordination writes) still asserts its exact argument list. `ScaffoldingService` and the dialog get explicit argument tests.
- **[Risk] Sampling misses a branch that emits a different flag.** → Parameters are limited to `String`, `boolean` and `List<String>`. Booleans are fully enumerated and nullable strings get `null`, so every branch the parameters control is visited.
- **[Risk] The help format changes in a future release and the parser misses flags.** → `CliFlagTableContractTest` fails loudly, because table flags would look unsupported. It also has a floor check that every swept release yields at least one flag for `validate`.
- **[Trade-off] The table is maintained by hand.** It is small (about 25 commands and 45 flags), and its contract test keeps it honest. A CLI bump adds a capture, and any drift shows up as a named failure.
- **[Trade-off] `cliMatrix` stays opt-in.** It needs Node 22.20+ for 2.1 and network for 2.1's `init`. It runs at release prep, not on every push.

## Migration Plan

This is all internal. One PR changes production code and tests together. Rolling back means reverting it. No user-facing settings or files change.

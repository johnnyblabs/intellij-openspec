# Proposal

## Why

The CI build job's test step grew from about 175 seconds to about 495 seconds in a week, in steps that match commits adding CLI captures. One class, `BuiltInAddContractTest`, is 30,768 of the suite's 35,594 tests and 91 of its 286 seconds; the plain-JUnit contract tests that replay every captured CLI release together take about 100 seconds. The build job's signal is slower for every change, though most changes don't touch those captures. Tracked in the linked tracker entry.

## What Changes

- **The CLI-fixture contract tests are a suite of their own.** Plain-JUnit tests that replay captured CLI releases carry a `cli-fixtures` tag. `test` excludes them, and a new `cliFixtureTest` task runs them. `./gradlew build` still runs both, so a local build checks everything it did before.
- **A guard keeps the split honest.** A test fails when a plain-JUnit class reads captured releases without the tag, so new fixture tests don't drift back into `test`.
- **CI runs the two suites in parallel.** The `build` job runs `test`, a `cli-fixtures` job runs `cliFixtureTest`, and a `coverage` job merges both suites' coverage data and enforces the unchanged coverage floor. Analysis upload moves to the `coverage` job, so it reports the merged figure.
- **The pre-push gate runs both suites.**

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ci`: the pipeline's jobs, what the build job runs, where the coverage floor is enforced, and the CLI-fixture suite.

## Impact

- **Build:** `build.gradle.kts` (`test` excludes the tag, `cliFixtureTest`, coverage over both suites' data, `check` depends on both).
- **CI:** `.forgejo/workflows/build.yaml` (`cli-fixtures` and `coverage` jobs; analysis upload in `coverage`). The new jobs become required checks on `main` (a repository setting).
- **Hooks:** `.githooks/pre-push` runs `test` and `cliFixtureTest`.
- **Tests:** a `cli-fixtures` tag on the plain-JUnit fixture contract classes; a guard test.
- **Docs:** CONTRIBUTING (running the suites), CLAUDE.md testing notes.
- **Platform:** no plugin code changes; IntelliJ 2024.2+ support is unaffected.

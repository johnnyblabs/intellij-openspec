# Tasks

## 1. Fixtures

- [x] 1.1 Add `scripts/capture-explore-context-fixtures.py`. It builds a project with an isolated `HOME`/`XDG_*` and telemetry off. The project has a top-level capability, a nested capability (`identity/user-auth`), a spec-driven change with a nested delta, and a forked-schema change whose extra `research` artifact generates nested `research/**/*.md` files. The script records `list --specs --json`, both changes' `status --json`, and the error envelope for a missing change under `src/test/resources/fixtures/cli/<version>/explore-context/`, with the project path sanitized to `/fixture/project`. Add a README with the recipe. Verify: the four 1.15.0 fixtures exist and contain no machine paths.
- [x] 1.2 Let the script take a CLI command (`OPENSPEC_BIN="npx -y @fission-ai/openspec@1.3.1"`) and capture `status-add-mfa.json` from 1.3.1, the last version without `artifactPaths`, into `fixtures/cli/1.3.1/explore-context/`. Verify: the captured file has `artifacts` and no `artifactPaths`.

## 2. Status model

- [x] 2.1 Add `artifactPaths` to `ChangeArtifactDag` as an order-preserving map of `ArtifactPaths(outputPath, existingOutputPaths)`, plus the payload's `root` (`source`, `store_id`). Verify with a contract test on `status-research-sso.json`: keys come back in schema order (`research, proposal, specs, design, tasks`), and `research` lists `summary.md` then `vendors/okta.md`. Also on the 1.3.1 fixture: `artifactPaths` is empty.

## 3. Context sources

- [x] 3.1 Add a CLI seam to `ExploreContextService` (`Cli` interface, `setCliForTest`, defaulting to `CliRunner::run`). Take change artifacts from `artifactPaths` as in design D1: schema order, id labels, `<id> (<path>)` labels for glob artifacts, realpath containment in the change dir. Verify with `ExploreContextContractTest` on the 1.15.0 fixtures, remapped to a temp project tree with a unique marker per file:
  - add-mfa's four artifacts appear in the order proposal < specs < design < tasks;
  - the nested delta is labelled `specs/identity/user-auth/spec.md`;
  - research-sso includes `research (research/summary.md)` and `research (research/vendors/okta.md)`;
  - a Markdown file on disk that the fixture doesn't list is absent;
  - the fake CLI received exactly `status --change <name> --json`.
- [x] 3.2 Add the fallback walk from design D2. It applies when the CLI throws, returns the error envelope, has no `artifactPaths`, or answers for a store. Verify in `ExploreContextServiceTest`: nested `specs/a/b/spec.md` and `research/x/y.md` are included with their paths; `.openspec.yaml`, a `.hidden/` directory and a non-Markdown file are excluded; proposal, design and tasks come first. Cover the error envelope with `status-missing.json`, the absent block with the 1.3.1 fixture, and a store root with a `root.source: declared` variant built from a captured fixture.
- [x] 3.3 List main specs as in design D3, using `list --specs --json` at CLI 1.7.0 or later (`LIST_SPECS_FLOOR`), otherwise the upstream-shaped walk. Verify:
  - from `list-specs.json`: `### billing` and `### identity/user-auth` appear, and a stray `openspec/specs/orphan/spec.md` on disk is absent, which proves the CLI answer was used;
  - with a 1.6.0 version, or a CLI failure: the walk gives `identity/user-auth`, skips a dot-folder and a folder without `spec.md`, and ignores `openspec/specs/spec.md`;
  - requirement summaries still appear under each id.
- [x] 3.4 Call `ProgressManager.checkCanceled()` before each CLI call. Verify: a test whose fake CLI cancels the indicator gets a `ProcessCanceledException` before the second call.

## 4. Background delivery

- [x] 4.1 Add `ExploreContextAction.newBuildTask` and route clipboard, editor-tab and no-provider deliveries through it. Fold the Direct API build into `newExploreTask.run()` before `send`, as in design D5. Make `AiToolDetectionService.detectedTools` volatile. Verify:
  - running the task's `run()` on a pooled thread records the fake CLI being called off the EDT;
  - clipboard delivery happens only in `onSuccess`;
  - a build failure in the Direct API task shows the panel error.
- [x] 4.2 Keep the Explore tab's input guarantee. `showLoading` comes before the task. Every panel path's `onFinished` restores the input, including the no-provider fallback and the missing prompt-service early return. Verify in `ExploreDirectApiPanelTest`: after cancel, after a build failure and after the no-provider notice, the input is enabled.
- [x] 4.3 Update existing Explore tests that expected synchronous delivery, so they drive the task (call `run`, then `onSuccess`/`onFinished`, then pump the event queue). Verify: `./gradlew test --tests '*Explore*'` passes.

## 5. Docs and verification

- [x] 5.1 Update `docs/feature-reference.md`'s Explore section (nested capabilities, any-schema artifacts, the CLI and fallback sources) and add an `## Unreleased` line to `CHANGELOG.md` for users. Verify: `DocumentationHygieneTest` passes.
- [x] 5.2 Mutation-check the tests. Each of these must fail at least one test, then be reverted:
  - sort `artifactPaths`;
  - label with the file name only;
  - always walk;
  - drop the dot-file filter;
  - make the walk non-recursive;
  - skip the containment check.
- [x] 5.3 Run `./gradlew build` and `./gradlew verifyPlugin` (new `com.intellij.*` references). Verify: both pass, and the coverage floor is ratcheted if coverage rose.

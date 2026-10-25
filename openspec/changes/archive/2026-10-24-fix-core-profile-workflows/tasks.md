## 1. Fixtures

- [x] 1.1 Capture from the real 1.15.0 CLI with an isolated `XDG_CONFIG_HOME` and HOME into `src/test/resources/fixtures/cli/1.15.0/config-list/`:
  - `fresh.json`: no config file;
  - `stale-core.json`: `{"profile":"core","workflows":["propose","explore","apply","archive"]}`;
  - `custom-archive-no-sync.json`: `{"profile":"custom","workflows":["propose","apply","archive"]}`.

  For each case, also capture the human `config list` output as `<case>.txt`, and the workflows `init --tools claude` actually installs as `<case>.installed.txt`. The installed list is the oracle: the human output leaves out the `sync` that custom archiving adds. Document the recipe in `fixtures/cli/README.md`.

## 2. Resolver

- [x] 2.1 Add `WorkflowProfileService.resolve(profile, stored, cliVersion)` and `coreWorkflows(version)` per the design. Replace `CORE_DEFAULTS` with the current set, and route `parseSnapshot` and the no-CLI fallback through the resolver, using the detected version. Treat a blank or unparseable version as "current".
- [x] 2.2 Contract tests on the captured fixtures, at version 1.15.0. Each must fail against today's code:
  - `stale-core` resolves to the six core workflows, not the stored four;
  - `fresh` resolves to `core` and the six;
  - `custom-archive-no-sync` resolves to the stored list plus `sync`, before `archive`.

  Check each against the captured installed list.
- [x] 2.3 Version-boundary tests for `resolve`:
  - core at 1.3.1 gives four; at 1.4.0 and 1.5.0, five; at 1.6.0, 1.9.0 and 1.15.0, six;
  - custom `[propose, archive]` at 1.9.0 is unchanged, and at 1.10.0 becomes `[propose, sync, archive]`;
  - custom with `bulk-archive` gets `sync` before it;
  - custom with `sync` already present is unchanged;
  - an empty custom list stays empty;
  - an unknown profile resolves as core;
  - a null version gives the current six.
- [x] 2.4 Update the existing tests that assumed the old behaviour:
  - the CLI-unavailable fallback now includes `update`;
  - `parsesRealConfigListWorkflows_notTheCoreFallback` now proves the core rule;
  - the `defaultStore` fixture test;
  - `OpenSpecSettingsPanelProfileTest`'s comment and expected set.

  Keep every test meaningful: each must still fail when the code it covers is broken.
- [x] 2.5 Mutation-check by hand. Each must make a test fail:
  - trust the stored list for core;
  - drop the 1.10 `sync` rule;
  - apply the `sync` rule below 1.10;
  - an off-by-one at the 1.4.0 or 1.6.0 boundary;
  - the old five-workflow fallback.

## 3. Docs and verify

- [x] 3.1 Update every doc that lists the core workflows (grep `docs/` and `README.md` for "propose, explore, apply" and "5 workflows") to the six, noting that older CLIs install fewer. Add a CHANGELOG `## Unreleased` Fixed entry: the core profile now shows the workflows the CLI actually installs, including Update, even when an old workflow list is left in the config.
- [x] 3.2 Run `./gradlew build` (green) and `openspec validate fix-core-profile-workflows --strict` (clean).

## Workflow follow-up

- Archive the change, then close the tracker entry.

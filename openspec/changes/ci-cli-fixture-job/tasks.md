# Tasks

## 1. Build

- [x] 1.1 `@CliFixtureSuite` (tag `cli-fixtures`) on every plain-JUnit class that reads captured releases (D1).
- [x] 1.2 `test` excludes `cli-fixtures`; `cliFixtureTest` reuses `test`'s setup; `check` depends on both; JaCoCo report and verification read both execution files (D2).
- [x] 1.3 `CliFixtureSuiteGuardTest` (D1).

## 2. CI and hooks

- [x] 2.1 `Build & Verify`: `build`, `cli-fixtures`, `coverage` jobs, each with the gate; analysis upload in `coverage` (D3).
- [x] 2.2 `.githooks/pre-push` runs `test` and `cliFixtureTest`.

## 3. Verification

- [x] 3.1 Merged coverage equals the single-suite figure on the same tree (D4).
- [ ] 3.2 The workflow passes on a manual dispatch of the branch; `build`'s test step is shorter.
- [x] 3.3 Mutation: an untagged fixture class turns the guard red.

## 4. Docs

- [x] 4.1 CONTRIBUTING and CLAUDE.md: the two suites, how to run each, the required checks.

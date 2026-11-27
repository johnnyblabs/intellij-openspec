# Design

## Context

`tasks.test` runs the whole suite in one JVM and finalizes the JaCoCo report; `jacocoTestCoverageVerification` (wired into `check`) reads `build/jacoco/test.exec` against the instrumented classes. The Forgejo `Build & Verify` workflow's `build` job runs `gradle build`, uploads test results, runs analysis upload on `main`, the SBOM step and signing. `cliMatrix` is already a separate `Test` task, excluded from `test` by its `cli-matrix` tag. Forgejo runs a pull request with the base branch's workflow, so new jobs first run on a manual dispatch of the branch or after merge.

## Goals / Non-Goals

**Goals:**
- A faster `build` job, with the same tests and the same coverage floor across CI.
- `./gradlew build` locally unchanged in what it checks.

**Non-Goals:**
- Parallel test forks, Gradle test caching, or reworking `BuiltInAddContractTest`.
- Moving IntelliJ Platform (JUnit 3) tests: they stay in `test`.

## Decisions

### D1. The tag and its guard

A composed annotation, `@CliFixtureSuite`, carries JUnit's `@Tag("cli-fixtures")`. It marks every plain-JUnit (Jupiter) test class that reads captured CLI releases through `FixtureVersions`. `CliFixtureSuiteGuardTest`, which runs in `test`, scans the test sources and fails when such a class lacks the annotation, or when a platform (JUnit 3) class carries it, which the tag can't select.

### D2. `cliFixtureTest` reuses `test`'s setup

`cliFixtureTest` takes `test`'s test classes, classpath (the instrumented classes the platform plugin prepares), JVM argument providers, toolchain, isolated machine folders and JaCoCo agent settings, and includes only the `cli-fixtures` tag. `test` excludes `cli-fixtures` and `cli-matrix`. `check` depends on both. The JaCoCo report and verification read both tasks' execution data, so the local floor is the same suite as before.

### D3. CI jobs

`Build & Verify` gets:
- **build**: `gradle build -x cliFixtureTest -x jacocoTestCoverageVerification`; uploads `test.exec` and test results. Sign, SBOM and the signed artifact stay here.
- **cli-fixtures** (parallel to build): `gradle cliFixtureTest -x jacocoTestReport`; uploads its execution data and test results.
- **coverage** (needs both): downloads both execution files, runs `gradle jacocoTestReport jacocoTestCoverageVerification -x test -x cliFixtureTest`, and on `main` the analysis upload, which moves here from build so it reports merged coverage.

Every job keeps the gate step, so a bypass-only PR reports success in seconds, and the job names are stable required checks.

### D4. Proving the split

Before merge, coverage merged from the two suites must equal the single-suite figure on the same tree (instruction, line, branch), and the workflow must pass on a manual dispatch of the branch.

Measured on 2026-11-27, same tree: one suite covered 64,683 instructions, 6,967 branches and 12,318 lines; the two suites merged covered 64,694, 6,966 and 12,318. Lines and classes are identical; the instruction, branch and method differences (+11, −1, −1 of ~85,000 instructions) are run-to-run variation from background-thread timing, and every ratio is unchanged to three decimals. Test time: `test` 185 s (was 286 s for the whole suite), `cliFixtureTest` 100 s.

**`cliFixtureTest` takes the platform's JVM argument providers but not `test`'s JaCoCo agent provider.** Copying all of them loaded two coverage agents, which aborts the test JVM.

## Risks / Trade-offs

- **A fixture test without the tag runs in `test` only**, which is the old behaviour, not a loss; the guard catches it.
- **Artifact transfer between jobs.** Mitigation: the Forgejo upload-artifact fork already used in this workflow, and its download counterpart; verified on the dispatch run.
- **The coverage job compiles again** (about a minute). Accepted: it runs in parallel with nothing else and replaces analysis time in build.

## Migration Plan

After merge, add `cli-fixtures` and `coverage` to `main`'s required checks.

## Open Questions

None.

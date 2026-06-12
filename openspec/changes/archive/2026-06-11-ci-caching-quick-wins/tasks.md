## 1. Concurrency blocks — build workflows

- [x] 1.1 Add top-level `concurrency` block to `.github/workflows/build.yml`. Insert immediately after the `on:` block and before the `env:` block. Use group `${{ github.workflow }}-${{ github.ref }}` with `cancel-in-progress: true`.
- [x] 1.2 Add the same block to `.forgejo/workflows/build.yaml` (Forgejo-side of the build workflow). Forgejo Actions supports GitHub-compatible `concurrency:` syntax.

## 2. Concurrency block — release workflow

- [x] 2.1 Add top-level `concurrency` block to `.github/workflows/release.yml`. Use group `${{ github.workflow }}` (workflow-scoped, no ref) and `cancel-in-progress: false`. The intent is "serialize all release runs; never cancel" because concurrent `publishPlugin` against the JetBrains Marketplace API could race.

## 3. Action version bumps — Forgejo workflow

- [x] 3.1 In `.forgejo/workflows/build.yaml`, replace `uses: actions/cache@v3` with `@v4`. Two occurrences (build job + verify job).
- [x] 3.2 In the same file, replace `uses: actions/upload-artifact@v3` with `@v4`. Two occurrences (test results upload + plugin artifact upload).
- [x] 3.3 Confirm no other `@v3` action references remain in the Forgejo workflow that should also be bumped. — grep `@v3` returned nothing; all six action references are now `@v4` (checkout × 2, cache × 2, upload-artifact × 2).

## 4. Gradle build cache (no-op — already enabled)

- [x] 4.1 Verify `gradle.properties` contains `org.gradle.caching=true`. — confirmed; shipped in commit `eef8fa2` (2026-03-18). No change needed.

## 5. Verification

- [x] 5.1 `openspec validate ci-caching-quick-wins --strict` — change validates cleanly.
- [x] 5.2 `./gradlew test` — BUILD SUCCESSFUL in 11s; all tests pass.
- [ ] 5.3 Manual verification deferred to first real CI run after merge: push a follow-up commit to a feature branch and confirm the prior run gets cancelled (GitHub Actions UI shows "Cancelled" status). Watch the Forgejo build log for absence of `actions/cache@v3` deprecation warnings.
- [x] 5.4 (bonus) YAML syntax sanity-check via `python3 -c "import yaml; yaml.safe_load(...)"` on all 3 workflow files — all parse cleanly.

## 6. Memory correction at archive

- [ ] 6.1 Update `~/.claude/projects/-Users-johnboyce-working-intellij-openspec/memory/project_ci_caching_optimization.md` to reflect that item 3 (Gradle build cache) was already shipped in commit `eef8fa2` (2026-03-18). Add a line noting the memory was stale on that point, fixed during ci-caching-quick-wins archive on 2026-06-11.

## 7. Spec sync at archive time

- [ ] 7.1 During `/opsx:archive`, sync the delta spec (new "Concurrency cancellation for in-flight runs" requirement with 5 scenarios) into `openspec/specs/ci/spec.md`.
- [ ] 7.2 Close Forgejo #211 (the original tracker) with an archival comment.
- [ ] 7.3 Move Plane OS-222 to Done.

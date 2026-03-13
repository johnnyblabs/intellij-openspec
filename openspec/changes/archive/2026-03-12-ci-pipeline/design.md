## Context

The project uses Forgejo (Gitea fork) at `http://forgejo.geek` with Forgejo Actions (compatible with GitHub Actions syntax). There's an existing `.forgejo/workflows/build.yaml` that runs on push/PR to main. It uses JDK 17 but the project requires JDK 21 (`build.gradle.kts` sets `JavaVersion.VERSION_21`). The `pluginVerification` block is already configured in `build.gradle.kts` with `ides { recommended() }`.

## Goals / Non-Goals

**Goals:**
- CI builds match the actual project requirements (JDK 21)
- Plugin compatibility verified against recommended IDE versions
- Test results visible in CI output
- Faster builds via Gradle caching

**Non-Goals:**
- Adding release automation or Marketplace publishing (v0.3.0 scope)
- Adding code coverage reporting
- Multi-platform testing (Linux-only is sufficient for JVM plugin)

## Decisions

### Single workflow file with two jobs: build + verify

**Build job** runs on every push/PR: compile, test, produce artifact. **Verify job** runs only on main branch pushes: `runPluginVerifier` is slow (downloads multiple IDE versions) and not needed on every PR.

**Why not one job?** Plugin verification can take 5-10 minutes downloading IDEs. Separating it keeps PR feedback fast while still catching compatibility issues before release.

### Gradle caching via setup-gradle action

The `gradle/actions/setup-gradle@v4` action already handles Gradle caching automatically. No additional configuration needed — it caches `~/.gradle/caches` and `~/.gradle/wrapper` between runs.

### Test results via JUnit XML

Gradle already produces JUnit XML in `build/test-results/test/`. We'll keep the existing `build` step and ensure test failures are visible in the workflow summary.

## Risks / Trade-offs

- **Forgejo Actions runner availability** → If the self-hosted runner doesn't have JDK 21, `setup-java` will install it. The `temurin` distribution is widely available.
- **Plugin verifier download size** → First run downloads IDE distributions. Subsequent runs benefit from Gradle caching. The verify job only runs on main to limit this cost.
- **Forgejo Actions compatibility** → Forgejo Actions supports most GitHub Actions syntax. The actions used (`checkout`, `setup-java`, `setup-gradle`, `upload-artifact`) are all compatible.

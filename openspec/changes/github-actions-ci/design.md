## Context

The project has an existing Forgejo Actions CI pipeline (`.forgejo/workflows/build.yaml`) that builds, tests, verifies binary compatibility, signs the plugin, and uploads artifacts. GitHub will be a public mirror where external contributors submit PRs. Both pipelines need to validate code, but only one should be the authoritative release pipeline.

## Goals / Non-Goals

**Goals:**
- Validate external PRs with build + test + Plugin Verifier on GitHub Actions
- Provide a public build status badge
- Optionally sign and produce artifacts on main for GitHub-based releases

**Non-Goals:**
- Replacing Forgejo as the primary CI — Forgejo remains authoritative for Marketplace publishing
- Publishing to JetBrains Marketplace from GitHub Actions
- Running Qodana or other analysis tools on GitHub

## Decisions

### GitHub Actions mirrors Forgejo structure
Use the same two-job structure (`build` + `verify` in parallel) to keep behavior consistent. Differences:
- GitHub uses `actions/setup-java` instead of a custom runner image
- GitHub uses `actions/cache` for Gradle dependencies (same pattern as Forgejo)
- Signing runs on main pushes only (same gate as Forgejo)

### JDK setup via actions/setup-java over container image
GitHub's hosted runners are Ubuntu-based. Using `actions/setup-java@v4` with Temurin 21 is the standard approach — no custom Docker image needed.

### Gradle wrapper over system Gradle
GitHub Actions should use `./gradlew` (Gradle wrapper) instead of a system-installed `gradle` command. This ensures the build uses the exact Gradle version committed to the repo, matching local dev. The Forgejo runner uses system Gradle because the runner image bakes it in, but the wrapper is more portable.

### Signing secrets duplicated to GitHub
The same `PLUGIN_SIGNING_KEY`, `PLUGIN_SIGNING_CERTIFICATE`, and `PLUGIN_SIGNING_KEY_PASSWORD` secrets need to be added to GitHub repo settings. This is a one-time manual step.

## Risks / Trade-offs

- **Risk: CI drift between Forgejo and GitHub** → Mitigate by keeping the same job structure and Gradle commands. Differences are only in runner setup (custom image vs setup-java).
- **Risk: Signing secret management across two platforms** → Accept the duplication. Both platforms encrypt secrets at rest. Rotate together if key changes.
- **Risk: GitHub Actions minutes usage** → Free tier provides 2,000 minutes/month. Build + verify takes ~10-15 minutes per run — well within budget for a small project.

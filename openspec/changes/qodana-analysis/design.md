## Context

Qodana is available as a Docker image (`jetbrains/qodana-jvm`), a CLI tool, or a GitHub/Forgejo Action. The `java-21` runner is a custom Docker image — running Qodana as Docker-in-Docker may not be available. The CLI approach (`qodana scan`) or a dedicated runner with Docker support are alternatives.

## Goals / Non-Goals

**Goals:**
- Run Qodana JVM analysis on every PR to catch new issues
- Establish a baseline so existing issues are tracked but don't block
- Surface findings as CI status (pass/fail)

**Non-Goals:**
- Qodana Cloud integration (keep it local/self-hosted)
- Fixing all existing issues upfront
- Running Qodana locally as part of developer workflow (optional, not required)

## Decisions

### Use Qodana CLI on the runner
**Decision:** Install `qodana` CLI on the `java-21` runner image and run `qodana scan` directly.
**Rationale:** Avoids Docker-in-Docker complexity. The runner image is custom-built anyway — adding the Qodana CLI is a one-time image update. Simpler than managing a separate Docker-capable runner.
**Alternative:** Run Qodana via Docker. Rejected — requires Docker-in-Docker or a separate runner, adds infra complexity.

### Use `qodana-jvm` linter
**Decision:** Use the `qodana-jvm` linter (Java + IntelliJ inspections).
**Rationale:** Matches the project language (Java 21) and includes IntelliJ Platform-specific inspections for plugin development.

### Baseline for existing issues
**Decision:** Generate a baseline on first run and commit it. Only new issues (not in baseline) will fail CI.
**Rationale:** Prevents a wall of existing findings from blocking all PRs. Issues can be fixed incrementally.

### Run on PRs only (not every push)
**Decision:** Run Qodana only on PRs targeting main, not on every push.
**Rationale:** Qodana analysis takes 3-5 minutes. Running on every push to feature branches adds unnecessary load. PR-only gives the right feedback at the right time.

## Risks / Trade-offs

- [Runner image update required] → Need to add Qodana CLI to the `java-21` Docker image. One-time effort.
- [Analysis time] → ~3-5 min per run. Runs in parallel with build/verify so wall-clock impact is minimal.
- [Baseline drift] → Baseline must be regenerated periodically as issues are fixed. Manual step, low effort.

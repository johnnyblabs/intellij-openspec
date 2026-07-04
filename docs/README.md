# Documentation Index

A map of every documentation file in this repository: what it is, who it's for, and how it's maintained. Start here to find the right doc — including which of the two similarly-named "matrix" documents you actually want.

> **Maintenance: Living** — updated as part of every relevant change. When a doc is added, removed, or renamed, update this index in the same change; a doc-hygiene test fails if any doc is missing an entry or lists a non-existent file.

## Maintenance classes

Every tracked documentation file declares one maintenance class near its top, so each doc's update contract is explicit and auditable:

| Class | Contract |
|-------|----------|
| **Living** | Updated as part of every relevant change. Expected to always be current. |
| **Snapshot** | Reviewed on a stated cadence (e.g. per release) and may lag reality between reviews. Carries a "last reviewed" date. |
| **Reference** | Stable; updated only when the thing it describes changes. |
| **Retired** | Kept for history, explicitly not maintained. |

## Documentation tiers — repo markdown is canonical

Repository markdown (the files below) is the **canonical** documentation source. Any published mirror of these docs — a project wiki, a team knowledge base — is a **generated mirror** of the repo markdown and **must not be hand-edited to diverge**. When docs change, they change here first; the mirrors are regenerated, never edited in place.

## Single source of truth for version facts

The [**Version support**](openspec-support.md#version-support) block in `openspec-support.md` is the canonical statement of version facts — the current plugin version and the minimum / baseline / supported OpenSpec CLI versions. Other docs link to that block rather than restating specific version numbers, so the numbers live in exactly one place and can't drift. (Versions intrinsic to a document, such as a CHANGELOG release heading, are exempt.)

## The two "matrix" docs — which is which

- **Version / coverage matrix** → [`openspec-support.md`](openspec-support.md). How the plugin maps to the OpenSpec **client**, by CLI version — what's supported, partial, or planned. This is the parity north-star and the version source of truth.
- **Competitive comparison matrix** → [`feature-comparison-matrix.md`](feature-comparison-matrix.md). How this plugin compares to third-party VS Code **extensions**. A point-in-time competitive survey (Snapshot).

## Docs under `docs/`

| Doc | Purpose | Primary audience | Maintenance |
|-----|---------|------------------|-------------|
| [README.md](README.md) | This documentation index / map | Everyone | Living |
| [openspec-support.md](openspec-support.md) | Plugin ↔ OpenSpec client coverage by CLI version; canonical version-support block | Users, contributors | Living |
| [feature-reference.md](feature-reference.md) | Complete reference for every plugin feature, setting, and troubleshooting note | Users | Living |
| [feature-comparison-matrix.md](feature-comparison-matrix.md) | Competitive comparison vs. VS Code OpenSpec extensions | Evaluators, maintainers | Snapshot |
| [getting-started-browser.md](getting-started-browser.md) | Setup guide: browse specs, no AI setup | Reviewers, leads, PMs | Reference |
| [getting-started-copilot.md](getting-started-copilot.md) | Setup guide: IDE-first developer (Copilot / Cursor / Windsurf / Cline) | IDE-AI developers | Reference |
| [getting-started-cli-companion.md](getting-started-cli-companion.md) | Setup guide: terminal AI companion (Claude Code, Gemini CLI, …) | CLI-AI developers | Reference |
| [getting-started-api.md](getting-started-api.md) | Setup guide: standalone API-key workflow | API-key developers | Reference |
| [marketplace-page.md](marketplace-page.md) | Source copy for the JetBrains Marketplace listing | Maintainers | Reference |

## Per-CLI-version analyses

Deep, cited analyses of what each OpenSpec **client** version introduced, modified, deprecated, or removed — and how the plugin responds — live under [`cli-versions/`](cli-versions/README.md). They ground the plugin's version-support decisions (the epistemic base behind the coverage matrix above), one document per CLI version, produced via an OpenSpec explore over upstream docs. Start at the [cli-versions index](cli-versions/README.md).

## Testing layers

Three complementary layers, cheapest first; each exists because the one above it structurally cannot see what it covers:

1. **Headless platform tests** (`src/test/java`, every PR via `gradle build`) — behavior against the real IntelliJ platform without rendering: parsers (contract-tested against captured CLI output), services, notification flows, tree models, validators. This is the primary automated coverage and the JaCoCo floor's domain.
2. **Manual test-drive** (the `lifecycle-testdrive` project skill) — a seeded sandbox IDE (`runIde`) plus an in-project walkthrough checklist, for *judgment* checks: wording, layout, feel. Seeding source: `scripts/seed-lifecycle-demo.sh`.
3. **UI smoke journeys** (`src/integrationTest/kotlin`, `gradle uiSmoke`) — a real IDE booted by the Starter framework with the built plugin installed, driven via the Driver SDK; asserts *presence and wiring* of rendered surfaces. Five journeys: tool window open-and-render, Update legacy-cleanup notice, Settings schemas section, editor validator-parity diagnostics (via the Problems view), and the archive incomplete-change guard (assert-and-cancel; nothing mutating ever runs). Policy: manual dispatch + release-tag gating only — **never a per-PR blocker**. Shares the seeding source with layer 2. Failure artifacts land under `out/perf-startup/`.

## Repository-root docs

| Doc | Purpose | Primary audience | Maintenance |
|-----|---------|------------------|-------------|
| [../README.md](../README.md) | Project overview, personas, installation | Everyone | Living |
| [../CHANGELOG.md](../CHANGELOG.md) | User-facing release history | Users | Living |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | Development setup and contribution workflow | Contributors | Reference |

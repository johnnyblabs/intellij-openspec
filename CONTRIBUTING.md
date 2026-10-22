# Contributing to OpenSpec IntelliJ Plugin

Thanks for your interest in contributing! This guide covers everything you need to get started.

> **Maintenance: Reference** — stable; updated only when the contribution workflow changes (see the [documentation index](docs/README.md)).

## Development Setup

### Prerequisites

- **Java 21** (JDK) — [Adoptium Temurin](https://adoptium.net/) recommended
- **IntelliJ IDEA 2024.2+** — Community or Ultimate
- **Gradle 9** — included via wrapper, no separate install needed

### Build and Run

```bash
git clone https://github.com/johnnyblabs/intellij-openspec.git
cd intellij-openspec
./gradlew build        # Compile and run tests
./gradlew runIde       # Launch sandboxed IDE with the plugin
./gradlew test         # Run tests only
```

Or use the Makefile shortcuts: `make build`, `make test`, `make install`.

### Git hooks (do this once per clone)

Activate the repo's hooks — they are **off by default** until you point Git at them:

```bash
git config core.hooksPath .githooks
```

Once active, the hook runs before any push that touches `src/`: it runs the test suite, runs the Plugin Verifier when a change adds a platform API reference, and scans the outgoing push for secrets and internal identifiers. Bypass it in an emergency with `git push --no-verify` (or skip only the verifier with `SKIP_VERIFY_PLUGIN=1 git push`).

A `commit-msg` hook also checks each commit message for internal identifiers, because commit messages are published with the repository history. Bypass it with `git commit --no-verify`.

### Project Structure

```
src/main/java/com/johnnyblabs/openspec/
├── actions/          # IntelliJ actions (menu items, toolbar)
├── ai/               # AI provider integration (Claude, OpenAI, Gemini)
├── coordination/     # OpenSpec coordination model (1.4 workspaces/initiatives; 1.5 stores/worksets)
├── dialogs/          # UI dialogs
├── editor/           # Editor integration (inspections, annotators, line markers)
├── filetype/         # OpenSpec file-type registration
├── model/            # Data models
├── scaffolding/      # Artifact/project scaffolding + content detection
├── services/         # Project services (core logic)
├── settings/         # Plugin settings
├── statusbar/        # Status bar widgets (CLI + AI tools)
├── toolwindow/       # Tool window UI (tree, panels)
├── util/             # Utilities
├── validation/       # Built-in validation
└── version/          # CLI/config version-support model
```

The `openspec/` directory contains the project's own specs and change history — this plugin uses the [OpenSpec](https://github.com/fission-ai/openspec) framework for its own development.

## Making Changes

### Branch Naming

Use `change/<description>` for feature branches:

```
change/fix-tree-refresh
change/add-kotlin-support
change/update-marketplace-docs
```

### Coding Standards

- **Java 21** — use modern language features where they improve readability
- **Services** — register as IntelliJ project services via `plugin.xml`
- **Settings** — use `PersistentStateComponent` with `@State` annotation
- **Credentials** — store in IntelliJ `PasswordSafe`, never in files
- **Tests** — JUnit 5, use `@ExtendWith(MockitoExtension.class)` for mocking

### Commit Messages

Write concise commit messages that explain *why*, not just *what*:

```
Fix tree refresh after archive — VFS listener was missing recursive flag

Honor Default schema setting in built-in init — was hardcoding spec-driven
```

### Testing

- **JUnit 5**, with `@ExtendWith(MockitoExtension.class)` for mocking. Every change needs tests, and each test must fail if the code it covers regresses.
- **Contract-test parsers against captured real output, never hand-written shapes.** Any code that parses output from an external tool (the OpenSpec CLI's `--json`, on-disk registry/YAML formats) is tested against fixtures captured from the real tool, sanitized (machine-specific paths rewritten to `/fixture/...`), and version-namespaced under `src/test/resources/fixtures/cli/<version>/`. A hand-authored shape encodes your assumption, so the test passes while the parser is wrong. When the tool's output format changes, re-capture the fixture and fix the failures. See `CoordinationContractTest`, `StoreWorksetContractTest`, and `util/CliContractTest`.
- **Cross-platform fixtures and gating.** The store/workset surface is verified across Windows, macOS, and Linux:
  - *Data-dir resolution* is tested host-independently through the parameterized resolver (`CoordinationPathsCrossPlatformTest`) — the Windows `%LOCALAPPDATA%\openspec` branch is exercised from any host via the `windows=true` override, with the native backslash-separator guarantee asserted in an `@EnabledOnOs(WINDOWS)` case.
  - *CRLF-vs-LF parse parity* (`CrlfLfParseParityTest`) feeds each captured fixture as LF, as `\r\n`, and with a trailing lone `\r`, asserting an identical model — host-independent.
  - *Native path round-tripping* (`NativePathRoundTripTest`, fixture `store-list-native-paths.json`) asserts spaced, Windows-drive backslash, and UNC roots survive parsing unchanged; assertions that resolve those strings through `Path` are OS-gated to where the form is valid.
  - *OS-gated integration* — the `.cmd` shim invocation (`WindowsCmdShimInvocationTest`, `@EnabledOnOs(WINDOWS)`) and 8.3 short-path canonicalization run on the GitHub Windows matrix leg; symlink canonicalization runs on macOS/Linux. These are **skipped**, not failed, off their target OS, so `./gradlew build` stays green everywhere.
  - *Windows data-dir capture (follow-up).* Confirming the Windows branch against a real `store register --json` `registry.path` requires a Windows host; it's a documented manual follow-up (the branch is asserted against the documented `%LOCALAPPDATA%\openspec` layout via the resolver override in the meantime).

### Version-support fidelity

The plugin gates behavior on the detected OpenSpec CLI version (e.g. coordination reads/writes exist only in the `[1.4.0, 1.5.0)` window; the store/workset model leads at `>= 1.5.0`). This gating is a contract, not an implementation detail — a capability that silently appears, disappears, or changes which CLI line it targets is a regression even when the build is green. (This rule exists because the 1.4 coordination write actions shipped in a release, were silently dropped in a later rebuild with no spec delta or CHANGELOG note, and shipped broken until restored.)

Therefore, parallel to documentation fidelity: **any change that touches CLI-version-gated behavior MUST, in the same change, update the per-version behavior contract and its per-version tests.**

- The contract lives in the `coordination-surfaces` spec (*CLI-version behavior contract* requirement) and is mirrored, user-facing, in `docs/openspec-support.md` (the single source of truth for per-version behavior).
- The enforcing tests are the per-version matrix in `CoordinationServiceWindowTest` — assert, for each supported line (1.3.x / 1.4.x / 1.5.x), exactly which read surface, tier, and write path is enabled, so that adding, removing, or re-gating a version-gated capability fails the build. No vacuous asserts: each row must fail if that version's behavior regresses.

### CLI version targeting & local development

The plugin supports a **range** of OpenSpec CLI versions, but your machine has **one** installed. Keep these two axes straight — they are bridged by captured fixtures, which is what lets the build stay version-agnostic:

- **Declared support is a range, carried in-repo by fixtures.** The floor is CLI 1.3.0 and there is no ceiling. Support for a generation is *proven*, not asserted, by a committed corpus of real CLI output under `src/test/resources/fixtures/cli/<version>/` plus per-generation contract tests. Because the tests parse those captured fixtures rather than shelling out to a CLI, `./gradlew build` needs **no** OpenSpec CLI on `PATH` and produces the same result on any machine.
- **The installed CLI is one version.** It matters only for things that actually shell out: the manual test-drive, the UI-smoke journeys, and re-capturing fixtures.
- **The top of the supported range is single-sourced.** `openspecTargetVersion` in `gradle.properties` is the sole declaration of the top-supported CLI version. The UI-smoke workflow installs exactly that version (it reads the property; it never hardcodes a pin), and `TargetVersionSingleSourceTest` fails the build if the property drifts from its captured fixtures or if the workflow reintroduces a literal pin. The durable *floor* — the older generations whose corpora must be retained — is a separate, hand-maintained list in `ValidatorVerdictVersionStabilityTest.FLOOR` and `CliVersionAtLeastTest`; it is deliberately **not** derived from `openspecTargetVersion` (a floor that derived itself from the target could never catch the target being wrong).

Local-development disciplines:

- **Capture fixtures in an isolated sandbox.** When you re-capture CLI output, run the CLI with an isolated `HOME` / `XDG_*` (and telemetry off) so your real global OpenSpec state is untouched, then **sanitize machine-specific paths** (rewrite absolute paths to `/fixture/...`) before committing under `src/test/resources/fixtures/cli/<version>/`.
- **Drive an off-target generation without downgrading your global CLI.** To reproduce behavior against an older or newer line, use `npx --yes @fission-ai/openspec@<version> <args>` rather than reinstalling the global — your everyday CLI stays put.
- **`openspec update` is adopt-sync only.** It rewrites the tracked skills and commands to match your *installed* CLI, so run it deliberately (as part of adopting a new CLI generation), not as a routine step — and never to "target" a version other than the one you have installed.

**Bumping the top-supported CLI version** (a deliberate, ordered checklist — each step gates the next):

1. Install the new CLI generation locally and capture its fixtures **first**: `src/test/resources/fixtures/cli/<new>/version.txt`, `validate-parity-corpus.json`, and `validate-parity-corpus-strict.json` (sanitized, from an isolated sandbox as above).
2. Bump `openspecTargetVersion` in `gradle.properties` to `<new>`.
3. Append `<new>` to `ValidatorVerdictVersionStabilityTest.FLOOR` and to `CliVersionAtLeastTest`'s supported-versions value list (these are additive — do not remove older entries).
4. Run `./gradlew build`. `TargetVersionSingleSourceTest` will red if step 1 was skipped (no corpus for the target) — that is the guard working.

## Pull Requests

1. Fork the repo and create a branch from `main`
2. Make your changes with tests
3. Run `./gradlew build` to verify everything passes
4. Open a PR against `main`

### PR Checklist

- [ ] Code compiles and tests pass (`./gradlew build`)
- [ ] New features have tests
- [ ] No hardcoded credentials or internal URLs
- [ ] Plugin runs correctly (`./gradlew runIde`)

## Releasing

### Release Flow

1. Merge your PR to `main`
2. Tag the release: `git tag v0.2.4 && git push origin v0.2.4`
3. GitHub Actions automatically: builds, tests, signs, publishes to JetBrains Marketplace, and creates a GitHub Release

### Required GitHub Secrets

| Secret | Purpose |
|--------|---------|
| `PLUGIN_SIGNING_KEY` | Base64-encoded private key PEM |
| `PLUGIN_SIGNING_CERTIFICATE` | Base64-encoded certificate PEM |
| `PLUGIN_SIGNING_KEY_PASSWORD` | Passphrase for the signing key |
| `JETBRAINS_MARKETPLACE_TOKEN` | API token from [JetBrains Marketplace](https://plugins.jetbrains.com/author/me/tokens) |

Signing secrets are generated by `scripts/setup-signing.sh`, which sets them on both Forgejo and GitHub.

### Version Bump

Update the version in `build.gradle.kts` before tagging:

```kotlin
version = "X.Y.Z"
```

The version in `build.gradle.kts` is the single source of truth — it flows into `plugin.xml`, the signed ZIP filename, and the marketplace listing.

## Reporting Issues

- **Bugs** — use the [bug report template](https://github.com/johnnyblabs/intellij-openspec/issues/new?template=bug_report.md)
- **Feature requests** — use the [feature request template](https://github.com/johnnyblabs/intellij-openspec/issues/new?template=feature_request.md)
- **Security vulnerabilities** — see [SECURITY.md](SECURITY.md) (do not file public issues)

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

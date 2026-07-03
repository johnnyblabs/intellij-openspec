## Why

The plugin's CI runs on Ubuntu only. Every job in the build workflow targets a single Linux runner, so every Windows- and macOS-specific code path ships unverified. The store/workset surface planned for the 1.5.0 window (`add-store-workset-read-surface` and `add-store-workset-write-actions`) leans directly on paths that differ per platform:

- CLI invocation on Windows goes through a `.cmd` shim, not a bare executable — `CliDetectionService` already carries Windows-only fallback logic (`.cmd`/`.bat`/`.exe` suffixes, `where.exe`, `%APPDATA%`/`%LOCALAPPDATA%` common paths) that no CI job exercises.
- The global data dir resolves to `%LOCALAPPDATA%\openspec` on Windows with backslash separators; `CoordinationPaths` has a Windows branch that is only unit-tested with a forced `windows=true` flag on a Linux host, never against a real Windows filesystem.
- Store roots and registry entries carry native paths — Windows drive/UNC backslash paths (`C:\...`, `\\host\share\...`), paths with spaces, and paths routed through `cmd.exe`. The store parsers must round-trip these unchanged.
- OpenSpec 1.5.0 canonicalizes roots (resolving Windows 8.3 short-paths and symlinks) before matching. The plugin performs no `toRealPath()` canonicalization, so once a store-per-project feature lands, the plugin and the CLI can disagree on the resolved root of the same directory — a class of bug a Linux-only matrix cannot see.
- The exact Windows data-dir layout is unconfirmed. `CoordinationPaths` assumes `%LOCALAPPDATA%\openspec`, but the CLI's path library may instead append a `\Data` segment or key off `%APPDATA%`. This has never been checked against a real Windows capture of `store register --json`.

This is the cross-platform safety net the store work needs. It adds no product behavior; it adds coverage and a real-OS capture so the store surface can rely on Windows and macOS being verified rather than assumed.

## What Changes

- **Add a Windows + macOS CI matrix — on the public mirror's CI only.** The matrix job lives in the public mirror's build workflow, not the self-hosted origin CI. The self-hosted runner has no Windows host and rejects newer action versions, so it stays Linux-only; the cross-platform matrix runs where Windows and macOS hosts are available. All matrix workflow YAML stays vendor-neutral — no internal hostnames, identifiers, or URLs.
- **Add host-independent cross-platform test cases**, using real-OS gating (`@EnabledOnOs` / `assumeTrue(SystemInfo.isWindows)`) only where an actual OS filesystem or process is required; everything expressible with the parameterized resolvers stays host-independent:
  1. `CoordinationPaths` resolution table — `windows=true` + `LOCALAPPDATA` resolves to `<LOCALAPPDATA>\openspec` with backslash separators; `XDG_DATA_HOME` takes precedence even when `windows=true`; null-home falls back correctly; and the new `stores/` and `worksets/` directories resolve under the data dir.
  2. CRLF-vs-LF parity — each captured store/workset JSON fixture is fed to the parser as LF and again as `\r\n` (plus a trailing-`\r` variant); the resulting models must be identical.
  3. Spaced and backslash paths survive parsing — store roots like `C:\Program Files\...` and UNC `\\host\share\...` and paths containing spaces round-trip through the parsers unchanged; `Path`-resolving assertions are OS-gated.
  4. Root canonicalization parity — a symlink to a store root is created (`Files.createSymbolicLink`, exercised on macOS/Linux CI) and the plugin must match it to the canonical registered root; the 8.3 short-path variant is OS-gated to Windows.
  5. Windows `.cmd` shim invocation from a spaced path — an integration test on the mirror's Windows job invokes a store/context command through the `.cmd` shim and asserts exit 0 with parseable output.
- **Capture the real Windows data-dir path** — run `store register --json` on a real Windows host, read the `registry.path`, and confirm or correct the Windows branch of `CoordinationPaths`. The captured, sanitized output is committed as a contract fixture.

> The store models, parsers, and UI themselves are defined by `add-store-workset-read-surface` and `add-store-workset-write-actions`; this change treats them as the code under test and does not add or alter them. The CRLF corruption *source* fix (YAML frontmatter `\r` escaping) shipped upstream and in the plugin's 0.3.1 line; this change only adds cross-platform *test coverage* for the parsers — it does not re-fix parsing.

## Capabilities

### New Capabilities
- `cross-platform-verification`: a Windows + macOS CI matrix on the public mirror plus the cross-platform test cases and real-OS captures that verify the store/workset surface's platform-specific paths — data-dir resolution with backslash separators, CRLF/LF parse parity, spaced and UNC path round-tripping, root canonicalization (symlink and 8.3 short-path), and `.cmd`-shim invocation.

### Modified Capabilities
<!-- None. This change adds verification for the store surface (`store-workset-surface`); it does not modify that capability's requirements. -->

## Impact

- **CI:** a new matrix job (Windows + macOS) added to the public mirror's build workflow only. The self-hosted origin CI is unchanged and stays Linux-only. All matrix YAML is vendor-neutral.
- **Tests:** new cross-platform cases under the coordination and CLI-detection test packages — host-independent where the parameterized resolvers allow, OS-gated (`@EnabledOnOs` / `assumeTrue`) only where a real filesystem or process is required. No product code changes except a possible correction to the Windows branch of `CoordinationPaths` if the real capture disproves the current assumption.
- **Fixtures:** captured, sanitized real CLI output (including a real Windows `store register --json`) committed under `src/test/resources/fixtures/cli/`, version-namespaced for 1.5.0, per the contract-test discipline.
- **CLI contract:** relies on the 1.5.0 `store` command surface (`store register --json`, `store list --json`, `store doctor`) for the captures; no new command dependency beyond what the store surface already introduces.
- **Platform compatibility:** unchanged — the plugin continues to support the same IDE baseline; this change verifies existing behavior rather than extending it.
- **Docs:** README/CHANGELOG note that CI now verifies Windows and macOS; the contract-test guidance is extended to cover the cross-platform fixtures.
- **Tracker:** the linked issue.

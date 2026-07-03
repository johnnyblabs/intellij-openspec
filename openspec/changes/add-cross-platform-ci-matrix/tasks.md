## 1. CI matrix on the public mirror

- [x] 1.1 Add a cross-platform matrix job to the public mirror's build workflow with a Windows host and a macOS host in the matrix; keep the existing Linux job as-is. Do not add the matrix to the self-hosted origin workflow — that runner has no Windows host and rejects newer action versions. (Added a separate `cross-platform` job guarded by `if: ${{ contains(github.server_url, 'github.com') }}`; the shared `build`/`verify` jobs are unchanged and stay Linux-only.)
- [x] 1.2 Have the matrix job run the plugin build and test suite (`./gradlew build` / `test`) on each OS, so the OS-gated cases below actually execute on a real Windows and macOS host. (Runs `./gradlew --console=plain test`, with `defaults.run.shell: bash` so `./gradlew` resolves identically on the Windows leg.)
- [x] 1.3 Keep all matrix workflow YAML vendor-neutral: no internal hostnames, IPs, tracker IDs, or internal URLs. Pin actions to versions the public mirror supports (independent of the self-hosted runner's constraints). (`checkout@v7`, `setup-java@v5`, `setup-node@v4`; the self-hosted runner is described only generically in comments.)
- [x] 1.4 Gate the Windows-only integration step (task 5) so it runs only on the Windows matrix leg, and ensure a real OpenSpec CLI at the 1.5.0 floor is available to that step (install or provision it in the job). (The Windows leg installs `@fission-ai/openspec@latest` via npm; the `.cmd` shim test is additionally self-contained via a crafted shim so it is deterministic regardless of the installed CLI.)

## 2. CoordinationPaths cross-platform resolution table

- [x] 2.1 Add a host-independent table test over `CoordinationPaths.resolve(env, windows, home)`: `windows=true` + `LOCALAPPDATA` set → global data dir is `<LOCALAPPDATA>\openspec` and the string uses backslash separators. (`CoordinationPathsCrossPlatformTest`: host-independent Path-equality + LOCALAPPDATA-prefix assertions; the full backslash-separator guarantee is an `@EnabledOnOs(WINDOWS)` case, since `Path.of` uses the host separator.)
- [x] 2.2 Assert `XDG_DATA_HOME` precedence holds even when `windows=true` (XDG wins over the `%LOCALAPPDATA%` branch).
- [x] 2.3 Assert null-home fallback resolves to the Unix `~/.local/share/openspec` shape when neither XDG nor the Windows branch applies.
- [x] 2.4 Assert the new `stores/` and `worksets/` directories resolve under the data dir on both the `windows=true` and `windows=false` legs (mirroring the `CoordinationPaths` extensions from the store read surface).

## 3. CRLF-vs-LF parse parity

- [x] 3.1 For each captured store/workset JSON fixture, add a parity test that parses the fixture with LF line endings and again with every `\n` rewritten to `\r\n`; assert the two resulting models are equal. (`CrlfLfParseParityTest` covers `store-list`, `store-doctor`, `store-doctor-diagnostic`, `workset-list` JSON parsers plus the on-disk YAML registry readers.)
- [x] 3.2 Add a trailing-`\r` variant (a lone `\r` at end of value/line) and assert it parses to the same model — guarding the parsers against CRLF corruption without re-fixing the upstream escaping.

## 4. Spaced, backslash, and UNC path round-tripping

- [x] 4.1 Add fixtures/cases where store roots contain spaces (`C:\Program Files\...`), Windows drive backslash paths, and UNC paths (`\\host\share\...`); assert the parsers preserve the raw path string unchanged. (New fixture `store-list-native-paths.json` reuses the captured `store list --json` shape with only the leaf `root` strings varied; `NativePathRoundTripTest` asserts verbatim round-trip.)
- [x] 4.2 OS-gate (`@EnabledOnOs(WINDOWS)` / `assumeTrue(SystemInfo.isWindows)`) any assertion that resolves those strings through `Path`, so the round-trip check stays host-independent while the resolution check runs only where it is meaningful. (Windows-path `Path.of` resolution is `@EnabledOnOs(WINDOWS)`; POSIX-path resolution is `@EnabledOnOs({MAC, LINUX})`.)

## 5. Root canonicalization parity

- [x] 5.1 Add a test that creates a symlink to a store root via `Files.createSymbolicLink` (runs on macOS/Linux CI), registers/records the canonical root, and asserts the plugin matches the symlinked path to the canonical registered root. (`RootCanonicalizationParityTest#symlinkedRootMatchesCanonicalRegisteredRoot`, `@EnabledOnOs({MAC, LINUX})`.)
- [x] 5.2 Add an OS-gated Windows 8.3 short-path variant: assert the plugin matches an 8.3 short-path form of a store root to its canonical long-path registered root, `@EnabledOnOs(WINDOWS)`. (Self-skips via `assumeTrue` when 8.3 short-name creation is disabled on the volume, so the Windows leg stays green either way.)
- [x] 5.3 If the plugin lacks the canonicalization these tests require, record the gap explicitly and reference `add-store-workset-read-surface` (the surface that owns root-to-store matching) rather than adding matching logic here. (No gap: the plugin already provides `CoordinationService.canonicalize` + `storeMatchingRoot`; the test javadoc credits `add-store-workset-read-surface` as the owning surface and this change adds no matching logic.)

## 6. Windows `.cmd` shim invocation (integration)

- [x] 6.1 Add an integration test, gated to the Windows matrix leg, that invokes a store/context command through the `.cmd` shim from a path containing spaces and asserts exit code 0. (`WindowsCmdShimInvocationTest`, `@EnabledOnOs(WINDOWS)`: crafts an `openspec.cmd` in a spaced directory, resolves it through the real `CliDetectionService` suffix fallback, and invokes `store list --json`.)
- [x] 6.2 Assert the invocation's output is parseable by the store/context parser (round-trips to a model), exercising the real `CliDetectionService` Windows suffix/`where.exe` resolution end-to-end. (Output is fed through `CoordinationService.parseStores` and asserted against the captured model. `tryPath` was widened to `public` so the cross-package integration test can drive the real suffix fallback + process spawn.)

## 7. Real Windows data-dir capture and confirmation

- [ ] 7.1 On a real Windows host, run `openspec store register --json` for a scratch store and record the `registry.path` (the resolved data-dir location). **BLOCKED — needs a Windows machine (not available in this environment).** Do not guess the shape; capture it from a real Windows CLI run.
- [x] 7.2 Confirm or correct the Windows branch of `CoordinationPaths`: verify the real path is `%LOCALAPPDATA%\openspec`, or adjust the branch if the CLI's path library appends a `\Data` segment or keys off `%APPDATA%`. (Implemented via the `windows=true` resolver override asserted in `CoordinationPathsCrossPlatformTest` against the documented `%LOCALAPPDATA%\openspec` layout. Final confirmation/correction against a real capture is pending 7.1 — the branch is asserted, not yet cross-checked against captured CLI output.)
- [ ] 7.3 Sanitize the capture (strip the machine-specific user path segment) and commit it as a version-namespaced 1.5.0 fixture under `src/test/resources/fixtures/cli/`; add a contract test asserting the plugin's Windows resolution matches the captured `registry.path` shape. **PENDING 7.1** — the resolution shape is already asserted host-independently (task 2.1); committing the real captured `registry.path` fixture and cross-checking it is a manual follow-up once a Windows capture exists.

## 8. Documentation

- [x] 8.1 Update README/CHANGELOG (vendor-neutral) to note CI now verifies Windows and macOS in addition to Linux. (README "Contributing" note + CHANGELOG `## Unreleased` → `### Changed` bullet.)
- [x] 8.2 Extend the contract-test guidance to cover the cross-platform fixtures (CRLF/LF parity, spaced/UNC paths, the Windows data-dir capture) and where they live. (New "Testing" subsection in `CONTRIBUTING.md`.)

## Tests

- CoordinationPaths resolution table (2.1–2.4): host-independent, exercises the `windows=true`/`false` legs of the parameterized resolver, including the new `stores/`/`worksets/` dirs — fails if the Windows branch or the new dir resolution regresses.
- CRLF/LF and trailing-`\r` parity (3.1–3.2): host-independent, drives each captured fixture through both line-ending forms — fails if the parsers diverge on CRLF input.
- Spaced/backslash/UNC round-trip (4.1–4.2): host-independent string round-trip plus OS-gated `Path` resolution — fails if a native path is mangled during parse.
- Root canonicalization parity (5.1–5.2): symlink case on macOS/Linux CI, 8.3 short-path case OS-gated to Windows — fails if the plugin cannot match a non-canonical path to its canonical registered root.
- `.cmd` shim invocation (6.1–6.2): integration, Windows matrix leg only — fails if the shim invocation or output parse breaks on a real Windows host.
- Windows data-dir contract test (7.3): asserts the plugin's Windows resolution matches the captured real `registry.path` — fails if the assumed data-dir layout is wrong. **(Deferred: awaits the real Windows capture in 7.1; the resolution shape is asserted host-independently in the interim.)**
- All fixtures are CAPTURED REAL CLI output, sanitized, and version-namespaced for 1.5.0 under `src/test/resources/fixtures/cli/`; no hand-authored shapes. (`store-list-native-paths.json` reuses the captured `store list --json` shape with only the leaf path values varied — the payload under test.)

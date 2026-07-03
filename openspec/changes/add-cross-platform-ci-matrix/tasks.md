## 1. CI matrix on the public mirror

- [ ] 1.1 Add a cross-platform matrix job to the public mirror's build workflow with a Windows host and a macOS host in the matrix; keep the existing Linux job as-is. Do not add the matrix to the self-hosted origin workflow — that runner has no Windows host and rejects newer action versions.
- [ ] 1.2 Have the matrix job run the plugin build and test suite (`./gradlew build` / `test`) on each OS, so the OS-gated cases below actually execute on a real Windows and macOS host.
- [ ] 1.3 Keep all matrix workflow YAML vendor-neutral: no internal hostnames, IPs, tracker IDs, or internal URLs. Pin actions to versions the public mirror supports (independent of the self-hosted runner's constraints).
- [ ] 1.4 Gate the Windows-only integration step (task 5) so it runs only on the Windows matrix leg, and ensure a real OpenSpec CLI at the 1.5.0 floor is available to that step (install or provision it in the job).

## 2. CoordinationPaths cross-platform resolution table

- [ ] 2.1 Add a host-independent table test over `CoordinationPaths.resolve(env, windows, home)`: `windows=true` + `LOCALAPPDATA` set → global data dir is `<LOCALAPPDATA>\openspec` and the string uses backslash separators.
- [ ] 2.2 Assert `XDG_DATA_HOME` precedence holds even when `windows=true` (XDG wins over the `%LOCALAPPDATA%` branch).
- [ ] 2.3 Assert null-home fallback resolves to the Unix `~/.local/share/openspec` shape when neither XDG nor the Windows branch applies.
- [ ] 2.4 Assert the new `stores/` and `worksets/` directories resolve under the data dir on both the `windows=true` and `windows=false` legs (mirroring the `CoordinationPaths` extensions from the store read surface).

## 3. CRLF-vs-LF parse parity

- [ ] 3.1 For each captured store/workset JSON fixture, add a parity test that parses the fixture with LF line endings and again with every `\n` rewritten to `\r\n`; assert the two resulting models are equal.
- [ ] 3.2 Add a trailing-`\r` variant (a lone `\r` at end of value/line) and assert it parses to the same model — guarding the parsers against CRLF corruption without re-fixing the upstream escaping.

## 4. Spaced, backslash, and UNC path round-tripping

- [ ] 4.1 Add fixtures/cases where store roots contain spaces (`C:\Program Files\...`), Windows drive backslash paths, and UNC paths (`\\host\share\...`); assert the parsers preserve the raw path string unchanged.
- [ ] 4.2 OS-gate (`@EnabledOnOs(WINDOWS)` / `assumeTrue(SystemInfo.isWindows)`) any assertion that resolves those strings through `Path`, so the round-trip check stays host-independent while the resolution check runs only where it is meaningful.

## 5. Root canonicalization parity

- [ ] 5.1 Add a test that creates a symlink to a store root via `Files.createSymbolicLink` (runs on macOS/Linux CI), registers/records the canonical root, and asserts the plugin matches the symlinked path to the canonical registered root.
- [ ] 5.2 Add an OS-gated Windows 8.3 short-path variant: assert the plugin matches an 8.3 short-path form of a store root to its canonical long-path registered root, `@EnabledOnOs(WINDOWS)`.
- [ ] 5.3 If the plugin lacks the canonicalization these tests require, record the gap explicitly and reference `add-store-workset-read-surface` (the surface that owns root-to-store matching) rather than adding matching logic here.

## 6. Windows `.cmd` shim invocation (integration)

- [ ] 6.1 Add an integration test, gated to the Windows matrix leg, that invokes a store/context command through the `.cmd` shim from a path containing spaces and asserts exit code 0.
- [ ] 6.2 Assert the invocation's output is parseable by the store/context parser (round-trips to a model), exercising the real `CliDetectionService` Windows suffix/`where.exe` resolution end-to-end.

## 7. Real Windows data-dir capture and confirmation

- [ ] 7.1 On a real Windows host, run `openspec store register --json` for a scratch store and record the `registry.path` (the resolved data-dir location).
- [ ] 7.2 Confirm or correct the Windows branch of `CoordinationPaths`: verify the real path is `%LOCALAPPDATA%\openspec`, or adjust the branch if the CLI's path library appends a `\Data` segment or keys off `%APPDATA%`.
- [ ] 7.3 Sanitize the capture (strip the machine-specific user path segment) and commit it as a version-namespaced 1.5.0 fixture under `src/test/resources/fixtures/cli/`; add a contract test asserting the plugin's Windows resolution matches the captured `registry.path` shape.

## 8. Documentation

- [ ] 8.1 Update README/CHANGELOG (vendor-neutral) to note CI now verifies Windows and macOS in addition to Linux.
- [ ] 8.2 Extend the contract-test guidance to cover the cross-platform fixtures (CRLF/LF parity, spaced/UNC paths, the Windows data-dir capture) and where they live.

## Tests

- CoordinationPaths resolution table (2.1–2.4): host-independent, exercises the `windows=true`/`false` legs of the parameterized resolver, including the new `stores/`/`worksets/` dirs — fails if the Windows branch or the new dir resolution regresses.
- CRLF/LF and trailing-`\r` parity (3.1–3.2): host-independent, drives each captured fixture through both line-ending forms — fails if the parsers diverge on CRLF input.
- Spaced/backslash/UNC round-trip (4.1–4.2): host-independent string round-trip plus OS-gated `Path` resolution — fails if a native path is mangled during parse.
- Root canonicalization parity (5.1–5.2): symlink case on macOS/Linux CI, 8.3 short-path case OS-gated to Windows — fails if the plugin cannot match a non-canonical path to its canonical registered root.
- `.cmd` shim invocation (6.1–6.2): integration, Windows matrix leg only — fails if the shim invocation or output parse breaks on a real Windows host.
- Windows data-dir contract test (7.3): asserts the plugin's Windows resolution matches the captured real `registry.path` — fails if the assumed data-dir layout is wrong.
- All fixtures are CAPTURED REAL CLI output, sanitized, and version-namespaced for 1.5.0 under `src/test/resources/fixtures/cli/`; no hand-authored shapes.

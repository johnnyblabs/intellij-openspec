# cross-platform-verification Specification

## Purpose
TBD - created by archiving change add-cross-platform-ci-matrix. Update Purpose after archive.

## Requirements
### Requirement: Cross-platform CI matrix on the public mirror

The project SHALL verify the plugin on Windows and macOS in addition to Linux by running the build and test suite in a CI matrix. The matrix SHALL live in the public mirror's CI workflow, not the self-hosted origin CI, because the self-hosted runner has no Windows host and rejects newer action versions. All matrix workflow configuration SHALL be vendor-neutral, containing no internal hostnames, addresses, tracker identifiers, or internal URLs.

#### Scenario: Matrix runs the suite on Windows and macOS

- **WHEN** the public mirror's CI workflow runs
- **THEN** the plugin's build and test suite SHALL execute on a Windows host and a macOS host in addition to the existing Linux host

#### Scenario: Self-hosted CI stays Linux-only

- **WHEN** the self-hosted origin CI workflow runs
- **THEN** it SHALL continue to run on its Linux runner only and SHALL NOT attempt to run the Windows or macOS matrix legs

#### Scenario: Matrix configuration is vendor-neutral

- **WHEN** the cross-platform matrix workflow is reviewed
- **THEN** it SHALL contain no internal hostnames, addresses, tracker identifiers, or internal URLs

### Requirement: Cross-platform data-dir resolution

The plugin's global-data-dir resolver SHALL be verified across platforms. On Windows the resolver SHALL produce `%LOCALAPPDATA%\openspec` using native backslash separators; `XDG_DATA_HOME` SHALL take precedence over the Windows branch when set; a null home SHALL fall back to the Unix data-dir shape; and the store and workset directories SHALL resolve under the data dir on every platform. These SHALL be verified host-independently through the parameterized resolver.

#### Scenario: Windows data dir uses LOCALAPPDATA with backslashes

- **WHEN** the resolver is invoked with the Windows platform flag set and `LOCALAPPDATA` present
- **THEN** the resolved global data dir SHALL be `<LOCALAPPDATA>\openspec` and its string form SHALL use backslash separators

#### Scenario: XDG_DATA_HOME overrides the Windows branch

- **WHEN** the resolver is invoked with the Windows platform flag set and `XDG_DATA_HOME` present
- **THEN** the resolved global data dir SHALL be derived from `XDG_DATA_HOME` rather than `LOCALAPPDATA`

#### Scenario: Store and workset directories resolve under the data dir

- **WHEN** the resolver is invoked on either the Windows or the non-Windows leg
- **THEN** the store directory and the workset directory SHALL resolve as children of the resolved global data dir

### Requirement: CRLF and LF parse parity

The store and workset JSON parsers SHALL produce identical models regardless of line endings. Each captured fixture SHALL be parsed with LF endings and with `\r\n` endings, and a trailing-`\r` variant, and all forms SHALL yield an equal model. This verification SHALL be host-independent and SHALL NOT re-implement the upstream CRLF escaping fix.

#### Scenario: LF and CRLF yield the same model

- **WHEN** a captured store or workset fixture is parsed once with LF line endings and once with `\r\n` line endings
- **THEN** the two resulting models SHALL be equal

#### Scenario: Trailing carriage return does not corrupt parsing

- **WHEN** a captured fixture containing a trailing `\r` is parsed
- **THEN** the resulting model SHALL equal the model parsed from the LF form of the same fixture

### Requirement: Native path round-tripping

The store and workset parsers SHALL preserve native path strings unchanged, including paths containing spaces, Windows drive backslash paths, and UNC paths. Assertions that resolve such strings through the platform `Path` type SHALL be gated to the operating system where that resolution is meaningful; the raw round-trip assertion SHALL remain host-independent.

#### Scenario: Spaced and backslash paths survive parsing

- **WHEN** a store root containing spaces, a Windows drive backslash path, or a UNC path is parsed
- **THEN** the parser SHALL preserve the raw path string unchanged in the resulting model

#### Scenario: Path resolution is OS-gated

- **WHEN** a test resolves a native path string through the platform `Path` type
- **THEN** that assertion SHALL run only on the operating system for which the path form is valid and SHALL be skipped elsewhere

### Requirement: Root canonicalization parity

The plugin SHALL match a store root reached by a non-canonical path to its canonical registered root. A path reached through a symlink SHALL match the canonical registered root; on Windows an 8.3 short-path form SHALL match its canonical long-path registered root. The symlink case SHALL be verified on macOS/Linux CI and the 8.3 short-path case SHALL be OS-gated to Windows.

#### Scenario: Symlinked root matches the canonical root

- **WHEN** a store root is registered and a symlink to that root is resolved by the plugin
- **THEN** the plugin SHALL match the symlinked path to the canonical registered root

#### Scenario: Windows 8.3 short-path matches the canonical root

- **WHEN** a store root is registered under its long path and referenced by its 8.3 short-path form on Windows
- **THEN** the plugin SHALL match the short-path form to the canonical long-path registered root

### Requirement: Windows CLI shim invocation

On Windows, the plugin SHALL invoke the OpenSpec CLI through its `.cmd` shim from a path containing spaces and obtain parseable output. This SHALL be verified by an integration test that runs only on the Windows matrix leg against a real CLI at the 1.5.0 floor.

#### Scenario: Store command invoked through the .cmd shim

- **WHEN** the plugin invokes a store or context command through the `.cmd` shim from a path containing spaces on Windows
- **THEN** the invocation SHALL exit with code 0 and its output SHALL parse to a model

### Requirement: Verified Windows data-dir capture

The plugin's assumed Windows data-dir location SHALL be confirmed against real CLI output. A real Windows run of `store register --json` SHALL be captured, its `registry.path` used to confirm or correct the Windows branch of the resolver, and the sanitized capture committed as a version-namespaced 1.5.0 contract fixture. A contract test SHALL assert the plugin's Windows resolution matches the captured path shape.

#### Scenario: Windows resolution matches the captured registry path

- **WHEN** the plugin resolves the Windows global data dir
- **THEN** the result SHALL match the shape of the `registry.path` captured from a real Windows `store register --json`

#### Scenario: Capture is sanitized and version-namespaced

- **WHEN** the Windows capture is committed as a fixture
- **THEN** it SHALL have machine-specific path segments sanitized and SHALL be stored under the 1.5.0-namespaced fixtures directory

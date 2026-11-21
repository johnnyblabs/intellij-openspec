## MODIFIED Requirements

### Requirement: Cross-platform CI matrix on the public mirror

The project SHALL verify the plugin on Windows and macOS as well as Linux, by running the build and test suite in a CI matrix on the public mirror. The matrix SHALL NOT live on the self-hosted origin CI, which has no Windows host and rejects newer action versions. All matrix workflow configuration SHALL be vendor-neutral, with no internal hostnames, addresses, tracker identifiers or internal URLs.

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

On Windows the global-data-dir resolver SHALL produce `%LOCALAPPDATA%\openspec` with native backslash separators. `XDG_DATA_HOME` SHALL take precedence over the Windows branch when set, and a null home SHALL fall back to the Unix data-dir shape. The store and workset directories SHALL resolve under the data dir on every platform, verified host-independently through the parameterized resolver.

#### Scenario: Windows data dir uses LOCALAPPDATA with backslashes

- **WHEN** the resolver is invoked with the Windows platform flag set and `LOCALAPPDATA` present
- **THEN** the resolved global data dir SHALL be `<LOCALAPPDATA>\openspec` and its string form SHALL use backslash separators

#### Scenario: XDG_DATA_HOME overrides the Windows branch

- **WHEN** the resolver is invoked with the Windows platform flag set and `XDG_DATA_HOME` present
- **THEN** the resolved global data dir SHALL be derived from `XDG_DATA_HOME` rather than `LOCALAPPDATA`

#### Scenario: Store and workset directories resolve under the data dir

- **WHEN** the resolver is invoked on either the Windows or the non-Windows leg
- **THEN** the store directory and the workset directory SHALL resolve as children of the resolved global data dir

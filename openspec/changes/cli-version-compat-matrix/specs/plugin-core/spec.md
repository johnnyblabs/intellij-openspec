## ADDED Requirements

### Requirement: CLI arguments match the detected CLI version

The plugin SHALL build every OpenSpec CLI invocation against the detected CLI version, using one record of the release that introduced, and any release that dropped, each command and each flag it passes. On a release that lacks an optional flag, the flag SHALL be omitted. On a release that lacks a required flag or the command itself, the plugin SHALL report the gap and SHALL NOT start the CLI. CLI version detection is exempt.

#### Scenario: An optional flag the release lacks is left out
- **WHEN** an invocation includes an optional flag that the detected release does not accept
- **THEN** the plugin SHALL run the command without that flag

#### Scenario: A required flag the release lacks is refused before the CLI runs
- **WHEN** an invocation needs a flag that the detected release does not accept
- **THEN** the plugin SHALL NOT start the CLI
- **AND** the error SHALL name the command, the flag and the release that introduced it

#### Scenario: A command the release lacks is refused before the CLI runs
- **WHEN** an invocation names a command that the detected release does not have, such as `doctor` on 1.4.x or `workspace` on 1.5.0 and later
- **THEN** the plugin SHALL NOT start the CLI and SHALL report that the command needs another release

#### Scenario: Every flag passed today reaches every release that accepts it
- **WHEN** the detected release is any supported release from 1.3.0 to 2.1.1
- **THEN** each invocation SHALL pass the same arguments it passed before this requirement, for every release where the plugin already offered it

#### Scenario: An unknown version is treated as the target release
- **WHEN** the CLI is available but its version could not be read
- **THEN** arguments SHALL be built as for the plugin's target CLI release

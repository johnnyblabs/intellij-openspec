## ADDED Requirements

### Requirement: CLI arguments are checked against every captured release

The build SHALL check the plugin's CLI argument record against each captured release's `--help` output, for every command the plugin runs. It SHALL drive every argument builder once per captured release. It SHALL fail when an emitted command or flag is missing from that release's help, when the record disagrees with the help, or when an invocation bypasses the record. Floors SHALL keep the set of releases from shrinking.

#### Scenario: A newer-only flag reaching an older release fails the build
- **WHEN** an argument builder emits a flag on a captured release whose help does not list it
- **THEN** the test suite SHALL fail and name the release, the command and the flag

#### Scenario: A record that disagrees with captured help fails the build
- **WHEN** the record says a release accepts a flag its captured help omits, or omits one its help lists for a flag the plugin passes
- **THEN** the test suite SHALL fail and name the release and the flag

#### Scenario: An invocation outside the record fails the build
- **WHEN** plugin source passes a flag literal or calls the CLI runner without going through the argument record
- **THEN** the test suite SHALL fail and name the file and line

#### Scenario: A missing capture cannot shrink the matrix
- **WHEN** a release named in a version floor has no captured help or no capture of the output a contract test reads
- **THEN** that contract test SHALL fail rather than run over fewer releases

#### Scenario: The real CLIs can be exercised before a release
- **WHEN** the maintainer runs the optional local CLI matrix task
- **THEN** every argument builder SHALL run against the real CLI package of each boundary release, in an isolated home with telemetry off, and the task SHALL fail on any unknown-option or unknown-command error
- **AND** the task SHALL NOT be part of the default build

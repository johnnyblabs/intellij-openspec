## ADDED Requirements

### Requirement: Cleanup verification reports only what the CLI confirmed

After a legacy-file cleanup, the plugin SHALL report a clean state only when the verification re-run of `openspec update` succeeds and reports no files to remove. If the re-run exits non-zero, times out or cannot be started, the plugin SHALL say the cleanup could not be verified, and SHALL NOT report a clean state.

#### Scenario: Verification re-run fails
- **WHEN** the files are removed and the verification `openspec update` exits 1 with no stdout
- **THEN** the plugin SHALL report that the cleanup could not be verified, with the CLI's stderr when it has text, and SHALL NOT say the state is clean

#### Scenario: Verification re-run cannot start
- **WHEN** the verification re-run times out or the CLI is not found
- **THEN** the plugin SHALL report that the cleanup could not be verified

#### Scenario: Successful verification is unchanged
- **WHEN** the verification re-run exits 0 and reports no files to remove
- **THEN** the plugin SHALL report the clean state as before

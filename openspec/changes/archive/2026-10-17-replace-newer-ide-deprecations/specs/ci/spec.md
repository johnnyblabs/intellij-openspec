## MODIFIED Requirements

### Requirement: Plugin Verifier compatibility

The verify job SHALL run the IntelliJ Plugin Verifier against a verified IDE set that spans the declared compatibility range, reporting binary compatibility, deprecated API usage, and experimental API usage. The verified set SHALL contain the minimum declared version (2024.2), the latest release of the last IntelliJ IDEA Community line (2025.2), and the latest release of every IntelliJ IDEA release line after it through the newest stable release. Intermediate release lines between the floor and the last Community line MAY be omitted, because the plugin compiles against the floor and API breakage surfaces first in the newest releases. Early-access (EAP) builds SHALL NOT be part of the verified set.

#### Scenario: All declared versions checked
- **WHEN** the verify job runs
- **THEN** it SHALL verify the plugin against the minimum declared version (2024.2), the last Community release line (2025.2), and each IntelliJ IDEA release line after it through the latest stable release

#### Scenario: A new stable release line is added to the verified set
- **WHEN** JetBrains ships a new stable IntelliJ IDEA release line
- **THEN** the verified set SHALL be extended to include it, so the newest stable IDE is always verified

#### Scenario: EAP builds are not verified in CI
- **WHEN** the verify job resolves its IDE set
- **THEN** it SHALL NOT include early-access builds

#### Scenario: Compatible result
- **WHEN** the Plugin Verifier reports "Compatible" for all checked versions
- **THEN** the verify job SHALL pass

#### Scenario: Incompatible result
- **WHEN** the Plugin Verifier reports binary incompatibilities for any checked version
- **THEN** the verify job SHALL fail, blocking the PR from merging

## ADDED Requirements

### Requirement: Plain-deprecated API usage is migrated when a floor replacement exists

When the Plugin Verifier reports a plain-deprecated IntelliJ Platform API usage on any IDE in the verified set, the plugin SHALL migrate it if a non-deprecated replacement exists on every IDE from the compatibility floor through the newest verified release. Otherwise the usage MAY remain as a documented exception.

#### Scenario: A usage with a floor replacement is migrated
- **WHEN** the verifier reports a plain-deprecated usage whose non-deprecated replacement exists on every IDE from 2024.2 through the newest verified release
- **THEN** the plugin SHALL use the replacement, and the verifier SHALL no longer report that usage

#### Scenario: A usage without a floor replacement is a documented exception
- **WHEN** the verifier reports a plain-deprecated usage whose replacement does not exist on 2024.2
- **THEN** the usage MAY remain, and it SHALL be listed with its reason in the documented exceptions next to the verifier configuration, so it is migrated when the floor advances

#### Scenario: Only documented exceptions remain
- **WHEN** the verify job checks the plugin against the verified set
- **THEN** every deprecated API usage it reports SHALL be one of the documented exceptions

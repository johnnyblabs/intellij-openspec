## ADDED Requirements

### Requirement: Supported CLI versions and capability preservation

The plugin SHALL declare the set of OpenSpec CLI versions it supports — currently the `1.3.x`, `1.4.x`, and `1.5.x` lines, with floor `1.3.0` — and SHALL treat the user-facing behavior it delivers **for each supported version** as a preserved contract. Preservation is **per version and bounded by what the client provides**: where the installed client of a supported version provides the underlying command, a capability the plugin offers for that version SHALL NOT be removed, disabled, or re-gated for that version without (a) a delta on the affected capability's spec, (b) an entry in the changelog, and (c) a test asserting the changed behavior. A change that alters a supported version's delivered capability set with `Modified Capabilities: None` for the affected capability SHALL be treated as a defect.

Because the client's own surface differs across versions, the plugin's delivered capability set MAY legitimately differ between supported versions. Offering a capability for one supported version and not another is **faithful mirroring, not a regression, when it tracks the client** — for example, the `workspace` / `context-store` / `initiative` commands exist in `[1.4.0, 1.5.0)` and were removed by the client in `1.5.0`, so the plugin offers the 1.4 coordination write actions for `1.4.x` and correctly retires them for `1.5.x`. Such version-scoped, self-retiring differences are permitted, but adding, removing, or re-gating any version's capability is still governed by the delta + changelog + test rule above and SHALL NOT happen silently.

#### Scenario: A supported version's client-backed capability is not silently dropped
- **WHEN** a change would remove, disable, or re-gate a user-facing capability that the plugin currently delivers for a supported CLI version whose client still provides the underlying command
- **THEN** the change SHALL include a delta on that capability's spec, a changelog entry, and a test asserting the new behavior, and absent any of these it SHALL be treated as a defect

#### Scenario: Client-faithful retirement across versions is permitted but governed
- **WHEN** a capability is retired for a newer supported version because that version's client removed the underlying command, while remaining available for older supported versions whose client still provides it
- **THEN** the retirement SHALL be permitted as faithful mirroring, and SHALL still be reflected in the affected capability's spec, in the changelog, and in per-version tests — never applied silently

#### Scenario: Supported-version behavior is test-pinned
- **WHEN** the plugin's behavior for a supported CLI version is version-gated
- **THEN** a per-version test SHALL pin that version's delivered capabilities so that removing or mis-gating one fails the build

#### Scenario: The supported-version floor is declared and pinned
- **WHEN** the set of supported CLI versions or the floor changes
- **THEN** the declared floor SHALL be the single source consulted by version-floor behavior and SHALL be asserted by a test so the declaration itself cannot drift unnoticed

## MODIFIED Requirements

### Requirement: Supported CLI versions and capability preservation

The plugin SHALL declare the set of OpenSpec CLI versions it supports — currently the `1.3.x`, `1.4.x`, `1.5.x`, and `1.6.x` lines, with floor `1.3.0` — and SHALL treat the user-facing behavior it delivers **for each supported version** as a preserved contract. Preservation is **per version and bounded by what the client provides**: where the installed client of a supported version provides the underlying command, a capability the plugin offers for that version SHALL NOT be removed, disabled, or re-gated for that version without (a) a delta on the affected capability's spec, (b) an entry in the changelog, and (c) a test asserting the changed behavior. A change that alters a supported version's delivered capability set with `Modified Capabilities: None` for the affected capability SHALL be treated as a defect.

Because the client's own surface differs across versions, the plugin's delivered capability set MAY legitimately differ between supported versions. Offering a capability for one supported version and not another is **faithful mirroring, not a regression, when it tracks the client** — for example, the `workspace` / `context-store` / `initiative` commands exist in `[1.4.0, 1.5.0)` and were removed by the client in `1.5.0`, so the plugin offers the 1.4 coordination write actions for `1.4.x` and correctly retires them for `1.5.x`. Such version-scoped, self-retiring differences are permitted, but adding, removing, or re-gating any version's capability is still governed by the delta + changelog + test rule above and SHALL NOT happen silently.

#### Scenario: A supported version's client-backed capability is not silently dropped
- **WHEN** a change would remove, disable, or re-gate a user-facing capability that the plugin currently delivers for a supported CLI version whose client still provides the underlying command
- **THEN** the change SHALL include a delta on that capability's spec, a changelog entry, and a test asserting the new behavior, and absent any of these it SHALL be treated as a defect

#### Scenario: The 1.6.x line is a supported generation
- **WHEN** the detected CLI is in the `1.6.x` line
- **THEN** the plugin SHALL deliver the full capability set it delivers for `1.5.x` (no re-gating), applying the 1.6-generation semantics specified by the affected capabilities (store health, registration outcomes, validator behavior), and per-generation contract coverage SHALL exist for the behaviors that differ

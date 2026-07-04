## ADDED Requirements

### Requirement: User-facing capability changes are disclosed

The changelog SHALL disclose changes to user-facing capabilities. When a change adds, removes, or materially alters a user-facing capability — including **retiring a capability for a supported CLI version** — it SHALL record a corresponding entry in the changelog's unreleased section, written for users. A user-facing capability that a released version delivered SHALL NOT be removed or re-gated **silently** (with no changelog entry). Internal-only refactors with no user-visible effect are exempt.

#### Scenario: Removing a shipped capability is disclosed
- **WHEN** a change removes or re-gates a user-facing capability that a released version delivered
- **THEN** the changelog's unreleased section SHALL include an entry describing the change for users, and the absence of such an entry SHALL be treated as a defect in the change

#### Scenario: Version-scoped retirement is noted with its scope
- **WHEN** a capability is retired for a supported CLI version because that version's client removed the underlying command
- **THEN** the changelog SHALL note the retirement and the version scope (which CLI line still offers it and which no longer does)

#### Scenario: Internal refactors need no capability entry
- **WHEN** a change alters implementation without adding, removing, or materially changing any user-facing capability
- **THEN** no user-facing capability changelog entry is required

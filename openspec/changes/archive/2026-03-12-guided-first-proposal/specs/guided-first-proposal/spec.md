## ADDED Requirements

### Requirement: Propose dialog placeholder text
The ProposeChangeDialog SHALL display placeholder (empty text) hints in all text fields so first-time users understand what to enter.

#### Scenario: Name field shows example
- **WHEN** the ProposeChangeDialog opens and the name field is empty
- **THEN** grayed-out placeholder text SHALL show example change names (e.g., "add-user-auth, fix-login-redirect")

#### Scenario: Why field shows example
- **WHEN** the ProposeChangeDialog opens and the why field is empty
- **THEN** grayed-out placeholder text SHALL show an example motivation

#### Scenario: What Changes field shows example
- **WHEN** the ProposeChangeDialog opens and the what changes field is empty
- **THEN** grayed-out placeholder text SHALL show an example of a change description

#### Scenario: Placeholder disappears on input
- **WHEN** the user begins typing in any field
- **THEN** the placeholder text SHALL disappear

### Requirement: First-run workflow banner
The ProposeChangeDialog SHALL display a contextual banner above the form fields during the user's first proposal explaining the OpenSpec workflow.

#### Scenario: Banner shown on first proposal
- **WHEN** the ProposeChangeDialog opens and `firstProposalCompleted` is false
- **THEN** a banner SHALL appear above the form explaining the propose → generate → implement → archive lifecycle

#### Scenario: Banner hidden for experienced users
- **WHEN** the ProposeChangeDialog opens and `firstProposalCompleted` is true
- **THEN** the banner SHALL NOT be displayed

### Requirement: First-run tracking setting
The plugin SHALL persist a `firstProposalCompleted` boolean in settings that tracks whether the user has completed their first proposal.

#### Scenario: Default value
- **WHEN** the plugin is first installed
- **THEN** `firstProposalCompleted` SHALL default to false

#### Scenario: Set after first proposal
- **WHEN** the user successfully creates their first change via ProposeChangeDialog
- **THEN** `firstProposalCompleted` SHALL be set to true

### Requirement: Post-proposal next-steps notification
After a user's first successful proposal, the plugin SHALL display a notification explaining next steps.

#### Scenario: What's Next notification fires on first proposal
- **WHEN** a proposal is successfully created and `firstProposalCompleted` was previously false
- **THEN** a notification SHALL appear in the Workflow group with title "What's Next" explaining that the user should generate artifacts from their change

#### Scenario: Notification does not fire on subsequent proposals
- **WHEN** a proposal is successfully created and `firstProposalCompleted` was already true
- **THEN** no "What's Next" notification SHALL be displayed

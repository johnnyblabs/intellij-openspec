## ADDED Requirements

### Requirement: Post-click inline guidance area

The workflow panel SHALL display an inline guidance area below the Generate/Apply button after a delivery action, showing tool-specific instructions.

#### Scenario: Guidance area appears after clipboard delivery
- **WHEN** a Generate or Apply action is delivered via clipboard
- **THEN** an inline guidance area SHALL appear below the button with status, action, and save instructions

#### Scenario: Guidance area appears after editor tab delivery
- **WHEN** a Generate or Apply action is delivered via editor tab
- **THEN** an inline guidance area SHALL appear with instructions to copy from the editor tab to the tool

#### Scenario: Guidance area hidden before first delivery
- **WHEN** no delivery action has been performed yet
- **THEN** the inline guidance area SHALL not be visible

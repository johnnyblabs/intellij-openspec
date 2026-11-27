## ADDED Requirements

### Requirement: Continue is offered beside Revise and Apply

On a profile that includes the `continue` workflow, the plugin SHALL offer Continue on an active change's right-click menu, directly after Revise Change, and as a workflow panel button after Revise. Launched for a change, Continue SHALL act on that change; it SHALL ask which change only when launched without one.

#### Scenario: Continue on the change's right-click menu
- **WHEN** the user right-clicks an active change in the Browse tree
- **THEN** the menu SHALL offer Explore This Change, Revise Change and Continue, then Apply and Archive, with Continue gated by the profile as Apply and Archive are

#### Scenario: Continue acts on the change it was launched for
- **WHEN** Continue is launched from a change's right-click menu or the workflow panel's button while several changes are active
- **THEN** it SHALL continue that change without asking which one

#### Scenario: Continue without a change asks
- **WHEN** Continue is launched from the OpenSpec menu while several changes are active
- **THEN** it SHALL ask which change to continue

### Requirement: The empty workflow panel points custom profiles at more workflows

When no change is active, the workflow panel's "No changes yet" card SHALL say "More workflows are in the OpenSpec menu." if the active profile is `custom` and includes a workflow outside the core set, and SHALL list those workflows in its tooltip. It SHALL say nothing more on the core profile.

#### Scenario: Custom profile with more workflows
- **WHEN** the active profile is `custom` and includes `continue` and `verify`
- **THEN** the card SHALL show the hint, and its tooltip SHALL name continue and verify

#### Scenario: Core profile
- **WHEN** the active profile is `core`, or `custom` with only core workflows
- **THEN** the card SHALL show only "No changes yet.", Propose a change and Explore an idea first

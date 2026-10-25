## ADDED Requirements

### Requirement: Revise change action

The plugin SHALL offer a "Revise Change..." action that hands an active change off to the user's AI tool's own `update` workflow, with an optional note saying what should change. It SHALL deliver only what the user types to run that workflow, the change name and the note, through Clipboard or Editor Tab, and SHALL NOT include the workflow's instructions. The action SHALL be gated on the `update` workflow and SHALL NOT be named "Update".

#### Scenario: Revise with a note
- **WHEN** the user chooses Revise Change... on an active change and enters "drop the export feature"
- **THEN** the plugin SHALL deliver the tool's update invocation, the change name and the note, for example `/opsx:update add-auth drop the export feature`

#### Scenario: Revise without a note
- **WHEN** the user leaves the note empty
- **THEN** the plugin SHALL deliver the invocation and the change name only, and the dialog SHALL say the agent will review the change's artifacts for coherence

#### Scenario: The tool's own invocation
- **WHEN** the user's tool has its update command or skill installed
- **THEN** the invocation SHALL follow upstream's rules for that tool and those files, as Explore's handoff does

#### Scenario: Update not installed
- **WHEN** the tool has neither its update skill nor its update command
- **THEN** the plugin SHALL deliver nothing and SHALL say to run Update OpenSpec… or add `update` to the profile, then retry

#### Scenario: Direct API
- **WHEN** the delivery mode is Direct API
- **THEN** the plugin SHALL NOT send a request, and SHALL deliver through Clipboard or Editor Tab with a note saying why

#### Scenario: Where it's offered
- **WHEN** an active change is selected
- **THEN** Revise Change... SHALL be on the change's right-click menu after Explore This Change..., on the workflow panel's change header, and in the OpenSpec menu, and SHALL NOT be offered for specs or delta specs

#### Scenario: Profile without update
- **WHEN** the active profile doesn't include the `update` workflow
- **THEN** Revise Change... SHALL be disabled, like other workflow-gated actions

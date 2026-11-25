## MODIFIED Requirements

### Requirement: Propose action

Propose SHALL do what OpenSpec's `propose` workflow does: create a change and produce every artifact it needs before apply. It SHALL ask for a description. With Direct API it SHALL also offer a change name and a schema, create the change and generate the artifacts needed before apply from the description, and stop there. With clipboard or editor tab it SHALL hand the description to the AI tool's own propose workflow. Creation SHALL run off the EDT.

#### Scenario: Change creation
- **WHEN** the user proposes a change with Direct API as the delivery method
- **THEN** the plugin SHALL create the change off the EDT, as `openspec new change` does (through the CLI when it is available, without `--description`), switch the workflow panel to it, and generate the artifacts the change needs before apply, using the description as the request, leaving any other artifact ungenerated

#### Scenario: Schema selection during propose
- **WHEN** multiple schemas are available and the user proposes a change with Direct API
- **THEN** the Propose dialog SHALL display a schema combo box and the chosen schema SHALL be passed as `--schema "<schema>"` when the change is created

#### Scenario: No template design or tasks files
- **WHEN** a change is created without the CLI
- **THEN** no template `design.md` or `tasks.md` SHALL be written, because upstream never creates them and the CLI would report them as done

#### Scenario: Handoff to the AI tool's propose workflow
- **WHEN** the user proposes a change with clipboard or editor tab as the delivery method, and the project has the tool's generated propose skill or command
- **THEN** the dialog SHALL ask only for the description, and the plugin SHALL deliver that tool's propose invocation followed by the description, and SHALL NOT create the change itself

#### Scenario: Empty description in a handoff
- **WHEN** the user proposes with clipboard or editor tab and leaves the description empty
- **THEN** the plugin SHALL deliver the propose invocation alone, so the agent asks what to build

#### Scenario: Propose workflow not installed
- **WHEN** the user proposes with clipboard or editor tab and the project has no generated propose skill or command for any candidate tool
- **THEN** the plugin SHALL warn that the propose workflow is missing and offer **Update OpenSpec…**, and SHALL NOT create a change

#### Scenario: Direct API needs a description
- **WHEN** the user proposes with Direct API and the description is empty
- **THEN** the dialog SHALL NOT allow the request to be submitted

## ADDED Requirements

### Requirement: Workflow pipeline panel

The panel SHALL show the selected change's pipeline as interactive artifact chips, the primary action surface. READY chips SHALL generate and DONE chips SHALL open the file, each with a right-click menu. A compact icon bar SHALL give secondary actions, and a one-line status strip SHALL show compliance, task progress and delivery mode. Its "no changes" card SHALL offer Propose and SHALL NOT offer Fast-Forward.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show interactive artifact status chips (DONE, READY, BLOCKED, GENERATING) with the status `openspec status` reports, a non-blocking hint on a DONE chip whose file looks like an unfilled template, hover affordances, and click/right-click actions

#### Scenario: Generate via chip click
- **WHEN** the user clicks a READY chip
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or Direct API) with a guidance popover showing post-delivery feedback

#### Scenario: Sync Specs in overflow menu
- **WHEN** all artifacts are complete and the change contains delta spec sections
- **THEN** the overflow menu SHALL include a "Sync Specs" item

#### Scenario: Sync Specs hidden from overflow
- **WHEN** the change has no delta spec sections
- **THEN** the overflow menu SHALL NOT include "Sync Specs"

#### Scenario: Compliance chip displayed
- **WHEN** the workflow action panel renders for a selected change
- **THEN** the status strip SHALL include compliance status alongside task progress and delivery mode

#### Scenario: No Fast-Forward on the empty card
- **WHEN** the "no changes" card renders, whatever the profile, delivery method or Direct API configuration
- **THEN** it SHALL offer Propose and SHALL NOT offer Fast-Forward, because Propose creates the change and generates its artifacts; Fast-Forward SHALL be reachable only through its menu and toolbar action

#### Scenario: FF input activation
- **WHEN** the user runs the Fast-Forward menu or toolbar action, which is enabled only when the active profile includes `ff`
- **THEN** the panel SHALL switch to the FF_INPUT card while keeping the tool selector visible

#### Scenario: Content views
- **WHEN** the panel shows content
- **THEN** a CardLayout SHALL switch between the NO_CHANGES, FF_INPUT and PIPELINE views

#### Scenario: Explore context
- **WHEN** the change selection changes
- **THEN** the Explore tab context SHALL include the selected change's details and follow the selection


### Requirement: New Change action

New Change SHALL do what OpenSpec's `new` workflow does: create the change without any artifact. It SHALL be gated on the `new` workflow, so on a profile without it the action is disabled with the profile suffix. It SHALL ask for a name and, when several schemas exist, a schema, and leave every artifact, the proposal first, for Continue or the workflow panel. Creation SHALL run off the EDT.

#### Scenario: Scaffold a change
- **WHEN** the user runs New Change with `new` in the active profile and submits the dialog
- **THEN** the plugin SHALL create the change as `openspec new change` does (through the CLI when it is available, without `--description`), with no artifact file, select the change in the workflow panel, and generate nothing

#### Scenario: Not in the profile
- **WHEN** the active profile does not include `new`, as on core
- **THEN** New Change SHALL be shown disabled with the profile suffix, as Fast-Forward and Continue are

## REMOVED Requirements

### Requirement: Workflow action panel

**Reason**: Replaced by "Workflow pipeline panel". Its Fast-Forward scenarios described a "no changes" card link that this change removes, and a kept scenario can't be renamed, so the requirement is restated under a new name with the same pipeline behaviour.

**Migration**: None for users. The pipeline, chips, overflow menu and status strip behave as before; the "no changes" card offers Propose only.

## ADDED Requirements

### Requirement: Ask the Agent hands a request to the OpenSpec router

On CLI 2.2.0 and later, the plugin SHALL offer Ask the Agent, which hands the user's request, unchanged, to the OpenSpec router skill through the workflow handoff, spelled for the user's AI tool as upstream spells it. The plugin SHALL NOT interpret the request, choose a skill or run a command for it.

#### Scenario: A request is handed to the router
- **WHEN** the CLI is 2.2.0, the user's tool is Claude Code with the `openspec` skill installed, and the user asks "verify add-auth, then archive it"
- **THEN** the handoff SHALL be `/openspec verify add-auth, then archive it`, the request unquoted as upstream types it, delivered as other workflow handoffs are

#### Scenario: Each tool's spelling is upstream's
- **WHEN** the user's tool is Codex, Kimi Code, or a tool with no slash commands
- **THEN** the router SHALL be named `$openspec`, `/skill:openspec`, or "the openspec skill", as the captured 2.2.0 invocations show for that tool

#### Scenario: No router before 2.2
- **WHEN** the detected CLI is older than 2.2.0
- **THEN** Ask the Agent SHALL NOT be shown

#### Scenario: The router isn't installed
- **WHEN** the CLI is 2.2.0 or later but no AI tool has the `openspec` skill installed
- **THEN** Ask the Agent SHALL be disabled, with a tooltip saying to run `openspec update` or set up an AI tool

#### Scenario: Direct API
- **WHEN** the delivery mode is Direct API
- **THEN** the handoff SHALL be copied to the clipboard with a note that the router runs in the AI tool, and no model SHALL be called

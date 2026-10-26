## ADDED Requirements

### Requirement: Settings reachable from the OpenSpec menu

The plugin SHALL offer a "Settings…" action in the OpenSpec menu and on the tool window toolbar that opens the plugin's settings page.

#### Scenario: Settings from the menu
- **WHEN** the user chooses OpenSpec → Settings…
- **THEN** the OpenSpec settings page SHALL open

#### Scenario: Settings on the toolbar
- **WHEN** the tool window toolbar is shown
- **THEN** it SHALL include the Settings action, with an icon different from Manage AI Tools'

### Requirement: Agent tools and Direct API are told apart and linked

The plugin SHALL name AI coding agents (Manage AI Tools) and Direct API providers so that each says what it does, and each surface SHALL point to the other.

#### Scenario: The API delivery label names the API
- **WHEN** a Direct API provider is configured and no delivery preference is saved
- **THEN** the delivery label SHALL read "Generate via <provider> API", for example "Generate via Claude API"

#### Scenario: Adding a tool says what it did
- **WHEN** the user adds or configures a tool in Manage AI Tools and OpenSpec succeeds
- **THEN** the plugin SHALL say the tool is set up, that OpenSpec wrote its commands to the project, and that prompts are labeled "Copy for <tool>" to paste into the agent

#### Scenario: Adding a tool offers Direct API
- **WHEN** that notification is shown and no Direct API provider is configured
- **THEN** it SHALL offer "Set up Direct API…", which opens the settings page

#### Scenario: Direct API points to agents
- **WHEN** the user views the Direct API section of the settings page
- **THEN** it SHALL say Direct API is optional, that an agent like Claude Code needs no key, and SHALL link to Manage AI Tools

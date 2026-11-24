## MODIFIED Requirements

### Requirement: Agent tools and Direct API are told apart and linked

The plugin SHALL name AI coding agents (Manage AI Tools) and Direct API providers so that each says what it does, and each surface SHALL point to the other.

#### Scenario: The API delivery label names the API
- **WHEN** a Direct API provider is configured and no delivery preference is saved
- **THEN** the delivery label SHALL read "Generate via <provider> API", for example "Generate via Claude API"

#### Scenario: Adding a tool says what it did
- **WHEN** the user adds or configures a tool in Manage AI Tools and OpenSpec succeeds
- **THEN** the plugin SHALL say the tool is set up, that OpenSpec wrote its skills (CLI 2.x) or its `/opsx` commands (CLI 1.x) to the project, and that prompts are labeled "Copy for <tool>" to paste into the agent

#### Scenario: The CLI's setup note is passed on
- **WHEN** that notification is for a tool whose registry entry in the detected CLI release has a setup note (Hermes from 1.7, with its 2.x text from 2.0, and on 2.1 nine existing tools that use the universal skills folder)
- **THEN** the notification SHALL include that note as that release's captured registry records it, except, as the CLI prints it, for a tool outside the universal skills folder whose skills were installed only for the machine

#### Scenario: The dialog's subtitle names what OpenSpec writes
- **WHEN** Manage AI Tools opens
- **THEN** its subtitle SHALL describe the agents as running OpenSpec's skills on CLI 2.x and its `/opsx` commands on CLI 1.x

#### Scenario: Adding a tool offers Direct API
- **WHEN** that notification is shown and no Direct API provider is configured
- **THEN** it SHALL offer "Set up Direct API…", which opens the settings page

#### Scenario: Direct API points to agents
- **WHEN** the user views the Direct API section of the settings page
- **THEN** it SHALL say Direct API is optional, that an agent like Claude Code needs no key, and SHALL link to Manage AI Tools

# Documentation

## ADDED

### REQ-DOC-README: README reflects current feature set

The README SHALL document all shipped workflow actions: Init, Propose, Fast-Forward, Continue, Generate, Verify, Apply, Archive, Sync Specs, Bulk Archive, Explore, Manage AI Tools, Update.

The README SHALL document all tool window tabs: Browse, Coverage, Console, Explore.

The README SHALL document all settings sections: CLI, General, Config Profile, Schemas, Tools & Delivery, Direct API.

The README SHALL include a Menu Reference table listing every action with its description.

#### Scenario: User reads README for feature overview

- Given the plugin is at version 0.2.3 or later
- When a user reads README.md
- Then every action registered in plugin.xml is documented
- And every tool window tab is described
- And every settings section is explained

### REQ-DOC-MARKETPLACE: Marketplace page reflects current features

The marketplace page SHALL list custom schemas, explore panel, config management, and CLI update in the Key Features section.

The marketplace page SHALL include updated screenshot guidance covering the Workflow Action Panel and Explore tab.

#### Scenario: Marketplace visitor sees current capabilities

- Given a user visits the JetBrains Marketplace listing
- When they read the Description section
- Then all v0.2.x features are mentioned
- And the Key Features list is complete

### REQ-DOC-GUIDE: Getting-started guide uses current workflow

The getting-started guide SHALL use the Workflow Action Panel (pipeline chips, Generate button) as the primary workflow, not right-click context menus.

The getting-started guide SHALL document the Config Profile and Schemas settings sections.

#### Scenario: New user follows the guide

- Given a user follows the getting-started guide step-by-step
- When they reach the generation steps
- Then instructions reference the Workflow Action Panel
- And the settings walkthrough covers all current sections

### REQ-DOC-MATRIX: Feature matrix shows current version

The feature comparison matrix SHALL show the plugin version as `0.2.3` (not `0.1.0-dev`).

The matrix SHALL include rows for features added in v0.2.0 through v0.2.3.

#### Scenario: Reader compares plugin features

- Given a reader opens the feature comparison matrix
- When they check the version row
- Then it shows `0.2.3`
- And new feature rows exist for workflow actions, spec sync, bulk archive, and custom schemas

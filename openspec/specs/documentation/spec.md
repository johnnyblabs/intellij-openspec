# Documentation

## Purpose
Keeping README, marketplace page, getting-started guide, and feature matrix current with the shipped feature set.

## Requirements

### Requirement: README reflects current feature set

The README SHALL document all shipped workflow actions: Init, Propose, Fast-Forward, Continue, Generate, Verify, Apply, Archive, Sync Specs, Bulk Archive, Explore, Manage AI Tools, Update.

The README SHALL document all tool window tabs: Browse, Coverage, Console, Explore.

The README SHALL document all settings sections: CLI, General, Config Profile, Schemas, Tools & Delivery, Direct API.

The README SHALL include a Menu Reference table listing every action with its description.

#### Scenario: User reads README for feature overview
- **WHEN** a user reads README.md
- **THEN** every action registered in plugin.xml SHALL be documented, every tool window tab SHALL be described, and every settings section SHALL be explained

### Requirement: Marketplace page reflects current features

The marketplace page SHALL list custom schemas, explore panel, config management, and CLI update in the Key Features section.

The marketplace page SHALL include updated screenshot guidance covering the Workflow Action Panel and Explore tab.

#### Scenario: Marketplace visitor sees current capabilities
- **WHEN** a user reads the JetBrains Marketplace Description section
- **THEN** all v0.2.x features SHALL be mentioned and the Key Features list SHALL be complete

### Requirement: Getting-started guide uses current workflow

The getting-started guide SHALL use the Workflow Action Panel (pipeline chips, Generate button) as the primary workflow, not right-click context menus.

The getting-started guide SHALL document the Config Profile and Schemas settings sections.

#### Scenario: New user follows the guide
- **WHEN** a user follows the getting-started guide and reaches the generation steps
- **THEN** instructions SHALL reference the Workflow Action Panel and the settings walkthrough SHALL cover all current sections

### Requirement: Feature matrix shows current version

The feature comparison matrix SHALL show the plugin version as `0.2.3` (not `0.1.0-dev`).

The matrix SHALL include rows for features added in v0.2.0 through v0.2.3.

#### Scenario: Reader compares plugin features
- **WHEN** a reader opens the feature comparison matrix
- **THEN** the version row SHALL show the current plugin version and new feature rows SHALL exist for workflow actions, spec sync, bulk archive, and custom schemas
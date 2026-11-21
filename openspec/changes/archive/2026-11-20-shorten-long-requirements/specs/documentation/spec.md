## MODIFIED Requirements

### Requirement: README reflects current feature set

The README SHALL document every shipped workflow action, every tool window tab and every settings section, and SHALL include a Menu Reference table listing every action with its description.

#### Scenario: User reads README for feature overview
- **WHEN** a user reads README.md
- **THEN** every action registered in plugin.xml SHALL be documented, every tool window tab SHALL be described, and every settings section SHALL be explained

#### Scenario: Names the shipped surfaces
- **WHEN** a reader checks the README against the plugin's surfaces
- **THEN** it SHALL name the workflow actions Init, Propose, Fast-Forward, Continue, Generate, Verify, Apply, Archive, Sync Specs, Bulk Archive, Explore, Manage AI Tools and Update
- **AND** it SHALL name the tool window tabs Browse, Coverage, Console and Explore
- **AND** it SHALL name the settings sections CLI, General, Config Profile, Schemas, Tools & Delivery and Direct API

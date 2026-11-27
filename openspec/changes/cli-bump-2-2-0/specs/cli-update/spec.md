## ADDED Requirements

### Requirement: The update preview covers the CLI 2.2 router skill

From CLI 2.2.0 the update plan also writes the `openspec` router skill and its `references/` files. The preview SHALL show a tool folder's router entry as a link where the CLI writes a link, and SHALL warn about an edited router file the update would overwrite, as it does for the workflow skills.

#### Scenario: Router link shown as a link
- **WHEN** a 2.2.0 plan adds `.claude/skills/openspec` with no file extension
- **THEN** the preview SHALL show it as a link to `.agents/skills/openspec`

#### Scenario: Edited router reference at risk
- **WHEN** a 2.2.0 plan overwrites a router file the user edited, such as `.agents/skills/openspec/references/answers.md`
- **THEN** the preview SHALL list it among the edited files the update would replace

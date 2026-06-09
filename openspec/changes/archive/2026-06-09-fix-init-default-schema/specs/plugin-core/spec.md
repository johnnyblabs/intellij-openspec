## MODIFIED Requirements

### Requirement: Project detection and initialization

The plugin SHALL detect OpenSpec projects by checking for `openspec/` and provide initialization via CLI delegation with built-in fallback. The built-in fallback SHALL honor the user's configured Default schema preference when writing `openspec/config.yaml`, falling back to `spec-driven` only when no preference is set.

#### Scenario: Project detection
- **WHEN** a project is opened in the IDE
- **THEN** the plugin SHALL check for `openspec/` directory and enable/disable features accordingly

#### Scenario: Init delegates to CLI when available
- **WHEN** `ScaffoldingService.initOpenSpec()` is called and the CLI is detected
- **THEN** it SHALL run `openspec init --tools <detected-tools>` and perform a synchronous VFS refresh

#### Scenario: Init falls back to built-in
- **WHEN** the CLI is unavailable or fails
- **THEN** the service SHALL create `openspec/config.yaml`, `specs/`, `changes/`, and `changes/archive/` via VFS

#### Scenario: Default schema honored on built-in init
- **WHEN** the user has set Default schema to a value other than empty (e.g., `workspace-planning`) in Settings → Tools → OpenSpec, and the built-in init path runs
- **THEN** the generated `openspec/config.yaml` SHALL contain `schema:` matching the user's chosen value, not the hardcoded literal `spec-driven`

#### Scenario: Default schema fallback when unset
- **WHEN** the user has not set Default schema (the setting is empty), and the built-in init path runs
- **THEN** the generated `openspec/config.yaml` SHALL contain `schema: spec-driven` (preserving the prior default behavior)

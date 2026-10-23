# Spec Delta

## MODIFIED Requirements

### Requirement: Generation prompt tolerates glob-valued dependency paths

When assembling the prompt to generate an artifact, the plugin inlines the content of each completed dependency. A dependency's declared path MAY be a glob pattern rather than a single file — the OpenSpec CLI declares the `specs` dependency's path as `specs/**/*.md`. The plugin SHALL NOT resolve a glob-valued path (one containing `*`, `?`, or `[`) to a single filesystem path, on any operating system. It SHALL instead inline every existing file in the change that matches the glob, each labeled with its path, and SHALL fall back to a path-only reference when none match. Artifact generation SHALL NOT fail because a dependency path cannot be resolved to a filesystem path.

#### Scenario: Completed dependency has a glob-valued path
- **WHEN** an artifact is generated whose completed dependency declares a glob-valued path (e.g. `specs/**/*.md`)
- **THEN** the plugin SHALL NOT attempt to read that glob as a single file
- **AND** the generation prompt SHALL inline each file in the change that matches the glob, labeled with its path relative to the change, or SHALL reference the dependency by its path when no file matches
- **AND** generation SHALL succeed identically on Windows and on POSIX platforms, without a path-resolution error

#### Scenario: Completed dependency is a real file
- **WHEN** an artifact is generated whose completed dependency declares a concrete (non-glob) file path that exists on disk
- **THEN** the plugin SHALL inline that file's content into the generation prompt

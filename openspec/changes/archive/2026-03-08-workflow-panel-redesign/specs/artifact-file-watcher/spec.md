## ADDED Requirements

### Requirement: Auto-detect artifact file changes after clipboard or editor delivery
After clipboard or editor-tab delivery, the panel SHALL watch for the expected artifact file to appear or change and auto-refresh when detected.

#### Scenario: Artifact file created after clipboard delivery
- **WHEN** an artifact prompt is copied to the clipboard and the user creates the artifact file externally
- **THEN** the panel SHALL detect the new file, invalidate the DAG cache, refresh the pipeline, and transition to the next ready artifact

#### Scenario: Artifact file modified after clipboard delivery
- **WHEN** an artifact prompt is copied to the clipboard and the existing artifact file is modified externally
- **THEN** the panel SHALL detect the modification, invalidate the DAG cache, and refresh the pipeline

#### Scenario: File watcher timeout
- **WHEN** no file change is detected within 10 minutes of delivery
- **THEN** the file watcher SHALL stop watching and display a hint: "Artifact not detected yet. Click 'Check for updates' to refresh manually."

#### Scenario: File watcher cleanup on change switch
- **WHEN** the user switches to a different active change while a file watcher is active
- **THEN** the file watcher SHALL be unregistered

#### Scenario: File watcher cleanup on panel disposal
- **WHEN** the tool window panel is disposed while a file watcher is active
- **THEN** the file watcher SHALL be unregistered

### Requirement: Periodic VFS refresh as fallback
The file watcher SHALL periodically trigger a VFS refresh on the change directory to catch external file changes that the VFS event system may miss.

#### Scenario: External tool writes artifact file
- **WHEN** an external tool (e.g., Claude Code CLI) writes the artifact file outside IntelliJ's awareness
- **THEN** the periodic VFS refresh (every 5 seconds) SHALL detect the file and trigger the auto-refresh flow

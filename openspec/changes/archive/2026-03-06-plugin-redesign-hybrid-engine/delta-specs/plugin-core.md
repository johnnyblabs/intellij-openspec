# Delta Spec: Plugin Core

## MODIFIED

### Settings Infrastructure
- Added `OpenSpecSettings` PersistentStateComponent with version override, CLI path, profile, auto-refresh, strictness
- Added `OpenSpecConfigurable` for Settings > Tools > OpenSpec panel
- Added `OpenSpecSettingsPanel` Swing form with CLI detection button

### CLI Detection
- Added `CliDetectionService` — detects CLI via settings path, `which`, or bare command
- Modified `CliRunner` — uses detected CLI path, adds timeout, wraps exceptions as `CliException`

### Project Service
- Modified `OpenSpecProjectService` — triggers CLI detection on startup, exposes settings and detection service accessors
- Added `StartupDetection` ProjectActivity — shows notification if CLI missing

### Notifications
- Added `OpenSpecNotifier` — wraps NotificationGroupManager with info/warn/error/cliMissing helpers

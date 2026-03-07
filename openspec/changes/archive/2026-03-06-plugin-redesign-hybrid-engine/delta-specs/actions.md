# Delta Spec: Actions

## MODIFIED

### OpenSpecCliAction
- Added `handleCliMissing()` hook for subclass fallback behavior
- CLI commands now run in `Task.Backgroundable` to avoid EDT blocking
- Output routed to OpenSpec tool window Console tab instead of Run window
- Shows notification via OpenSpecNotifier on CLI-missing

### OpenSpecValidateAction
- No longer extends OpenSpecCliAction — standalone hybrid implementation
- Always runs BuiltInValidator; merges CLI validation when available
- Shows structured results with error/warning counts in console and notification
- Supports `--all` and `--strict` flags from settings

### OpenSpecProposeAction
- Uses ProposeChangeDialog (DialogWrapper) instead of simple input dialog
- Falls back to ScaffoldingService.createChange() when CLI unavailable

### OpenSpecInitAction
- Falls back to ScaffoldingService.initOpenSpec() when CLI unavailable

### OpenSpecArchiveAction
- Falls back to ChangeService.archiveChange() when CLI unavailable

### OpenSpecListAction
- Falls back to built-in listing via SpecParsingService and ChangeService

### OpenSpecRefreshAction
- Updated to work with tabbed tool window layout

## 1. Notification Group Registration

- [x] 1.1 Replace single `notificationGroup` in `plugin.xml` with 5 groups: `OpenSpec.Workflow`, `OpenSpec.Generation`, `OpenSpec.Validation`, `OpenSpec.System` (STICKY_BALLOON), `OpenSpec.Tracker`

## 2. OpenSpecNotifier API Expansion

- [x] 2.1 Add group constants (GROUP_WORKFLOW, GROUP_GENERATION, GROUP_VALIDATION, GROUP_SYSTEM, GROUP_TRACKER)
- [x] 2.2 Add core `notify(project, groupId, title, content, type, actions...)` method with `NotificationAction` support
- [x] 2.3 Add titled convenience methods: `info(project, title, content)`, `warn(project, title, content)`, `error(project, title, content)`
- [x] 2.4 Add action factory methods: `openFileAction(VirtualFile)`, `openSettingsAction()`
- [x] 2.5 Add `generateAllSummary(project, count, elapsed)` method for collapsed summary
- [x] 2.6 Update legacy `info/warn/error(project, content)` to route through new core method with `OpenSpec.Workflow` group

## 3. Migrate Workflow Action Notifications

- [x] 3.1 Update `OpenSpecProposeAction` — title: "Propose"
- [x] 3.2 Update `OpenSpecApplyAction` — title: "Apply"
- [x] 3.3 Update `OpenSpecArchiveAction` — title: "Archive"
- [x] 3.4 Update `OpenSpecInitAction` — title: "Initialize"

## 4. Migrate Generation Notifications

- [x] 4.1 Update single-artifact generation notifications in `WorkflowActionPanel` — title: "Generate", group: Generation, add Open File action
- [x] 4.2 Replace per-artifact Generate All notifications with collapsed summary using `generateAllSummary()`
- [x] 4.3 Update Generate All error notification — title: "Generate All", group: Generation

## 5. Migrate Validation Notifications

- [x] 5.1 Update `OpenSpecValidateAction` — title: "Validate", group: Validation

## 6. Migrate System Notifications

- [x] 6.1 Update `cliMissing()` — title: "CLI Detection", group: System, add Open Settings action
- [x] 6.2 Update API failure notifications in `OpenSpecSettingsPanel` and `WorkflowActionPanel` — title: "API Error", group: System, add Open Settings action
- [x] 6.3 Update `ConfigService` parse errors — title: "Configuration", group: System
- [x] 6.4 Update `CliDetectionService` / `OpenSpecProjectService` notifications — group: System

## 7. Migrate Tracker Notifications

- [x] 7.1 Update `IssueLifecycleService` notifications — title: "Forgejo"/"Plane", group: Tracker
- [x] 7.2 Update `ArchiveSyncService` notifications — title: "Tracker Sync", group: Tracker

## 8. Migrate Remaining Notifications

- [x] 8.1 Update `ExploreContextAction` — title: "Explore Context", group: Workflow
- [x] 8.2 Update `CreateDeltaSpecAction` — title: "Delta Spec", group: Workflow
- [x] 8.3 Update `OpenSpecCliAction` — title from `getCommandLabel()`, group: Workflow
- [x] 8.4 Update `OpenSpecListAction` — title: "List", group: Workflow
- [x] 8.5 Update `GettingStartedPanel` and `OpenSpecToolWindowPanel` error notifications — group: System
- [x] 8.6 Update `ChangeService` YAML parse warning — group: System

## 9. Verification

- [x] 9.1 Build compiles with no errors
- [x] 9.2 All existing tests pass
- [x] 9.3 Verify notifications display with titles in IDE
- [x] 9.4 Verify Generate All fires single summary instead of per-artifact balloons
- [x] 9.5 Verify error notifications are sticky (persist until dismissed)

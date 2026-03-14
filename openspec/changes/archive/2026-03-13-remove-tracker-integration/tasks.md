## 1. Delete tracking package and tests

- [x] 1.1 Delete all 7 files in `src/main/java/com/johnnyb/openspec/tracking/` (`ForgejoService.java`, `PlaneService.java`, `IssueLifecycleService.java`, `ArchiveSyncService.java`, `TrackerCredentialStore.java`, `TrackerType.java`, `TrackingMetadataWriter.java`)
- [x] 1.2 Delete test files `src/test/java/com/johnnyb/openspec/tracking/IssueLifecycleServiceTest.java` and `ArchiveSyncServiceTest.java`

## 2. Remove plugin.xml registrations

- [x] 2.1 Remove the 4 `<projectService>` registrations for `ForgejoService`, `PlaneService`, `IssueLifecycleService`, `ArchiveSyncService`
- [x] 2.2 Remove the `<notificationGroup id="OpenSpec.Tracker">` entry

## 3. Remove tracking calls from actions

- [x] 3.1 In `OpenSpecProposeAction.java`: remove `IssueLifecycleService` import and the 4-line block calling `lifecycle.onPropose()` (lines 66-70)
- [x] 3.2 In `OpenSpecApplyAction.java`: remove `IssueLifecycleService` import and the 4-line block calling `lifecycle.onApply()` (lines 103-107)
- [x] 3.3 In `OpenSpecArchiveAction.java`: remove `ArchiveSyncService` import, remove the sync phase block (lines 69-75), and just call `refreshToolWindow(project)` after archive success. Update the class javadoc to remove sync references.

## 4. Remove tracking from WorkflowActionPanel

- [x] 4.1 Remove `ArchiveSyncService` import (line 24)
- [x] 4.2 Remove `syncRetryButton` field declaration (line 139), its initialization (lines 251-254), and its addition to `actionRow` (line 342)
- [x] 4.3 Remove the sync block in the archive handler: replace lines 1089-1095 with direct call to `showPostArchiveState(changeName)`
- [x] 4.4 Simplify `showPostArchiveState` to remove the `syncSuccess` parameter — always show success (remove sync-failure branch, lines 1125-1128, and `syncRetryButton` references)
- [x] 4.5 Delete `showSyncOutcome()` method (lines 1731-1760) and `onRetrySyncAction()` method (lines 1762-1777)

## 5. Remove tracking from model and settings

- [x] 5.1 In `ChangeMetadata.java`: remove the `tracking` field, its getter/setter (lines 36-42), and the three inner classes `TrackingMetadata`, `ForgejoRef`, `PlaneRef` (lines 44-72)
- [x] 5.2 In `OpenSpecSettings.java`: remove Forgejo and Plane accessor methods (lines 134-160) and the 8 state fields (lines 184-192)
- [x] 5.3 In `OpenSpecNotifier.java`: remove the `GROUP_TRACKER` constant (line 21)

## 6. Verification

- [x] 6.1 Build the project (`./gradlew build`) and verify no compilation errors

## 1. Credential storage

- [x] 1.1 Create `TrackerCredentialStore` utility class following `AiCredentialStore` pattern with `OpenSpec-Tracker-` service prefix, supporting FORGEJO_TOKEN and PLANE_API_KEY
- [x] 1.2 Add store/get/remove/has methods for each tracker type

## 2. Settings extension

- [x] 2.1 Add tracker settings fields to `OpenSpecSettings.State`: forgejoEnabled, forgejoUrl, forgejoOwner, forgejoRepo, planeEnabled, planeUrl, planeWorkspace, planeProject
- [x] 2.2 Add getters/setters for all new tracker settings fields
- [x] 2.3 Add "Issue Tracking" section to `OpenSpecSettingsConfigurable` with Forgejo and Plane sub-groups, enable checkboxes, URL/credential fields, and "Test Connection" buttons
- [x] 2.4 Wire enable checkboxes to disable/enable their sub-group fields

## 3. Forgejo API client

- [x] 3.1 Create `ForgejoService` as `@Service(Service.Level.PROJECT)` with HttpClient and Gson, reading credentials from `TrackerCredentialStore` and URLs from `OpenSpecSettings`
- [x] 3.2 Implement `createIssue(title, body, labels)` → returns issue number and URL
- [x] 3.3 Implement `addComment(issueNumber, body)` and `updateIssue(issueNumber, state, labels)`
- [x] 3.4 Implement `testConnection()` → validates credentials by calling the repo API endpoint
- [x] 3.5 Register `ForgejoService` in `plugin.xml`

## 4. Plane API client

- [x] 4.1 Create `PlaneService` as `@Service(Service.Level.PROJECT)` with HttpClient and Gson, reading credentials from `TrackerCredentialStore` and URLs from `OpenSpecSettings`
- [x] 4.2 Implement `createWorkItem(title, descriptionHtml)` → returns work item ID and URL
- [x] 4.3 Implement `updateWorkItemState(workItemId, stateName)` for transitioning work item states
- [x] 4.4 Implement `testConnection()` → validates credentials by listing project details
- [x] 4.5 Add basic markdown-to-HTML conversion utility for Plane description field (headings, lists, paragraphs, bold/italic)
- [x] 4.6 Register `PlaneService` in `plugin.xml`

## 5. Tracking metadata

- [x] 5.1 Extend `ChangeService` (or `.openspec.yaml` parser) to read/write optional `tracking` block with `forgejo.issueNumber`, `forgejo.issueUrl`, `plane.workItemId`, `plane.workItemUrl`
- [x] 5.2 Ensure tracking metadata survives read/write round-trips without losing other YAML fields

## 6. Issue lifecycle orchestration

- [x] 6.1 Create `IssueLifecycleService` as `@Service(Service.Level.PROJECT)` that coordinates Forgejo and Plane during lifecycle events
- [x] 6.2 Implement `onPropose(changeName, changeDir)` — creates issues in enabled trackers, writes tracking metadata to `.openspec.yaml`
- [x] 6.3 Implement `onApply(changeName, changeDir)` — adds comment/updates label in Forgejo, transitions state in Plane
- [x] 6.4 Implement `onArchive(changeName, changeDir)` — closes Forgejo issue, transitions Plane work item to done state
- [x] 6.5 All tracker calls execute on background thread via `ApplicationManager.getApplication().executeOnPooledThread()`
- [x] 6.6 Register `IssueLifecycleService` in `plugin.xml`

## 7. Action integration

- [x] 7.1 Modify `OpenSpecProposeAction` to call `IssueLifecycleService.onPropose()` after successful proposal creation
- [x] 7.2 Modify `OpenSpecApplyAction` to call `IssueLifecycleService.onApply()` after successful delivery
- [x] 7.3 Modify `OpenSpecArchiveAction` to call `IssueLifecycleService.onArchive()` after successful archive

## 8. Tool window indicators

- [x] 8.1 Add linked/unlinked indicator to `SpecTreeCellRenderer` for change nodes that shows tracker link status (icon or text suffix)
- [x] 8.2 Read tracking metadata from `.openspec.yaml` in `SpecTreeModel` and pass to `TreeNodeData`

## 9. Verify

- [x] 9.1 Run `./gradlew clean build test` — all green
- [x] 9.2 Verify settings panel shows Issue Tracking section with Forgejo and Plane sub-groups
- [x] 9.3 Verify Test Connection buttons work for both trackers
- [x] 9.4 Verify Propose creates issues in both trackers and writes metadata to `.openspec.yaml`
- [x] 9.5 Verify Apply updates issue status in both trackers
- [x] 9.6 Verify Archive closes issues in both trackers
- [x] 9.7 Verify tracking failures show warnings but do not block primary actions

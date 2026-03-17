## 1. Context Assembly Service

- [x] 1.1 Create `ExploreContextService` as a `@Service(Service.Level.PROJECT)` with `assembleContext()` method that returns a Markdown-formatted string
- [x] 1.2 Implement config summary section (version, schema) delegating to `ConfigService`
- [x] 1.3 Implement active changes section with proposal summaries (truncated to 500 chars) delegating to `ChangeService`
- [x] 1.4 Implement spec domains section by scanning `openspec/specs/` directories
- [x] 1.5 Implement detected AI tools section with tool type classification delegating to `AiToolDetectionService`
- [x] 1.6 Register `ExploreContextService` in `plugin.xml` as a project service

## 2. Explore Panel UI

- [x] 2.1 Create `ExplorePanel` extending `JPanel` with `BorderLayout`, toolbar, and read-only `JBTextArea` in `JBScrollPane`
- [x] 2.2 Add Copy to Clipboard toolbar button using `AllIcons.Actions.Copy` that copies assembled context and shows a notification
- [x] 2.3 Add Open in Editor toolbar button using `AllIcons.Actions.EditSource` that creates/updates a Markdown scratch file and opens it in the editor
- [x] 2.4 Implement initial context load on panel creation via pooled thread with EDT update
- [x] 2.5 Cache scratch file `VirtualFile` reference in the panel to reuse on subsequent Open in Editor clicks

## 3. Tool Window Registration

- [x] 3.1 Register Explore tab in `OpenSpecToolWindowFactory.createNormalContent()` as the fourth tab after Console
- [x] 3.2 Verify tab appears alongside Browse, Coverage, and Console in a configured project

## 4. Auto-Refresh via VFS Listener

- [x] 4.1 Subscribe to `VirtualFileManager.VFS_CHANGES` in `ExplorePanel` via project message bus, filtering to paths containing `/openspec/`
- [x] 4.2 Implement 500ms debounce timer that resets on each new VFS event and triggers context reassembly
- [x] 4.3 Implement `Disposable` on `ExplorePanel` to unsubscribe the VFS listener and cancel timers on dispose

## 5. Refactor ExploreContextAction

- [x] 5.1 Refactor `ExploreContextAction` to delegate context assembly to `ExploreContextService` instead of inline logic
- [x] 5.2 Verify existing clipboard behavior is preserved after refactoring

## 6. Workflow Integration

- [x] 6.1 Update workflow spec delta to add scenario for Explore tab reflecting current change selection

## 7. Tests

- [x] 7.1 Create `ExploreContextServiceTest` with test for empty project state (no config, no changes, no specs)
- [x] 7.2 Add test for full project state with config, active changes, specs, and detected tools
- [x] 7.3 Add test verifying context format includes expected Markdown section headers
- [x] 7.4 Add test for proposal summary truncation at 500 characters

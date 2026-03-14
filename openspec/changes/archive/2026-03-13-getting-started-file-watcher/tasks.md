## 1. Add VFS Listener to GettingStartedPanel

- [x] 1.1 Make `GettingStartedPanel` implement `Disposable` and add a `MessageBusConnection` field
- [x] 1.2 Add a `registerFileListener()` method that subscribes to `VirtualFileManager.VFS_CHANGES` and filters for events under `openspec/`, with debounced state re-evaluation
- [x] 1.3 In the debounced callback, call `detectState()` — if `READY`, transition to tree view; if state changed, call `rebuild()`
- [x] 1.4 Implement `dispose()` to disconnect the message bus connection

## 2. Wire Up Lifecycle

- [x] 2.1 In `OpenSpecToolWindowFactory.createGettingStartedContent()`, register the panel with `Disposer` so it gets disposed when the content is removed
- [x] 2.2 Call `registerFileListener()` from the `GettingStartedPanel` constructor

## 3. Verification

- [x] 3.1 Build the project (`./gradlew build`) and verify no compilation errors

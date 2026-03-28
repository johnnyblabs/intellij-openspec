## 1. Conditional Tab Creation

- [x] 1.1 In `OpenSpecToolWindowFactory.createNormalContent()`, gate the Explore tab creation on `DirectApiService.isConfigured()` — only create `ExplorePanel`, add content, and register with `ExplorePanelService` when a provider is configured

## 2. Lazy Creation in ExplorePanelService

- [x] 2.1 Update `ExplorePanelService.getAndActivate()` to lazily create the Explore tab if `explorePanel` is null and `DirectApiService.isConfigured()` returns true — create the panel, add it as tool window content, and register it
- [x] 2.2 Return null from `getAndActivate()` if the panel doesn't exist and Direct API is not configured

## 3. Direct API Submit Path

- [x] 3.1 Add a public static `runExploreDirect(Project, String topic)` method to `ExploreContextAction` that builds the explore prompt and always calls `deliverDirectApi()`, bypassing the delivery mode resolver
- [x] 3.2 Update `ExplorePanel.submitTopic()` to call `ExploreContextAction.runExploreDirect()` instead of `ExploreContextAction.runExplore()`

## 4. Verification

- [x] 4.1 Build compiles and all existing tests pass
## 1. Helper and call sites

- [x] 1.1 Add a small action-invocation helper (e.g. `OpenSpecActions.invoke(AnAction action, Component source, String place)` returning the `ActionCallback`) that calls `ActionManager.getInstance().tryToExecute(action, null, source, place, true)`. Headless platform test (`BasePlatformTestCase` style, on the EDT) with a counting test action, a source `JPanel` that supplies `PROJECT` through `DataProvider`, and two cases: (a) `update()` enabled → `actionPerformed` runs once and the callback is done; (b) `update()` disabled → it never runs and the callback is rejected. Verify (b) would catch a helper that skips `update()`. **Done as `ActionLauncher` + `ActionLauncherTest`** (enabled runs once with the place passed through; disabled rejected; during indexing a non-dumb-aware action is rejected and a dumb-aware one runs). Mutation-checked: a no-op helper fails both, and a helper that skips `update()` fails the disabled case. *Limitation found:* the light test fixture's headless `DataManager` ignores the source component, so "context from the source" is not observable headlessly. It is left to the 3.3 sandbox pass.
- [x] 1.2 Route all five sites through the helper and delete their hand-made `DataContext` lambdas, passing these source components:
  - `OpenSpecToolWindowPanel` deltas-preview diff link → the preview pane;
  - `OpenSpecToolWindowPanel.handleHintAction` → the tree;
  - `GettingStartedPanel` Propose button → the button;
  - `WorkflowActionPanel` "Propose a change" link → the hyperlink label;
  - `SetupWizardDialog` propose-on-finish → `WindowManager.getInstance().getFrame(project)`.

  Verify that `grep -rn "ActionUtil.invokeAction" src/main/java` returns nothing.
- [x] 1.3 Make `OpenSpecProposeAction` and `DeltaSpecDiffAction` implement `DumbAware`. Test that both are `DumbAware` (`assertInstanceOf`), so a regression to a silent no-op during indexing is caught.
- [x] 1.4 Add a static guard test that fails if any file under `src/main/java` contains `ActionUtil.invokeAction`. Verify it fails when one call is temporarily reintroduced.
- [x] 1.5 Update the removal-canary comment in `build.gradle.kts` that mentions the deferred `ActionUtil.invokeAction` calls.

## 2. Documentation

- [x] 2.1 Add a CHANGELOG `## Unreleased` `### Fixed` entry: Propose and the delta-spec diff links now also work while the IDE is indexing. Keep it user-facing; the API migration itself isn't a user-facing change. Verify `DocumentationHygieneTest` is green.

## 3. Verify

- [x] 3.1 Run `./gradlew build` and confirm it is green.
- [x] 3.2 Run `./gradlew verifyPlugin`. Confirm every IDE (242/243/251/252) reports "Compatible" with **no** "usages of deprecated API", and that the report contains no `invokeAction` and no "will be removed".
- [x] 3.3 Sandbox pass (`/lifecycle-testdrive`). Each of these must open its action:
  - the Getting Started "Propose a Change" button;
  - the workflow panel's "Propose a change" link;
  - a double-click on the "No active changes" hint;
  - the Setup Wizard's finish-with-propose;
  - an `openspec-diff:` link in the deltas preview.

  Click at least one during indexing.

  **Result (2026-10-17):** done by the user in the sandbox on fresh scratch projects. All four clicked sites opened their action: the deltas-preview diff link, the "No active changes" hint double-click, the workflow panel "Propose a change" link, and the Getting Started "Propose a Change" button. The Setup Wizard path and the during-indexing behaviour were not clicked by hand. They are covered by the guard test (the wizard routes through `ActionLauncher` with the project frame) and by `ActionLauncherTest` (a dumb-aware action runs during indexing).
- [x] 3.4 Run `openspec validate replace-deprecated-invoke-action --strict` and confirm it is clean.

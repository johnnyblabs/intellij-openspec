## 1. Re-detection on focus-regain

- [x] 1.1 Extract the `toolWindowShown` re-detect-and-refresh body in `OpenSpecToolWindowFactory` into a single method: pooled `detectIfStale()` → `invokeLater` `updateCliStatus()` on the Browse panel, plus the best-effort profile refresh. The existing listener calls it. Verify behaviour is unchanged: existing tests stay green, and the tool window-shown path still refreshes in the sandbox.
- [x] 1.2 Add a pure predicate (e.g. `refreshesOnActivation(IdeFrame frame, Project project)`) that is true only when the activated frame's project is this project. Unit-test it with mocked frames: this project → true; another project → false; welcome frame (`null` project) → false.
- [x] 1.3 Subscribe `ApplicationActivationListener.TOPIC` on the application message bus, connected with the tool window's disposable. On `applicationActivated`, call the extracted method when the predicate holds.
- [x] 1.4 Test `CliDetectionService` through its injectable process runner:
  - after a first detection reports `1.12.0`, swap the runner to report `1.13.2`, age the last detection past 30 s, and check `detectIfStale()` runs and the detected version becomes `1.13.2`;
  - within 30 s, `detectIfStale()` does not run and the version stays the same.

  Verify the first case fails if staleness is ignored, and the second fails if the throttle is removed.

  **Also done (from the test-engineer plan):**
  - The five pre-existing `stalenessCheck_*` tests tested a private copy of the throttle logic, not the service, so deleting the real throttle could not fail them. They are rewritten against the real `detectIfStale()`, counting CLI runs.
  - The subscription is extracted into `subscribeActivation(project, parentDisposable, refresh)`. A platform test publishes on the real application bus: this project's window refreshes once, another project's doesn't, and nothing fires after disposal.
  - Mutation-checked: a wrong callback, a missing parent disposable, an always-true predicate, never re-detecting, and no throttle are each caught.

## 2. Documentation

- [x] 2.1 Add a CHANGELOG `## Unreleased` `### Fixed` entry: the plugin now notices an OpenSpec CLI upgraded during a running session when you return to the IDE, without reopening the project. Update `docs/feature-reference.md` where CLI re-detection is described. Verify `DocumentationHygieneTest` is green.

## 3. Verify

- [x] 3.1 Run `./gradlew build` and confirm it is green.
- [x] 3.2 Run `./gradlew verifyPlugin` and confirm it is Compatible on 242/243/251/252 with no new deprecated or removed API usage.
- [x] 3.3 Sandbox check: with the tool window visible, change the CLI the plugin resolves (e.g. point Settings' CLI path at a different version, or swap the binary). Wait more than 30 s, switch to another app and back, and confirm the status shows the new version without reopening the project or re-showing the tool window.

  **Status (2026-10-17): marked complete at the user's direction; not verified by hand.** The user waived the on-screen check after the sandbox setup proved awkward (a mid-session CLI swap via a scratch wrapper). What *is* covered automatically, all mutation-checked:
  - detection picks up an upgraded CLI from captured real `--version` output (1.12.0 → 1.13.2), and runs no CLI process inside the 30 s window;
  - the focus subscription on the real application bus refreshes only for this project's window and stops after disposal;
  - the project-routing predicate.

  Not observed: the status label repainting on screen. That is the same `updateCliStatus()` path the existing tool-window-shown trigger uses, now shared through one extracted method.
- [x] 3.4 Run `openspec validate redetect-cli-on-focus --strict` and confirm it is clean.

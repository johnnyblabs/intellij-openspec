# Tasks

## 1. Right-click menu and action

- [ ] 1.1 `contextMenuFor`: Continue after Revise Change for every active-change node type (D1).
- [ ] 1.2 `OpenSpecContinueAction`: use the data context's change when it is active; ask otherwise (D3).

## 2. Workflow panel

- [ ] 2.1 Continue button after Revise: hidden without `continue` in the profile, enabled with a ready artifact, tooltips per state (D2).
- [ ] 2.2 "No changes yet" hint for custom profiles with workflows beyond core, with the workflows in its tooltip (D4).

## 3. Tests

- [ ] 3.1 Context menu order for the three change node types.
- [ ] 3.2 Continue resolves the data-context change; asks only without one or for an inactive one.
- [ ] 3.3 Button: hidden on core; on custom with `continue`, enabled with a ready artifact, disabled with "(all artifacts done)" when complete.
- [ ] 3.4 Hint: none on core or a custom profile with only core workflows; shown, listing the extras, otherwise.
- [ ] 3.5 Mutations, each turning a test red: the button shown on core; the hint on core; Continue ignoring the data-context change.

## 4. Docs and verification

- [ ] 4.1 Feature reference and a CHANGELOG entry under Unreleased.
- [ ] 4.2 `./gradlew build`, `openspec validate --all --strict`.

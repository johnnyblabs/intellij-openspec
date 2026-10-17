## 1. Contract

- [x] 1.1 Capture `openspec status --change c --json` from CLI 1.14.3 for a change containing the plugin's `TemplateProvider` design and tasks templates (isolated `HOME`/`XDG_*`) into `fixtures/cli/1.14.3/status-template-artifacts/`, with the change files alongside, and document provenance in the fixtures README. Verify: the capture reports all four artifacts done and `isComplete: true`.

## 2. Trust the CLI

- [x] 2.1 Remove `applyScaffoldingOverrides` from `ArtifactOrchestrationService.getArtifactStatus`; add `templateLookingArtifacts(changeName, dag)` (D1). Verify: a contract test where the DAG for the capture equals the parsed capture (DONE everywhere, complete) and `templateLookingArtifacts` returns design and tasks; `ScaffoldingOverrideTest` and `ArtifactOrchestrationServiceTest` updated deliberately (their downgrade assertions become pass-through and hint assertions). Mutation check: re-adding any status downgrade fails the contract test.
- [x] 2.2 `ScaffoldingService.createChange` writes only `.openspec.yaml`, `proposal.md` and `specs/` (D2); remove the template methods if they're now unused. Verify: a test that a built-in Propose creates exactly that set, and that `openspec status` on the result (captured, or run in the test against a real-disk project if the CLI is unavailable) reports proposal done, design/specs ready, and tasks blocked.

## 3. The hint

- [x] 3.1 DONE chips for template-looking artifacts get the muted style and tooltip, with no change to click, menu or enablement (D3); confirm the surface with plugin-ui-specialist. Verify: a headless test of the chip's tooltip and enabled state for a flagged and an unflagged DONE artifact.

## 4. Docs and verify

- [x] 4.1 CHANGELOG (`### Changed`: the plugin shows each artifact's status exactly as OpenSpec reports it, and a file that still looks like a template is pointed out instead of blocking Apply, Verify and Archive; Propose no longer creates template design and task files), and `docs/feature-reference.md` (pipeline chips, Propose). Verify: entries present; a leak grep over the diff is clean.
- [x] 4.2 Sandbox pass: a change with a short but real `design.md` (Archive enabled, no hint); a change with the old templates (DONE with the hint, Regenerate replaces it); a new built-in Propose (only proposal and specs created, design ready). Record results here.
  - Recorded 2026-10-17 (CLI 1.14.3, IDEA 2024.2 sandbox):
    - **Old templates:** a change holding the old plugin templates showed proposal and specs green, and design and tasks as `design · template` / `tasks · template` in muted text with check marks. The tooltip pointed to Regenerate, which is in the menu, and Apply was enabled.
    - **Short real files:** a change whose `design.md` ("Use a set.") and `tasks.md` (one ticked task) are real but tiny first showed the template hint too. That was the detector's leftover 20-character rule; it was replaced by "nothing left after stripping". After the relaunch, all four chips were plain green with no hint, and Archive was enabled. Before this change, those files disabled Archive.
    - **Built-in Propose** runs only without the CLI, so it is covered by `ProposeCreatesUpstreamSetTest` and the real 1.14.3 capture (`status-propose-upstream-set`: proposal done, design and specs ready, tasks blocked) rather than in the sandbox.
- [x] 4.3 Run `./gradlew build`, `./gradlew verifyPlugin` and `openspec validate trust-cli-artifact-status --strict`. Verify: all pass.

## Context

See proposal.md, Why. `OpenSpecSettingsPanel` is the content of a project `Configurable`, so it always lives inside the modal Settings dialog. Its CLI-backed actions run on a pooled thread and post their UI updates back with `ApplicationManager.getApplication().invokeLater(runnable)`. From a background thread, that defaults to `ModalityState.NON_MODAL`, so the update is queued until no modal dialog is open.

A headless probe confirmed it (`LaterInvocator.enterModal`, then `invokeLater` from a pooled thread): the plain call did not run while the modal was open and ran as soon as it closed. `invokeLater(r, modality)` ran straight away. Two methods in the same file, `detectCli()` and `onProviderChanged()`, already capture `ModalityState.stateForComponent(mainPanel)` and pass it.

Seven sites lack the modality: `loadSchemaList`, `validateSelectedSchema`, `openSelectedSchemaTemplates`, `forkSelectedSchema`, `createNewSchema`, `refreshConfigProfileSection` and `onImDoneClicked`.

Real `schema fork --json` and `schema init --json` output was captured from CLI 1.3.0 and 1.14.3; the shapes are identical. Fork reports `destinationPath` and init reports `path`, both pointing at the new schema's directory, which contains `schema.yaml` and `templates/`. The "Schema commands are experimental" note goes to stderr, so stdout is clean JSON. `--no-default` also exists from 1.3.0.

## Goals / Non-Goals

**Goals:**
- Every background result on the settings page reaches the open dialog.
- Fork and New open the created `schema.yaml`.
- New passes the dialog's description and artifacts.
- A guard keeps a bare `invokeLater` out of the settings panel.

**Non-Goals:**
- The Bulk Archive dialog's identical pattern, tracked separately.
- Redesigning the schema section's UI, or exposing a "set as default" choice in the New dialog.

## Decisions

### D1. Post results with the modality that is current while the dialog is showing, and defer the initial loads until then

Each action captures `ModalityState.current()` on the EDT, before it leaves for the pooled thread, and passes it as the second argument to every `invokeLater` it makes. The capture goes through one helper, `uiModality()`. During a user action such as a button click, the Settings dialog is showing, so `current()` is the dialog's modality. This is what the headless probe showed delivering.

The loads that used to run from the constructor are deferred until the panel is first shown, via `UiNotifyConnector.doWhenFirstShown(mainPanel, this::onFirstShown)`. Those loads are the schema list, the config/profile section and CLI detection. At construction the panel isn't in a showing window, so any modality captured then is NON_MODAL, which is the same bug. `onFirstShown()` runs the loads in the original order, then re-detects the CLI. `loadSchemaList` and the profile section's CLI read return early until the panel has been shown. A `@TestOnly simulateFirstShownForTest()` stands in for the first show in a headless test. `doWhenFirstShown(JComponent, Runnable)` exists on 2024.2, and `verifyPlugin` covers the newer IDEs.

*Alternatives considered:*
- `ModalityState.stateForComponent(mainPanel)`, the earlier version of this design: during construction `mainPanel` is still null (an `IllegalArgumentException` that would stop Settings opening), and the panel has no window yet (NON_MODAL). The test-engineer's plan caught this, and the mutation check confirms it fails the dialog test.
- `ModalityState.any()`: this runs the update even while an unrelated modal dialog is on top. The platform discourages it for UI mutation.
- Running the CLI call synchronously on the EDT: this would freeze the dialog and break the project rule that CLI work stays off the EDT.

If the dialog closes before a result arrives, the queued update runs later against a panel that's no longer shown. That's harmless.

### D2. Read schema paths from `--json`, and open `schema.yaml`

`SchemaService.forkSchema` and `initSchema` add `--json`, parse stdout through the existing `CliJson.extractJsonPayload`, and read `destinationPath` (fork) or `path` (init). They return `<dir>/schema.yaml` when that file exists, and otherwise return the directory, so the caller can still report where the schema went. A response without the key, or one that isn't JSON, returns `null`; the caller already handles `null` as "nothing to open".

`initSchema` gains `description` and `artifacts` parameters, passed as `--description` (only when non-blank) and `--artifacts a,b,c`, plus a constant `--no-default`. Without `--no-default` the CLI may ask whether to make the new schema the default. The plugin runs it without a terminal, so it can't answer that question.

*Alternative considered:* scraping the prose output ("Destination: …", "Schema created at: …"). That's fragile across CLI versions when a structured mode exists all the way back to the floor.

### D3. Guard: no `invokeLater` without a modality in the settings panel

A source-scan test, in the style of `ActionInvocationHygieneTest`, finds each `invokeLater(` call in `OpenSpecSettingsPanel.java`. It scans to the call's matching closing parenthesis, skipping nested brackets, and requires a top-level comma, meaning a second argument. Its own pattern tests cover:
- a one-argument call (flagged);
- a two-argument call (allowed);
- a lambda containing commas (counted correctly, not mistaken for a second argument).

### D4. Prove the dialog behaviour headlessly

`OpenSpecSettingsPanelModalityTest` (a `BasePlatformTestCase`) sets up the panel without any real CLI:
- It replaces `CliDetectionService` with an instance whose process-runner seam reports CLI 1.14.3, through a test-source helper.
- It replaces `SchemaService` with one seeded from the captured `schemas --json` through `@TestOnly seedSchemaListForTest`. The panel loads on a pooled thread, where a thread-local static mock of the CLI runner wouldn't apply.

The test then:
1. enters a modal state with `LaterInvocator.enterModal`;
2. constructs the panel and asserts nothing has loaded yet;
3. calls `simulateFirstShownForTest()`;
4. pumps events, and records whether the list filled **before** the modal is left.

After leaving the modal, it asserts that the load delivered at all, then that it delivered while the dialog was open, and that the saved default schema is selected. Three mutations each fail the "while open" assertion:
- removing `loadSchemaList`'s modality;
- making `uiModality()` return NON_MODAL;
- using `stateForComponent(mainPanel)`.

Refresh reloads through the same `loadSchemaList`; a headless Refresh test would have to stop `clearCache()` from reaching the CLI, so the guard (D3) and the sandbox pass cover it.

## Risks / Trade-offs

- **The test seam leaks test concerns into production code.** → Keep it package-private and minimal, in the style of `CliDetectionService.setLastDetectionTimeForTest`.
- **A future CLI renames `destinationPath` or `path`.** → The contract tests read the captured fixtures, so re-capturing a newer CLI with different keys fails them loudly. A missing key returns `null`, which means nothing opens rather than a crash.
- **Passing `--artifacts` with an unusual selection.** → The dialog already enforces its own rules; the CLI validates the list and reports failures through the existing `schema init failed` path.

## Migration Plan

Code-only. To roll back, revert the commit.

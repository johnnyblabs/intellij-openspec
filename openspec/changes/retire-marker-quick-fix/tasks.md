## 1. The gate and the edit

- [ ] 1.1 `RetireMarkerFix`:
  - `asksForMarker(refusal)` (D1 text gate);
  - `withMarker(yaml)` (D2);
  - the offer (text gate plus edit check, off the UI thread);
  - `retirableWithMarker(change)` (D1a: the plugin's merge with the marker declared).
- [ ] 1.2 Contract test over every captured `retire/` refusal (1.8.0, 1.11.0, 1.16.1, 2.2.0):
  - Offered exactly for `without-marker`, `marker-not-boolean`, `marker-number`, `marker-null` and `marker-yes`.
  - Not offered for the leftovers, invalid-YAML, schema, `created:` and already-retired refusals, nor on 1.7.0.
  - Where offered, the plugin's merge on the edited metadata retires, as the CLI does on `clean`.
  - The plugin's own refusal on `marker-invalid-project-schema` (no reason given) is not offered, because the edit check fails.
  - A two-capability change: the confirmation lists both ids, though a CLI refusal would name one.
- [ ] 1.3 Unit tests for the edit:
  - Comments and other keys are kept.
  - Replacing a value, including one with indented continuation lines.
  - Appending to a file with no final newline.
  - Refusing to write when the result isn't honored.

## 2. Surfaces

- [ ] 2.1 Confirm, write and re-preview (D3), shared by all surfaces.
- [ ] 2.2 `SyncPreviewDialog`: an `ActionLink` in the refused panel when D1 matches.
- [ ] 2.3 Archive-failure notification action: tool window, Archive, Verify.
- [ ] 2.4 `BulkArchiveDialog` hint.
- [ ] 2.5 Tests:
  - The link and the notification action appear exactly when D1 matches.
  - The confirmation text names every spec.
  - Cancelling leaves the file unchanged.
- [ ] 2.6 Mutation checks: offering on a leftovers refusal, offering on an invalid-YAML reason, writing without the confirmation; each must fail a test.

## 3. Docs and verification

- [ ] 3.1 Feature reference (Sync Specs, Archive) and CHANGELOG.
- [ ] 3.2 `./gradlew build`, `openspec validate --all --strict`.

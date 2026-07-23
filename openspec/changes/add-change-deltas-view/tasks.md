## 1. Fixtures — capture change-show CLI output first

- [x] 1.1 Capture real `openspec show <change> --type change --json` from CLI 1.6.0 into `src/test/resources/fixtures/cli/1.6.0/change-deltas/`: `mixed.show.json` (ADDED+MODIFIED+REMOVED+RENAMED across ≥2 capabilities), `rename-only.show.json` (isolates the requirement-less branch), `empty.show.json` (`deltaCount:0`). Isolated `HOME`/`XDG_*`; **redirect stderr to /dev/null (stdout only)** — the CLI prints a spurious `Ignoring flags not applicable to change: scenarios` to stderr on success; sanitize `root.path`→`/fixture`. Add a `change-deltas` row to `fixtures/cli/README.md`. **Verified shape gotchas to pin (a hand-written fixture would get these wrong):** every non-RENAMED delta carries BOTH `requirement` (singular) and `requirements[]` (a 1-element mirror — the CLI splits multi-requirement blocks into one-delta-per-requirement, so `requirement == requirements[0]`); `REMOVED` carries `requirement.text` with `scenarios: []` (not requirement-less); only `RENAMED` is requirement-less (`rename{from,to}`, no `requirement`).

## 2. Delta model + CLI parse

- [x] 2.1 A small parse of the change-show JSON (`{id,title,deltaCount,deltas[]}`) into a delta model: per element `spec`, `operation`, `description`, `requirement{text,scenarios[].rawText}`/`requirements[]`, and the RENAMED variant `rename{from,to}` (no requirement). Null-safe on the rename shape. Reuse `DeltaSpecOperation.OperationType`.
- [x] 2.2 Group the parsed deltas by `spec` (capability), preserve within-capability operation order (ADDED→MODIFIED→REMOVED→RENAMED, then authored), and impose a stable cross-capability sort (alphabetical by `spec`).

## 3. Rendering — consolidated deltas fragment (pure, testable)

- [x] 3.1 `SpecPreviewRenderer.renderChangeDeltas(model)`: pure model→HTML-fragment. Header (`Deltas — <change>` + summary counts: N capabilities · X ADDED · Y MODIFIED · Z REMOVED · W RENAMED), then per capability (`<h2>`), then per operation (`<h3>` carrying the operation keyword so `DeltaBadgeDecorator` badges it), then each requirement's text + scenario `rawText`. RENAMED renders from/to. Empty model → the empty-state marker. Reuse `RequirementAnchors`.
- [x] 3.2 Add `PreviewKind.CHANGE_DELTAS`; `SpecPreviewRenderer.classify` routes a `CHANGE` node to it.
- [x] 3.3 Emit a diff cross-link anchor per capability section (e.g. `openspec-diff:<capability>`) for the `HyperlinkListener` to resolve.

## 4. Wire into the preview pipeline

- [x] 4.1 In `OpenSpecToolWindowPanel`, route a `CHANGE_DELTAS` selection to: run `openspec show <name> --type change --json` via `CliRunner` in the existing `previewAlarm` pooled thread (generation-token guarded), parse stdout, assemble the fragment, `invokeLater` to set the pane. Set the `CHANGE_DELTAS` accessible-name marker on success.
- [x] 4.2 Per-change render cache keyed on change name, invalidated by the existing VFS/`BulkFileListener` for `changes/<name>/specs/**`, so re-selection doesn't re-spawn the CLI.
- [x] 4.3 `HyperlinkListener` on the preview `JEditorPane`: resolve the `openspec-diff:<capability>` anchors to `DeltaSpecDiffAction` for that capability. (Guard so normal http links, if any, are unaffected.)
- [x] 4.4 Update the preview empty-state copy to mention change-node selection ("…or a change to see its consolidated deltas").
- [x] 4.5 CLI-unavailable path: render the "Consolidated deltas require the OpenSpec CLI" placeholder — with a **distinct marker**, NOT the empty-state marker, so "no CLI" and "no deltas" are never indistinguishable — rather than hand-assembling.

## 5. Tests — contract-first, pure-unit, uiSmoke

- [x] 5.1 `ChangeDeltasContractTest` (headless; alongside `CliContractTest`/`SpecParserCliStructureContractTest`): parse each captured fixture into the model. `mixed`: `deltaCount==4`, two capabilities, each operation classified to the right `OperationType`, MODIFIED/REMOVED requirement text + scenario `rawText` recovered, **REMOVED has empty scenarios**, RENAMED yields from/to and **null requirement**; assert the `requirements[0].text == requirement.text` invariant. `empty`: `deltaCount==0`. `rename-only`: single RENAMED, no NPE. **Keep the boundary split** — fixture→model is the contract test; hand-built model→HTML is the pure render test (hand-building the model is fine; hand-building the JSON is the cardinal sin).
- [x] 5.2 `ChangeDeltasRenderTest` (headless pure, sibling of `SpecPreviewRenderTest`) for `renderChangeDeltas`. **Sort trap:** hand-build a model with capabilities inserted **reverse-alphabetically** (`billing` before `auth`) and assert rendered `<h2>` order is `auth` then `billing` — do NOT rely on the fixture's incidental order. Both-directions badges: an ADDED section carries `openspec-op-added`, the header/summary and a RENAMED section carry **no** badge span. RENAMED: renders `from→to` AND asserts **no** requirement-body element (proves the null-safe branch). Empty model → exact `EMPTY_STATE_MARKER`. Summary: assert the **specific** numbers (2 capabilities · 1 ADDED · 1 MODIFIED · 1 REMOVED · 1 RENAMED). Ban "non-empty"/"doesn't throw" as a sole assertion.
- [x] 5.3 `DeltaDiffAnchorTest` (headless pure): the anchor resolver round-trips — `capabilityFromHref(diffAnchorHref("auth")) == "auth"` — AND the negative `capabilityFromHref("http://x") == null` (proves the http-link guard). Include a capability name with a hyphen/colon so a naive `split(":")` fails.
- [x] 5.5 One platform test (`BasePlatformTestCase`) OR a pure `changeShowArgs(name)` unit test: assert the CLI argv is `["show", <name>, "--type", "change", "--json"]` and that a **non-empty stderr does not blank the pane** (stdout-only, stderr-tolerant). Prefer extracting `changeShowArgs` as a pure method and unit-testing it.
- [x] 5.4 Release-gated uiSmoke journey: select a change node → assert the preview accessible name flips to the `CHANGE_DELTAS` rendered marker AND a badge span is present. (Never per-PR; select via the tree model API, not a row click.)

## 6. Docs, demo, build

- [x] 6.1 Add one screenshot-tour shot of the consolidated deltas (operation badges over a change) — the epic's visual thesis. Wire it into the tour + capture script (PNG capture is a manual `./gradlew screenshotTour` follow-up).
- [x] 6.2 Update `docs/feature-reference.md` (and the wiki tool-window page) to describe the deltas view + the diff cross-link. Vendor-neutral.
- [x] 6.3 Update `CHANGELOG.md` `## Unreleased` (user-facing, vendor-neutral: selecting a change shows its consolidated spec deltas). No tracker identifiers.
- [x] 6.4 Run `./gradlew build` green (suite + coverage floor); ratchet floors if coverage rose. Confirm `verifyPlugin` not required (no new `com.intellij.*` risk, no new dependency).

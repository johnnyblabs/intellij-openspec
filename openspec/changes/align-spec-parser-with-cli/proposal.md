## Why

The plugin's `SpecParsingService` recovers spec structure (requirements, scenarios, normative keywords) with multiline regexes that disagree with how the OpenSpec CLI itself parses the same files. The divergences are not cosmetic: the parser matches structural markers **inside fenced code blocks**, **miscounts scenarios** in both directions, and classifies **normative keywords** with the wrong set — so the tree/list can show a requirement count, scenario count, or keyword state that the CLI would never report. This is the keystone of the "Spec Intelligence & Viewing" work: the viewer, deltas view, and tree badge overlays that build on top of it are only as trustworthy as the parse beneath them, so the parse must match the CLI before anything is rendered from it.

## What Changes

- Reimplement `SpecParsingService` as a **line-oriented scanner that mirrors the CLI's own algorithm** — a code-fence mask applied before every structural match, plus anchored per-line recognition — replacing the current fence-blind multiline regexes.
- **Fence-awareness**: requirement headers, scenario headers, and normative keywords are no longer recognized inside fenced code blocks.
- **Scenario recognition** matches the CLI: **any** level-4 (`####`) header counts as a scenario; the previously-recognized bold `**Scenario:**` form is dropped, and the previously-required `Scenario:` label is no longer required.
- **Normative-keyword recognition** matches the CLI: the set becomes exactly `SHALL`/`MUST` (case-sensitive), tested against the **requirement body** rather than the whole requirement section. This adds `MUST` (previously omitted) and stops treating `SHOULD`/`MAY` as normative.
- **Requirement-header** recognition stays case-insensitive (matching the CLI's asymmetry: the header token is case-insensitive, the keyword is case-sensitive), and non-ATX / indented / trailing-hash header forms the CLI ignores are ignored.
- Consumers (`SpecTreeModel`, `OpenSpecListAction`, `OpenSpecProjectService`) are updated only as needed to stay behavior-preserving against the corrected model; the tree/list simply reflects the now-correct counts.
- **Out of scope (deliberate):** this does **not** touch the separate parse path used by validation and editor inspections (`BuiltInValidator`). The two-parser drift risk is accepted here and recorded as a follow-up rather than resolved in this change.

## Capabilities

### New Capabilities
- `spec-parsing`: The contract for how the plugin recognizes OpenSpec spec structure — headers, requirements, scenarios, and normative keywords — from spec markdown, in parity with the OpenSpec CLI parser, including code-fence exclusion. This carves the parsing behavior out as its own capability so the viewing features can depend on a stated contract rather than an implementation detail.

### Modified Capabilities
<!-- None. `tree-view` and related consumers render whatever the parser produces; no tree-view requirement changes, only the underlying parse is corrected. Validation's separate parse path is intentionally untouched. -->

## Impact

- **Affected code:** `SpecParsingService` (rewritten), its model classes (`SpecFile`/`Requirement`/`Scenario` — value equality added for parity testing), `SpecPatterns` (the incorrect scenario/keyword patterns retired or replaced), and the consumers `SpecTreeModel` / `OpenSpecListAction` / `OpenSpecProjectService` (behavior-preserving).
- **No new dependency.** The change deliberately does **not** adopt a markdown-AST library: the CLI is a line scanner, so a general CommonMark parser would be more permissive than the dialect the plugin must match. The `org.commonmark` library already on the classpath is not used for spec-structure parsing.
- **Behavior change users will see:** requirement/scenario/keyword counts in the tree and list may change for existing specs — because they become correct (matching `openspec validate` / `openspec show`). This is the intended effect, not a regression.
- **Platform compatibility:** unaffected. Pure parsing/model code; no IntelliJ Platform API surface changes, so IntelliJ IDEA 2024.2+ support is unchanged and `verifyPlugin` is not implicated.
- **Follow-up (separate change):** unify this parser with `BuiltInValidator`'s parse path so validation and the tree cannot drift.
- **Tracker:** this change is linked to an existing tracker issue via the gitignored `.tracking.yaml` sidecar, per the repository's tracker-sidecar convention.

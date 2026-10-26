# Tasks

## 1. Diff vs current spec

- [x] 1.1 Add `DiffVsCurrentSpec.entries` (design D1). Verify:
  - a DONE specs artifact with `auth` and `billing` gives two entries with the change's `specs/<capability>/spec.md` paths;
  - a non-specs artifact, a READY specs artifact and a change without delta specs give none.
- [x] 1.2 Add the submenu to the DONE chip menu (design D2, D4). Verify:
  - the specs chip's menu ends with "Diff vs current spec" holding one item per capability, after Open file, Regenerate and Copy prompt;
  - another DONE chip's menu doesn't have it;
  - an item launches the diff for its capability's path;
  - no menu text says "changed", "last", "before" or "previous".
- [x] 1.3 Add the popover link after a Direct API specs generation (design D3). Verify:
  - the link is offered for a specs output path and not for others;
  - with one capability it opens that diff, with several it offers a choice of all of them.

## 2. Docs and gates

- [x] 2.1 Update the feature reference and add a CHANGELOG line. Verify: `DocumentationHygieneTest` passes.
- [x] 2.2 Mutation-check: offer the entries for every DONE chip, and drop the popover link for specs. Each must fail a test, then be reverted. Run `./gradlew build`, `./gradlew verifyPlugin` and the full uiSmoke suite (`caffeinate -dimsu`). Verify: all green, 5/5 IDEs Compatible.

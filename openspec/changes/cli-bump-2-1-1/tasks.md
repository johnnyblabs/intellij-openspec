# Tasks

## 1. Target

- [x] 1.1 `openspecTargetVersion=2.1.1`; `TargetVersionSingleSourceTest` green (D1).
- [x] 1.2 Run the full suite. Fix tests that assumed the 1.17.0 target with no CLI so they state their release explicitly, and list the behavior each fix reflects (D1).

## 2. Code

- [x] 2.1 `CliInstallHints` says Node.js 22.20 or later for npm, with a test that fails on the old floor (D2).

## 3. This repository

- [x] 3.1 Install CLI 2.1.1 globally (pinned) so `openspec --version` equals the target. Run `openspec update` and commit the layout move separately (D3).
- [x] 3.2 Revise the CLAUDE.md restore recipe for `.agents/skills/` and the links, and prove it on a scratch clone. Confirm the custom skills are untouched and the `.claude/commands` tests pass.

## 4. UI smoke

- [x] 4.1 The 2.x layout journey on CLI 2.1.1 from the capture cache (D4).

## 5. Docs and verification

- [x] 5.1 "OpenSpec CLI 1.3.0 through 2.1.x" on the README, `openspec-support.md`, marketplace page, feature reference and comparison matrix. Add a `docs/cli-versions/2.x.md` note with a mermaid diagram of the 1.x → 2.x skills layout, and a CHANGELOG entry naming the CLI-less behavior that now follows 2.1.1.
- [x] 5.2 The screenshot tour runs on this repository's 2.x layout again: it copies CLI 1.17.0's generated `/opsx` files from `src/integrationTest/testData/claude-1.17.0`, not this repository's. Its shots still tell the 1.x story (an `/opsx` handoff, and a real 2.1.1 migration where the legacy-cleanup review used to be), so retaking them for 2.x moved to its own tracker entry, and the committed screenshots stay until then.
- [x] 5.3 Workflow ↔ UI parity audit against 2.1.1 (D5), with gaps filed. No core workflow is unreachable. Gaps filed for the next release: Continue's reach, Verify archiving on OK, Verify's non-Direct-API handoff; capability retirement was already tracked. Stale "commands" copy fixed here.
- [ ] 5.4 `./gradlew build`, `./gradlew verifyPlugin`, the full `caffeinate -dimsu ./gradlew uiSmoke`, and `openspec validate --all --strict`.

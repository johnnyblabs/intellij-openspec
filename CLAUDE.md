# intellij-openspec — agent conventions

Project-specific rules for AI agents working in this repo.

## Tracker mirroring — invoke the custom skills, never inline curl

OpenSpec changes are mirrored to project trackers via project-level custom skills. The three lifecycle skills keep the Plane card flowing **Todo → In Progress → Done** in step with the change, so it stays legible in Kanban/cycle/board views at every stage:

| When | Skill | Plane state |
|---|---|---|
| After `openspec-propose` | `/mirror-change-trackers <name>` | → Todo |
| At the start of `openspec-apply-change` (implementation begins) | `/advance-change-trackers <name>` | → In Progress |
| After `openspec-archive-change` | `/close-change-trackers <archive-dir>` | → Done |

Without the middle step the card jumps Todo→Done and never appears in the "In Progress" column while work is actually happening. Forgejo issues have no In-Progress state (open/closed only), so `advance-change-trackers` touches Plane only and leaves the issue open.

**Why these skills are custom-named, not inside `openspec-*`:** the `openspec` CLI manages `.claude/skills/openspec-*/SKILL.md` and rewrites them on every `openspec update`. Custom-named skills (`mirror-change-trackers`, `advance-change-trackers`, `close-change-trackers`, `release-cut`, `release-prep`) live outside that managed surface and survive updates.

**These five skills are gitignored** (`.gitignore` entries `.claude/skills/{mirror,advance,close}-change-trackers/`, `.claude/skills/release-cut/`, and `.claude/skills/release-prep/`). They intrinsically reference `forgejo.geek`, the Plane project UUID, `mcp__homelab__*` server-tool names, and `johnb/intellij-openspec` — things that violate the anti-leak rule below if they reach the public GitHub mirror. The skill files exist on disk and work in any local session; they just don't ride to git history. Edits to these skills are local-only.

**Do not put tracker plumbing back into the `openspec-*` skills.** If you find yourself tempted, you're at the wrong layer.

**A change must not archive without a tracker entry, unless the change says why it has none.** Mirror the change when its proposal lands. Before archiving, check for the `.tracking.yaml` sidecar. If it's missing, either mirror the change now or record the reason in the change's `tasks.md`, using vendor-neutral wording (for example, "Not tracked: created before the tracker convention"). Silently skipping the tracker step is not allowed.

**Parked or planned changes.** A change whose artifacts live only on a branch has no directory on `main`. Don't create a stub change directory just to hold its sidecar: the mirror skill keeps the sidecar in a gitignored clone-local location until the change lands on `main`, then moves it into the change directory.

**Do not commit these skills back into tracked state.** If you need a tracked skill that does similar work, write a vendor-neutral one that takes config from environment / a separate file rather than hardcoding the homelab references.

## Tracker IDs go in a gitignored `.tracking.yaml` sidecar

Inside each change directory (`openspec/changes/<name>/`, and the archived form), tracker IDs live in a `.tracking.yaml` file. The file is gitignored so it never enters version control. `mirror-change-trackers` writes it; `close-change-trackers` reads it.

**Do not put tracker IDs in `proposal.md`, `design.md`, or `tasks.md`** — those files are published when the change archives.
**Do not put them in `.openspec.yaml`** — it is committed with the change and published when the change archives, so anything written there reaches the public mirror. Its upstream schema also recognizes only a fixed change-metadata field set (`schema:`, `created:`, the optional `goal:`/`affected_areas:`/`initiative:` from CLI 1.6, and `skip_specs:`/`retire_capabilities:`) and ignores every other key. Before CLI 1.14 unknown keys were silently stripped; since 1.14.0 `validate`, `status` and `instructions` **warn** about them (a failure under `--strict`), and from 1.14.2 lowercase `x-*` keys are exempt. Neither the exemption nor the warning changes the rule: publication is the load-bearing reason, so tracker IDs stay in the gitignored sidecar regardless of which fields upstream accepts.
**Do not put them in commit messages** — `git log` is public on GitHub.

**Check whether a file is public before saying so.** Run `git check-ignore -v <path>` and `git ls-files <path>`. A gitignored file is not published. A tracked file is published unless it sits in the allow-list in `.githooks/pre-push`. Don't assume either way.

The broader rule: nothing local-homelab-specific ever lands in artifacts that will reach GitHub. That includes Forgejo URLs (`forgejo.geek`, `johnb/intellij-openspec#N`), Plane identifiers (`OSP-N`, `OSPEC-N`), `*.geek` hostnames, homelab MCP server names (`mcp__homelab__*`), and the `johnb` username. Use vendor-neutral wording — "tracker entry", "the linked issue" — in any published surface (proposal/design/tasks/CHANGELOG/README/docs/code comments/commit messages). Before any commit, grep the staged files: `grep -nrE "forgejo|plane|geek|OSPEC|OSP-|johnb/" <staged>`.

> **Provenance note:** the storage convention here (sidecar over proposal.md) is project-local. The upstream OpenSpec CLI doesn't mandate a tracker convention — its proposal template defines `Why → What Changes → Capabilities → Impact` and is silent on tracker IDs entirely. 10 archived proposals from 2026-04-29 onward still carry inline `## References` lines as a vestige of the prior local convention; new proposals use the sidecar.

## If `openspec update` clobbers customizations

```bash
git checkout HEAD -- .claude/skills/ .claude/commands/
```

Since the 1.5.0 skills-only migration, `.claude/skills/openspec-*/` was the only *tracked* surface the CLI regenerated; **since adopting 1.8.0, `.claude/commands/opsx/` is a second tracked, CLI-regenerated surface** (the `opsx:*` experimental commands) — hence the checkout restores both. The other AI-tool mirrors (`.augment/`, `.codex/`, `.gemini/`, `.github/skills/`, `.github/prompts/`, `.junie/`, and their `commands` variants) are gitignored regenerated copies — clobbering there is harmless, and git can't (and needn't) restore them. The custom-named skills are gitignored too, so the checkout won't touch them.

## OpenSpec validation and CLI updates

- **The project must pass strict validation.** Before a PR that touches `openspec/`, and before a release, run `openspec validate --all --strict`. Strict treats every warning as a failure, so a clean default run is not enough.
- **Keep requirement text under 500 characters.** The CLI warns beyond that, and strict fails. Move examples and edge cases into scenarios, or split the requirement. A MODIFIED delta must carry every scenario of its requirement.
- **Change main specs only through a change.** Write the delta, validate it, then archive. Do not edit `openspec/specs/` directly.
- **Public specs stay vendor-neutral, including scenario titles.** Requirements, Purpose text and scenario titles in `openspec/specs/` must not name hosting or tracking services. A scenario title can't be renamed without renaming its requirement, so write neutral titles from the start.
- **Removing a capability.** Archive can't delete a capability. In the same PR, `git rm` the spec, then archive with `--skip-specs`. The `git rm` is part of the change's PR, so the main specs still change only through a change. A change with no spec deltas needs `skip_specs: true` in its `.openspec.yaml`.
- **Match the CLI to the target.** The installed CLI must equal `openspecTargetVersion` in `gradle.properties`. After a bump, run `openspec update` and restore customizations as above. Never use a pinned `npx` version for repo work (its cache leaks into captured fixtures).
- **Validate the real tree, not a scratch copy.** Run `openspec validate --all --strict` on the branch you're about to merge, and again after archiving. A scratch copy with deltas applied is not proof, because deltas reach the main specs only on archive, so a copy can pass while `main` still fails.
- **Tick a change's task list in the PR that completes the work.** Don't tick it in a separate batch PR. Batch PRs that each touch `tasks.md` conflict with one another, and every later PR then needs a conflict fix.

## Plugin-internal config fields — audit before "aligning" to upstream

We wrap upstream OpenSpec, so both its contract and the plugin's own are load-bearing. Before deleting a key from `openspec/config.yaml`, `plugin.xml`, `.openspec.yaml` or any project config because upstream ignores it, run `grep -rn "<key>" src/main/java/ src/test/java/`. A hit means it's plugin-internal: keep it, with an inline comment saying why upstream doesn't see it. Surface a divergence that is load-bearing on both sides before changing anything. Example: `version:` and `profile:` in `openspec/config.yaml` are read-only legacy — no longer written, still read — and the config-format axis behind them is a deliberately kept seam for a future format bump. Don't delete it to "simplify".

## Branches, pull requests and CI

- **Develop on `origin`; GitHub is a read-only mirror of `main`.** Non-trivial work: branch from `main` → push to `origin` → open a PR there → CI → self-merge. Trivial doc/tracker/comment tweaks may go direct to `main` on `origin`. **The user mirrors `main` to GitHub; the agent never pushes to the `github` remote** (a user-level hook refuses it). GitHub blocks force-push and deletion of public `main`.
- **Merge PRs with rebase** on `origin`, not a merge commit or squash, so internal PR numbers stay out of the public log. Afterwards delete the branch locally and on `origin`.
- **Bring a branch up to date by merging or rebasing `main`; never rebuild it from a diff.** A net diff against a stale base silently reverts work merged since. Merge `main` in before merging when an earlier PR touched the same file (usually `CHANGELOG.md`).
- **A PR whose head predates a workflow change can't merge**: its required check never ran on the new workflow. Merge `main` in and push. An empty commit doesn't start CI.
- **"Build & Verify" always reports, but skips its heavy steps for bypass-only PRs** (`openspec/**`, `CLAUDE.md`, `.claude/**`, `.githooks/**`, `.gitignore`, `LICENSE`, `ui-smoke.yaml`), so those go green in seconds. Docs aren't bypassed: `DocumentationHygieneTest` validates them.
- **Hooks** (`git config core.hooksPath .githooks`): `pre-push` vets pushes to the GitHub mirror for leaks; `commit-msg` blocks the same patterns in messages (`pre-push` never scans `git log`). Keep the two `PATTERN`s in sync and never weaken them back to `\b` — git grep ignores it.
- **Waiting on CI:** use one self-terminating wait command (a clone-local helper, when present) run in the background, not a hand-written polling loop. Treat "no run yet" as pending. Stop any wait when its PR merges, and check for leftovers with `ps` at session end.

## Testing — required, and tests must verify *real* behavior

OpenSpec's `tasks` rules already mandate tests for every change and that *"each test SHALL fail if the code it covers is broken."* Enforcement layers on top of that:

- **CI gate:** `./gradlew build` runs the suite plus a JaCoCo coverage **regression floor** (`jacocoTestCoverageVerification`, wired into `check`). A PR can't merge red. The floor is a backstop against backsliding — ratchet the minimums in `build.gradle.kts` upward as coverage grows; it is *not* a substitute for covering new code.
- **Local pre-push gate:** `.githooks/pre-push` runs `./gradlew test` when pushed commits touch `src/` (every remote except the post-merge GitHub mirror). Activate per clone with `git config core.hooksPath .githooks`. Emergency bypass: `git push --no-verify`.
- **Plugin Verifier pre-push gate (platform-API changes):** the same hook also runs `./gradlew verifyPlugin` when a push adds/changes a `com.intellij.*` reference in Java. This is load-bearing because `./gradlew build`/`test` compile against the build SDK and **cannot** catch API incompatibilities against the target IDEs — only the verifier can. A verify-only CI failure (an unresolved `PlatformProjectOpenProcessor.attachToProject(...)` that compiled locally but would `NoSuchMethodError` on 2024.2) is why this exists: catch it locally (~3–5 min once the IDE archives are cached) instead of on the slow CI verify job. Skip this gate alone with `SKIP_VERIFY_PLUGIN=1 git push`.

**The UI smoke suite runs locally, not in CI — do not try to move it to CI.** The release gate is the maintainer's local run: `caffeinate -dimsu ./gradlew uiSmoke`. Forgejo's `ui-smoke` workflow is kept only for manual dispatch. It has never passed on the runner: every recorded run since v0.4.0 has failed, and the IDE sandbox cannot run headfully there even with Xvfb on `:99` (the runner was checked against the homelab image on 2026-11-21). Re-opening CI automation needs a new decision, not another round of workflow fixes.

**Contract-test external output — don't hand-write the expected shape.** Any code that parses output from an external tool (the OpenSpec CLI's `--json`, file/registry formats on disk, an API response) MUST be tested against **captured real output**, not a hand-authored approximation of what you *think* the shape is. Hand-written fixtures encode your assumption, so the test passes while the parser is wrong — a green-but-vacuous test.

- Capture once from the real tool (for CLI state that needs setup, use an isolated `XDG_DATA_HOME` so the real global dir is untouched), **sanitize machine-specific paths**, and commit under `src/test/resources/fixtures/cli/`.
- Add a contract test that parses the fixture (see `CliContractTest` and `CoordinationContractTest`). When the tool's output format changes, re-capture the fixture and fix the failures.
- Incident that motivated this: the Phase 3 coordination parsers were unit-tested against inferred JSON and shipped three shape bugs (wrong artifact nesting, wrong doctor key, wrong fallback dir) that all passed CI. Contract-testing against the real CLI caught them immediately.

## Agent routing — consult the project subagents at their trigger points

Project subagents live in `.claude/agents/`. Their descriptions state when to invoke them; the failure mode to avoid is doing the work inline when a designated agent exists for it. Standing trigger points:

- **test-engineer** — PLAN mode at the *start* of implementing every OpenSpec change (fixture strategy, what only verifyPlugin/uiSmoke can catch); AUDIT mode alongside code review whenever a diff touches parsers of external output or adds tests with inline expected-shape literals.
- **openspec-guru** — before any design decision that turns on what upstream OpenSpec models or emits (CLI shapes, config schemas, lifecycle procedure). Rule of thumb: if the proposal would introduce a concept, verify upstream has it first.
- **jetbrains-platform-guru** — before implementing anything that touches new IntelliJ Platform APIs or extension points (feasibility on 2024.2+, threading/dumb-mode implications).
- **plugin-ui-specialist** — when a feature needs a UI home ("which surface tells the story") or a demo/walkthrough is being planned.
- **intellij-code-reviewer** — after the generic review, for any diff touching PSI/VFS/EDT/services/actions/inspections.
- **Workflow ↔ UI parity audit (openspec-guru + plugin-ui-specialist)**: on every CLI bump, before every release, and whenever a change touches a workflow-bound action. openspec-guru compares each upstream workflow's template (`dist/core/templates/workflows/*.js` in the CLI package) with the plugin action gated on it: same result in every delivery mode, matching name and description, correct gating. plugin-ui-specialist checks that each new or changed workflow is discoverable (menu, toolbar, Getting Started, empty states). `WorkflowParityContractTest` and `CoreWorkflowsContractTest` catch the *structural* drift (an upstream workflow with no gated action and no recorded reason, a misspelled id, a diverging core set). Only this audit catches *behavioural* drift, such as an action named after one workflow that does another's job. Each gap becomes a tracked issue. In release prep, a core workflow that no UI action reaches is a blocker unless the user records an exception.
- A gitignored, clone-local **project-management agent** may also exist (untracked — it references private infrastructure). When present: run its tracker/board **audit** after closing an epic item and before any release cut (`/release-prep` step 0 invokes it). Per-change tracker mechanics stay with the lifecycle skills, not the agent.
- **Fan-out rule of thumb.** Spawn a subagent at a listed trigger point, or for independent research across many files. Anything settled in about five tool calls, do inline. One agent per question; never two agents on the same question.

## Release & publishing

**Derive release state before proposing any version or release step** — never from memory or the resting `version = "..."` in `build.gradle.kts`, which is the *last shipped* release:

```bash
git tag -l 'v*' | sort -V | tail -1          # last SHIPPED version
git rev-list "$(git tag -l 'v*' | sort -V | tail -1)"..HEAD --count   # commits since (0 ⇒ nothing to release)
awk '/^## Unreleased/{f=1;next} /^## v/{f=0} f' CHANGELOG.md | grep -c '\S'   # Unreleased non-empty?
```

HEAD at the last tag with an empty `## Unreleased` means there is nothing to cut. Otherwise the next version is the user's semver choice, strictly greater than the last tag (never inferred).

- **Pipeline:** `/release-prep <v> --pre` → `/release-cut <v>` → merge → the user mirrors `main` → `/release-prep <v> --post` → tag both remotes. The BLOCKER gates run before the bump: tracker audit, the **Direct API provider gate** (every `AiProvider`, against *live* auth, release-notes and deprecation pages, shipped and planned changes, including request headers), the workflow ↔ UI parity audit, and **doc-fidelity** on the user-facing docs (`docs/marketplace-page.md`, `docs/feature-comparison-matrix.md`, `docs/feature-reference.md`). The skills hold the procedures and the incidents behind them.
- Never run `publishPlugin` locally. CI signs and publishes to the Marketplace on `v*` tag push.
- `CHANGELOG.md` is for plugin users only — no internal housekeeping, tracker triage, or personal workflow notes.

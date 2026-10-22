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

**Do not commit these skills back into tracked state.** If you need a tracked skill that does similar work, write a vendor-neutral one that takes config from environment / a separate file rather than hardcoding the homelab references.

## Tracker IDs go in a gitignored `.tracking.yaml` sidecar

Inside each change directory (`openspec/changes/<name>/`, and the archived form), tracker IDs live in a `.tracking.yaml` file. The file is gitignored so it never enters version control. `mirror-change-trackers` writes it; `close-change-trackers` reads it.

**Do not put tracker IDs in `proposal.md`, `design.md`, or `tasks.md`** — those files are published when the change archives.
**Do not put them in `.openspec.yaml`** — it is committed with the change and published when the change archives, so anything written there reaches the public mirror. Its upstream schema also recognizes only a fixed change-metadata field set (`schema:`, `created:`, the optional `goal:`/`affected_areas:`/`initiative:` from CLI 1.6, and `skip_specs:`/`retire_capabilities:`) and ignores every other key. Before CLI 1.14 unknown keys were silently stripped; since 1.14.0 `validate`, `status` and `instructions` **warn** about them (a failure under `--strict`), and from 1.14.2 lowercase `x-*` keys are exempt. Neither the exemption nor the warning changes the rule: publication is the load-bearing reason, so tracker IDs stay in the gitignored sidecar regardless of which fields upstream accepts.
**Do not put them in commit messages** — `git log` is public on GitHub.

The broader rule: nothing local-homelab-specific ever lands in artifacts that will reach GitHub. That includes Forgejo URLs (`forgejo.geek`, `johnb/intellij-openspec#N`), Plane identifiers (`OSP-N`, `OSPEC-N`), `*.geek` hostnames, homelab MCP server names (`mcp__homelab__*`), and the `johnb` username. Use vendor-neutral wording — "tracker entry", "the linked issue" — in any published surface (proposal/design/tasks/CHANGELOG/README/docs/code comments/commit messages). Before any commit, grep the staged files: `grep -nrE "forgejo|plane|geek|OSPEC|OSP-|johnb/" <staged>`.

> **Provenance note:** the storage convention here (sidecar over proposal.md) is project-local. The upstream OpenSpec CLI doesn't mandate a tracker convention — its proposal template defines `Why → What Changes → Capabilities → Impact` and is silent on tracker IDs entirely. 10 archived proposals from 2026-04-29 onward still carry inline `## References` lines as a vestige of the prior local convention; new proposals use the sidecar.

## If `openspec update` clobbers customizations

```bash
git checkout HEAD -- .claude/skills/ .claude/commands/
```

Since the 1.5.0 skills-only migration, `.claude/skills/openspec-*/` was the only *tracked* surface the CLI regenerated; **since adopting 1.8.0, `.claude/commands/opsx/` is a second tracked, CLI-regenerated surface** (the `opsx:*` experimental commands) — hence the checkout restores both. The other AI-tool mirrors (`.augment/`, `.codex/`, `.gemini/`, `.github/skills/`, `.github/prompts/`, `.junie/`, and their `commands` variants) are gitignored regenerated copies — clobbering there is harmless, and git can't (and needn't) restore them. The custom-named skills are gitignored too, so the checkout won't touch them.

## Plugin-internal config fields — audit before "aligning" to upstream

When you're tempted to remove a field from `openspec/config.yaml`, `plugin.xml`, `.openspec.yaml`, or any other config file because "upstream's schema doesn't accept it / strips it / doesn't mention it", **stop and grep this codebase for reads of that field first**. We are a *wrapper around upstream OpenSpec*; both the upstream contract and the plugin's own internal contract are load-bearing.

Concrete case — `version:` and the plugin-invented `profile:` in `openspec/config.yaml`: upstream's Zod schema (`@fission-ai/openspec`'s `project-config.ts`) ignores both, but the plugin *reads* them. They are **read-only-legacy**: absence is valid (it resolves to `VersionSupport.V1_2`), and the plugin no longer *writes* them when scaffolding (that shape now matches upstream `openspec init`). Two things stay true anyway — the *readers* remain (`OpenSpecSettings.getEffectiveVersion`'s config-format fallback and the tree-view `SpecTreeModel` nodes), because a legacy config on disk may still carry the fields; and the config-format axis is **deliberately retained as the seam for a future format bump — do not delete it to "simplify"**. Lesson: even after you stop *writing* a plugin-internal field, grep for its *readers* before removing them; "upstream strips it" is never sufficient reason to delete.

The general rule:
- Before deleting a key in any project config file, run `grep -rn "<key>" src/main/java/ src/test/java/`. If there are hits, the key is plugin-internal — keep it, even if upstream doesn't acknowledge it.
- If a field is genuinely plugin-internal-only, leave an inline comment on the field explaining why upstream doesn't see it.
- If you find an internal/upstream divergence that's load-bearing on both sides, surface it before changing — it's a candidate for either a plugin-side refactor (decouple from the upstream field) or upstream issue, not a quiet config edit.

## Branching & pull requests — develop on `origin`, mirror to GitHub

Default workflow (adopted 2026-06-27): non-trivial work goes through a **pull request on the Forgejo `origin` remote**, not a direct push to `main`.

- Branch from `main` → push the branch to `origin` → open a PR on `origin` → let CI run → self-merge → then mirror with `git push github main`.
- **GitHub is a read-only mirror** of `main`. Review/PRs live on `origin`. GitHub's classic branch protection no longer requires PRs (removed 2026-06-27 — you can't PR a mirror) but still blocks force-push and deletion of public `main`.
- The `pre-push` leak guard (`.githooks/pre-push`, activated via `git config core.hooksPath .githooks`) vets every push to the GitHub mirror. Never weaken its pattern back to `\b` — git grep ignores it.
- The `commit-msg` hook (same `core.hooksPath`) blocks the same patterns in commit messages — `pre-push` scans only the pushed tree, never `git log`. Keep the two `PATTERN`s in sync.
- **Trivial changes** (doc/tracker/comment tweaks) may still go direct to `main` on `origin` — use judgement.
- After a PR merges, delete the branch locally (`git branch -d`) and on `origin` (`git push origin --delete <name>`), per the standing workflow preference.

## Testing — required, and tests must verify *real* behavior

OpenSpec's `tasks` rules already mandate tests for every change and that *"each test SHALL fail if the code it covers is broken."* Enforcement layers on top of that:

- **CI gate:** `./gradlew build` runs the suite plus a JaCoCo coverage **regression floor** (`jacocoTestCoverageVerification`, wired into `check`). A PR can't merge red. The floor is a backstop against backsliding — ratchet the minimums in `build.gradle.kts` upward as coverage grows; it is *not* a substitute for covering new code.
- **Local pre-push gate:** `.githooks/pre-push` runs `./gradlew test` when pushed commits touch `src/` (every remote except the post-merge GitHub mirror). Activate per clone with `git config core.hooksPath .githooks`. Emergency bypass: `git push --no-verify`.
- **Plugin Verifier pre-push gate (platform-API changes):** the same hook also runs `./gradlew verifyPlugin` when a push adds/changes a `com.intellij.*` reference in Java. This is load-bearing because `./gradlew build`/`test` compile against the build SDK and **cannot** catch API incompatibilities against the target IDEs — only the verifier can. A verify-only CI failure (an unresolved `PlatformProjectOpenProcessor.attachToProject(...)` that compiled locally but would `NoSuchMethodError` on 2024.2) is why this exists: catch it locally (~3–5 min once the IDE archives are cached) instead of on the slow CI verify job. Skip this gate alone with `SKIP_VERIFY_PLUGIN=1 git push`.

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
- A gitignored, clone-local **project-management agent** may also exist (untracked — it references private infrastructure). When present: run its tracker/board **audit** after closing an epic item and before any release cut (`/release-prep` step 0 invokes it). Per-change tracker mechanics stay with the lifecycle skills, not the agent.

## Release & publishing

**Release-state preflight — do this BEFORE proposing any release step or version number.** Never infer release state from memory, a memory-index hook, or the resting `version = "..."` in `build.gradle.kts` — that value is the *last shipped* release, not the next one. Derive it from git and the changelog every time:

```bash
git tag -l 'v*' | sort -V | tail -1          # last SHIPPED version
git rev-list "$(git tag -l 'v*' | sort -V | tail -1)"..HEAD --count   # commits since (0 ⇒ nothing to release)
awk '/^## Unreleased/{f=1;next} /^## v/{f=0} f' CHANGELOG.md | grep -c '\S'   # Unreleased non-empty?
```

If HEAD is at the last tag and `## Unreleased` is empty, there is nothing to cut — say so. Otherwise the next version is the user's semver choice **strictly greater than the last tag** (the minor-vs-patch call is theirs, never inferred). If the last tag already equals the version you were about to prep, STOP — that release shipped; the pending work is the *next* one.

**Doc-fidelity is a precondition, not a mid-cut afterthought.** Before *suggesting* the release process, run the doc-fidelity gate over the user-facing surfaces (`docs/marketplace-page.md`, `docs/feature-comparison-matrix.md`, `docs/feature-reference.md`) against the `## Unreleased` feature set, and land any refresh as its own doc commit. (`/release-cut` also gates on this at step 3, but surfacing stale user-facing docs *before* the cut is proposed avoids a late scramble — this is why the v0.5.0 pass was nearly skipped.)

**Direct API provider gate — every release, every provider, no exceptions.** Before *suggesting* the release process, have `openspec-guru` audit **every provider in `AiProvider`** (today Anthropic, OpenAI, Google) against that provider's **live** docs: its authentication page, its API release notes / changelog **since the last tag's date**, and its deprecation / retirement pages. "Live" means fetched now. A skill reference, memory, or a prior audit doesn't count. It includes a **per-provider request-header audit**: every required or conditionally required header for each key type (workspace, organization and project selectors, versioning, the recommended auth header), whether the plugin sends it, and what that means for the Direct API settings form. It covers both **shipped** changes (new required headers, auth/key models, endpoint or parameter changes, retired models) and **announced or planned** ones (deprecations, sunset dates, scheduled header or version changes) that land before the *next* release is likely to ship. Each gap becomes a tracked issue. A gap that breaks a supported key type or the default model is a **BLOCKER**. Everything else is fixed in this release or scheduled into the next one by an explicit user decision. This runs whether or not the release touched Direct API code, because providers change underneath the plugin. Incident: Anthropic's 2026-08-27 identity-backed API keys made `anthropic-workspace-id` mandatory for keys not scoped to a workspace. Two releases shipped without the plugin sending it, because the only audit checked a reference copy, not the live auth docs, and no release step required a provider check. `/release-cut` step 3b and `/release-prep` step 0b enforce this gate.

- Never run `publishPlugin` locally. CI handles signing and JetBrains Marketplace publishing on `v*` tag push.
- Use `/release-cut <version>` to start a release — it bumps `build.gradle.kts`, rolls `## Unreleased` into a versioned changelog section (`./gradlew patchChangelog`), and opens the release PR.
- Use `/release-prep <version>` before tagging — it validates `build.gradle.kts`, `CHANGELOG.md`, build, archived changes, and tracker state.
- `CHANGELOG.md` is for plugin users only — no internal housekeeping, tracker triage, or personal workflow notes.

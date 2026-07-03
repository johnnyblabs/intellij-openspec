# intellij-openspec — agent conventions

Project-specific rules for AI agents working in this repo.

## Tracker mirroring — invoke the custom skills, never inline curl

OpenSpec changes are mirrored to project trackers via project-level custom skills. The three lifecycle skills keep the Plane card flowing **Todo → In Progress → Done** in step with the change, so it stays legible in Kanban/cycle/board views at every stage:

| When | Skill | Plane state |
|---|---|---|
| After `openspec-propose` or `openspec-new-change` | `/mirror-change-trackers <name>` | → Todo |
| At the start of `openspec-apply-change` (implementation begins) | `/advance-change-trackers <name>` | → In Progress |
| After `openspec-archive-change` | `/close-change-trackers <archive-dir>` | → Done |

Without the middle step the card jumps Todo→Done and never appears in the "In Progress" column while work is actually happening. Forgejo issues have no In-Progress state (open/closed only), so `advance-change-trackers` touches Plane only and leaves the issue open.

**Why these skills are custom-named, not inside `openspec-*`:** the `openspec` CLI manages `.claude/skills/openspec-*/SKILL.md` and rewrites them on every `openspec update`. Custom-named skills (`mirror-change-trackers`, `advance-change-trackers`, `close-change-trackers`, `release-prep`) live outside that managed surface and survive updates.

**These four skills are gitignored** (`.gitignore` entries `.claude/skills/{mirror,advance,close}-change-trackers/` and `.claude/skills/release-prep/`). They intrinsically reference `forgejo.geek`, the Plane project UUID, `mcp__homelab__*` server-tool names, and `johnb/intellij-openspec` — things that violate the anti-leak rule below if they reach the public GitHub mirror. The skill files exist on disk and work in any local session; they just don't ride to git history. Edits to these skills are local-only.

**Do not put tracker plumbing back into the `openspec-*` skills.** If you find yourself tempted, you're at the wrong layer.

**Do not commit these skills back into tracked state.** If you need a tracked skill that does similar work, write a vendor-neutral one that takes config from environment / a separate file rather than hardcoding the homelab references.

## Tracker IDs go in a gitignored `.tracking.yaml` sidecar

Inside each change directory (`openspec/changes/<name>/`, and the archived form), tracker IDs live in a `.tracking.yaml` file. The file is gitignored so it never enters version control. `mirror-change-trackers` writes it; `close-change-trackers` reads it.

**Do not put tracker IDs in `proposal.md`, `design.md`, or `tasks.md`** — those files are published when the change archives.
**Do not put them in `.openspec.yaml`** — its upstream Zod schema only accepts `schema:` and `created:` and silently strips unknown keys.
**Do not put them in commit messages** — `git log` is public on GitHub.

The broader rule: nothing local-homelab-specific ever lands in artifacts that will reach GitHub. That includes Forgejo URLs (`forgejo.geek`, `johnb/intellij-openspec#N`), Plane identifiers (`OSP-N`, `OSPEC-N`), `*.geek` hostnames, homelab MCP server names (`mcp__homelab__*`), and the `johnb` username. Use vendor-neutral wording — "tracker entry", "the linked issue" — in any published surface (proposal/design/tasks/CHANGELOG/README/docs/code comments/commit messages). Before any commit, grep the staged files: `grep -nrE "forgejo|plane|geek|OSPEC|OSP-|johnb/" <staged>`.

> **Provenance note:** the storage convention here (sidecar over proposal.md) is project-local. The upstream OpenSpec CLI doesn't mandate a tracker convention — its proposal template defines `Why → What Changes → Capabilities → Impact` and is silent on tracker IDs entirely. 10 archived proposals from 2026-04-29 onward still carry inline `## References` lines as a vestige of the prior local convention; new proposals use the sidecar.

## If `openspec update` clobbers customizations

```bash
git checkout HEAD -- .augment/ .claude/ .codex/ .gemini/ .github/
```

After the migration to custom skills landed, the `openspec-*` files match what the CLI regenerates, so this should be a quieter recovery than it used to be.

## Plugin-internal config fields — audit before "aligning" to upstream

When you're tempted to remove a field from `openspec/config.yaml`, `plugin.xml`, `.openspec.yaml`, or any other config file because "upstream's schema doesn't accept it / strips it / doesn't mention it", **stop and grep this codebase for reads of that field first**. We are a *wrapper around upstream OpenSpec*; both the upstream contract and the plugin's own internal contract are load-bearing.

Concrete known case (incident 2026-06-15, commit `c34c7b2`): `version:` in `openspec/config.yaml` is silently stripped by upstream's Zod schema (`@fission-ai/openspec`'s `project-config.ts`) but is REQUIRED by the plugin's own `BuiltInValidator.validateConfig` — without it, the validator emits `config-version-required` WARNING + `config-field-required` ERROR on the plugin's own config file. `OpenSpecSettings.getEffectiveVersion` falls back to reading this field when no Settings override is set. Removing it broke the plugin's self-validation for ~24 hours until restored.

The general rule:
- Before deleting a key in any project config file, run `grep -rn "<key>" src/main/java/ src/test/java/`. If there are hits, the key is plugin-internal — keep it, even if upstream doesn't acknowledge it.
- If a field is genuinely plugin-internal-only, leave an inline comment on the field explaining why upstream doesn't see it.
- If you find an internal/upstream divergence that's load-bearing on both sides, surface it before changing — it's a candidate for either a plugin-side refactor (decouple from the upstream field) or upstream issue, not a quiet config edit.

## Branching & pull requests — develop on `origin`, mirror to GitHub

Default workflow (adopted 2026-06-27): non-trivial work goes through a **pull request on the Forgejo `origin` remote**, not a direct push to `main`.

- Branch from `main` → push the branch to `origin` → open a PR on `origin` → let CI run → self-merge → then mirror with `git push github main`.
- **GitHub is a read-only mirror** of `main`. Review/PRs live on `origin`. GitHub's classic branch protection no longer requires PRs (removed 2026-06-27 — you can't PR a mirror) but still blocks force-push and deletion of public `main`.
- The `pre-push` leak guard (`.githooks/pre-push`, activated via `git config core.hooksPath .githooks`) vets every push to the GitHub mirror. Never weaken its pattern back to `\b` — git grep ignores it.
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

## Release & publishing

- Never run `publishPlugin` locally. CI handles signing and JetBrains Marketplace publishing on `v*` tag push.
- Use `/release-prep <version>` before tagging — it validates `build.gradle.kts`, `CHANGELOG.md`, build, archived changes, and tracker state.
- `CHANGELOG.md` is for plugin users only — no internal housekeeping, tracker triage, or personal workflow notes.

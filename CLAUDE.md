# intellij-openspec — agent conventions

Project-specific rules for AI agents working in this repo.

## Tracker mirroring — invoke the custom skills, never inline curl

OpenSpec changes are mirrored to project trackers via two project-level custom skills:

| When | Skill |
|---|---|
| After `openspec-propose` or `openspec-new-change` | `/mirror-change-trackers <name>` |
| After `openspec-archive-change` | `/close-change-trackers <archive-dir>` |

**Why these skills are custom-named, not inside `openspec-*`:** the `openspec` CLI manages `.claude/skills/openspec-*/SKILL.md` and rewrites them on every `openspec update`. Custom-named skills (`mirror-change-trackers`, `close-change-trackers`, `release-prep`) live outside that managed surface and survive updates.

**Do not put tracker plumbing back into the `openspec-*` skills.** If you find yourself tempted, you're at the wrong layer.

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

## Release & publishing

- Never run `publishPlugin` locally. CI handles signing and JetBrains Marketplace publishing on `v*` tag push.
- Use `/release-prep <version>` before tagging — it validates `build.gradle.kts`, `CHANGELOG.md`, build, archived changes, and tracker state.
- `CHANGELOG.md` is for plugin users only — no internal housekeeping, tracker triage, or personal workflow notes.

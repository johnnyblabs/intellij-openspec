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

**Do not put tracker IDs in `proposal.md`** — that file is published.
**Do not put them in `.openspec.yaml`** — its upstream Zod schema only accepts `schema:` and `created:` and silently strips unknown keys.

> **Provenance note:** the storage convention here (sidecar over proposal.md) is project-local. The upstream OpenSpec CLI doesn't mandate a tracker convention — its proposal template defines `Why → What Changes → Capabilities → Impact` and is silent on tracker IDs entirely. 10 archived proposals from 2026-04-29 onward still carry inline `## References` lines as a vestige of the prior local convention; new proposals use the sidecar.

## If `openspec update` clobbers customizations

```bash
git checkout HEAD -- .augment/ .claude/ .codex/ .gemini/ .github/
```

After the migration to custom skills landed, the `openspec-*` files match what the CLI regenerates, so this should be a quieter recovery than it used to be.

## Release & publishing

- Never run `publishPlugin` locally. CI handles signing and JetBrains Marketplace publishing on `v*` tag push.
- Use `/release-prep <version>` before tagging — it validates `build.gradle.kts`, `CHANGELOG.md`, build, archived changes, and tracker state.
- `CHANGELOG.md` is for plugin users only — no internal housekeeping, tracker triage, or personal workflow notes.

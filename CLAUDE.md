# intellij-openspec — agent conventions

Project-specific rules for AI agents working in this repo. The homelab-ecosystem rules from `~/.claude/CLAUDE.md` apply on top of this file.

## Tracker mirroring — invoke the custom skills, never inline curl

OpenSpec changes are mirrored to two trackers: Forgejo (`johnb/intellij-openspec`) and Plane (`OSPEC` project on the openspec workspace). The plumbing lives in **two project-level custom skills**, NOT in the `openspec-*` skills:

| When | Skill to invoke | What it does |
|---|---|---|
| After `openspec-propose` or `openspec-new-change` | `/mirror-change-trackers <name>` | Opens Forgejo issue + Plane card in one MCP call. Appends `## References` to `proposal.md`. |
| After `openspec-archive-change` | `/close-change-trackers <archive-dir>` | Closes Forgejo issue with completion comment + moves Plane card to Done. |

**Why these skills exist as custom-named files (not inside `openspec-*`):** the `openspec` CLI manages `.claude/skills/openspec-*/SKILL.md` and rewrites them on every `openspec update`. Custom-named skills (`mirror-change-trackers`, `close-change-trackers`, `release-prep`) live outside that managed surface and survive updates. See the architectural lesson in `~/.claude/projects/-Users-johnboyce-working-intellij-openspec/memory/feedback_openspec_skill_customization_layering.md`.

**Do not put tracker plumbing back into the `openspec-*` skills.** If you find yourself tempted, you're at the wrong layer.

## If `openspec update` clobbers customizations

The recovery command:

```bash
git checkout HEAD -- .augment/ .claude/ .codex/ .gemini/ .github/
```

This restores all CLI-managed skill files to the last-committed state. After the mirror/close migration landed, the `openspec-*` skills match what `openspec update` regenerates, so this should be a quieter recovery than it used to be — but keep the command close.

## Tracker IDs go in `proposal.md`, never `.openspec.yaml`

`.openspec.yaml`'s upstream Zod schema only accepts `schema:` and `created:` — it silently strips unknown keys. Embedded `tracking:` blocks vanish. Use a `## References` section at the bottom of `proposal.md` instead. The mirror skill appends this automatically; the close skill reads from it.

## Release & publishing

- Never run `publishPlugin` locally. CI handles signing and JetBrains Marketplace publishing on `v*` tag push.
- Use `/release-prep <version>` before tagging — it validates `build.gradle.kts`, `CHANGELOG.md`, build, archived changes, and tracker state.
- `CHANGELOG.md` is for plugin users only — no internal housekeeping, tracker triage, or personal workflow notes.

## Memory pointers

Persistent learnings live in `~/.claude/projects/-Users-johnboyce-working-intellij-openspec/memory/`. The index (`MEMORY.md`) is auto-loaded each session. Relevant entries for this repo's day-to-day:

- `feedback_openspec_skill_customization_layering.md` — the architectural rule above, expanded
- `feedback_openspec_tracking.md` — why tracker IDs live in `proposal.md` not `.openspec.yaml`
- `feedback_plane_assignees_required.md` — MCP handles this, but if you ever do inline Plane calls, set `assignees`
- `feedback_plane_workspace_vs_project_membership.md` — Plane workspace-admin ≠ project access
- `reference_homelab_mcp_ospec_route.md` — what the MCP tools provide for the `OSPEC` project
- `reference_plane_user_id.md` — John's Plane user ID and project IDs

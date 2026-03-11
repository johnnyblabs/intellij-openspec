## Context

Post-archive steps (commit, push, update issue trackers) are defined in Claude Code's `MEMORY.md` — a tool-specific file invisible to Copilot, Cursor, and other AI tools. The project convention says "every change MUST use OpenSpec," but this critical workflow step lives outside OpenSpec.

OpenSpec has two mechanisms for expressing conventions:
1. **`config.yaml` rules** — freeform strings read by AI tools via `openspec instructions`
2. **Specs** — formal requirements with testable scenarios

Both are tool-agnostic: any AI tool that reads the OpenSpec project structure gets the same information.

## Goals / Non-Goals

**Goals:**
- Define post-archive workflow as an OpenSpec rule in `config.yaml`
- Create a formal spec with testable scenarios for the behavior
- Update all AI tool instruction files (archive commands/prompts/skills) to include post-archive steps
- Remove the Claude-specific checklist from `MEMORY.md`

**Non-Goals:**
- Automating the post-archive steps in the plugin (that's the plugin's issue tracking feature)
- Forking or extending the OpenSpec schema (the `spec-driven` schema doesn't need changes)
- Changing how `openspec archive` CLI works (that's upstream)

## Decisions

### 1. Add a rule to config.yaml

Add a `post-archive` rule:
```yaml
rules:
  post-archive: "After archiving a change, the tool SHALL commit all changes, push to remote, close the related Forgejo issue, and move the Plane work item to Done"
```

Rules are included in `openspec instructions` output, so any AI tool generating or applying artifacts sees them. This is the simplest, most portable mechanism.

### 2. Update all archive instruction files identically

All four file sets get the same post-archive steps added after step 6 (Display summary):
- `.claude/commands/opsx/archive.md`
- `.claude/skills/openspec-archive-change/SKILL.md`
- `.github/prompts/opsx-archive.prompt.md`
- `.github/skills/openspec-archive-change/SKILL.md`

The new step 7:
```
7. **Post-archive: commit, push, update trackers**
   - Commit all changes (implementation, archived change, synced specs)
   - Push to remote
   - Close the related Forgejo issue with a completion comment
   - Move the related Plane work item to "Done" state
   - If no matching issue/work item exists, skip silently
```

### 3. Replace MEMORY.md checklist with spec reference

Remove the "Post-Archive Checklist (MANDATORY)" section from `MEMORY.md` and replace with:
```
- Post-archive workflow: defined in `openspec/specs/post-archive-workflow/spec.md` and `config.yaml` rules
```

This keeps MEMORY.md as a pointer, not a source of truth.

## Risks / Trade-offs

- **[Rule visibility]** → Rules are freeform strings, not enforced. AI tools may ignore them. Mitigation: the archive instruction files explicitly include the steps, so tools following the archive workflow will see them.
- **[Tracker credentials]** → Post-archive steps need API access to Forgejo/Plane. Not all sessions will have credentials available. Mitigation: instruction says "skip silently" if no matching issue exists.

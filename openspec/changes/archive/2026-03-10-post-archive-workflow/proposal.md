## Why

Post-archive steps (commit, push, update issue trackers) are currently defined only in Claude Code's memory file (`MEMORY.md`), making them invisible to other AI tools like Copilot, Cursor, and Windsurf. This violates the project's principle that all conventions should be defined the OpenSpec way — tool-agnostic and spec-driven. Any AI tool reading the project's OpenSpec config and specs should know what to do after archiving a change.

## What Changes

- Add a `post-archive` rule to `openspec/config.yaml` that declares the post-archive steps as a project convention
- Create a formal spec defining the post-archive workflow behavior with testable scenarios
- Remove the Claude-specific post-archive checklist from `MEMORY.md` and replace it with a reference to the OpenSpec spec
- Update all AI tool instruction files (`copilot-instructions.md`, `.claude/commands/opsx/archive.md`, `.github/prompts/opsx-archive.prompt.md`) to reference the OpenSpec-native convention

## Capabilities

### New Capabilities
- `post-archive-workflow`: Formal specification of what happens after a change is archived — commit, push, update Forgejo issues, update Plane work items

### Modified Capabilities
- `plugin-core`: Add the post-archive rule to config.yaml conventions

## Impact

- `openspec/config.yaml` — new rule entry
- `MEMORY.md` — remove Claude-specific post-archive checklist, add reference to spec
- `.github/copilot-instructions.md` — add post-archive convention reference
- `.claude/commands/opsx/archive.md` — add post-archive steps
- `.github/prompts/opsx-archive.prompt.md` — add post-archive steps

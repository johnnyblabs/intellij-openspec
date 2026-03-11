## 1. Add post-archive rule to config.yaml

- [x] 1.1 Add `post-archive` rule to `openspec/config.yaml` rules section

## 2. Update archive instruction files with post-archive steps

- [x] 2.1 Add step 7 (post-archive: commit, push, update trackers) to `.claude/commands/opsx/archive.md`
- [x] 2.2 Add step 7 to `.claude/skills/openspec-archive-change/SKILL.md`
- [x] 2.3 Add step 7 to `.github/prompts/opsx-archive.prompt.md`
- [x] 2.4 Add step 7 to `.github/skills/openspec-archive-change/SKILL.md`

## 3. Update copilot-instructions.md

- [x] 3.1 Add post-archive convention to the project conventions section in `.github/copilot-instructions.md`

## 4. Clean up MEMORY.md

- [x] 4.1 Remove the Claude-specific "Post-Archive Checklist (MANDATORY)" section from MEMORY.md
- [x] 4.2 Add a reference to the OpenSpec spec: "Post-archive workflow defined in openspec/specs/post-archive-workflow/spec.md and config.yaml rules"

## 5. Add tracker API details to archive instruction files

- [x] 5.1 Expand step 7 in all four archive files with Forgejo and Plane API details (credentials location, API patterns, example commands)

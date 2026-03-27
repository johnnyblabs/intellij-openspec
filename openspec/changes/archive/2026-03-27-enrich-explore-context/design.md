## Context

`ExploreContextService.assembleContext()` produces a Markdown document with four sections: Project Config (version + schema), Detected AI Tools, Active Changes (name + truncated proposal), and Specs (domain names only). This is too sparse for an AI tool to understand the project.

## Goals / Non-Goals

**Goals:**
- Make the assembled context rich enough that an AI tool can understand the project's structure, requirements, and active work
- Include config context/rules, spec requirement summaries, and full change artifacts
- Keep the output readable and well-structured

**Non-Goals:**
- Changing the Explore panel UI (it already displays whatever the service produces)
- Adding interactive explore mode (that's an upstream CLI concept)
- Including archived changes (only active changes matter for exploration)

## Decisions

### Include config context and rules inline

Add `context` and `rules` from `OpenSpecConfig` to the Project Config section. Context as a quoted block, rules as a bulleted list. These fields describe the project's purpose and constraints — essential for an AI to understand scope.

### Show requirement names and descriptions per spec domain

For each domain in `openspec/specs/`, parse `spec.md` and extract `### Requirement: <name>` headers with their description paragraph (the text between the header and the first scenario). List these under each domain. Full scenario content is too verbose — requirement descriptions are the right level of detail.

### Include full change artifacts

Replace the truncated proposal approach with reading all artifact files for each active change. Read `proposal.md`, `design.md`, `tasks.md`, and delta specs from `specs/`. Present each under a sub-heading. Missing artifacts are silently skipped.

### No size cap

Remove the 500-character truncation. The assembled context may be longer, but AI tools have large context windows and benefit from completeness. The Explore panel's scroll and copy-to-clipboard UX handles any length.

## Risks / Trade-offs

- **Longer output**: Projects with many specs and active changes will produce larger context. Acceptable — completeness is more valuable than brevity for AI consumption.
- **File I/O during assembly**: Reading all spec files and change artifacts adds I/O. Mitigated by existing pooled-thread execution in `ExplorePanel.refresh()`.

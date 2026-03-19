## Context

The plugin repository is being open-sourced under Apache 2.0 at `github.com/johnnyblabs/intellij-openspec`. The `open-source-licensing` change handles the rename, license, audit, and first push. This change layers on community infrastructure so the repo is welcoming and well-organized from the first public commit.

## Goals / Non-Goals

**Goals:**
- Make the repo approachable for first-time visitors and potential contributors
- Establish clear processes for bug reports, feature requests, and PRs
- Provide a public project board separate from the private Plane board
- Add a public CI badge so visitors can see build health

**Non-Goals:**
- Actively recruiting contributors (can grow organically)
- CLA (Contributor License Agreement) setup — overkill for now
- Automated release pipelines on GitHub (Forgejo handles releases)
- GitHub Pages or a docs site

## Decisions

### Contributor Covenant for Code of Conduct
The Contributor Covenant v2.1 is the de facto standard for open-source projects. It's adopted by thousands of projects including JetBrains' own repos. No reason to write a custom one.

### Minimal GitHub Actions CI over full pipeline mirror
A single `build.yml` that runs `gradle build` on PRs is enough to show a green badge and validate external contributions. Full signing, verification, and artifact publishing stays on Forgejo. This avoids duplicating CI complexity.

### GitHub Project board over GitHub Issues milestones
A Kanban-style project board (Backlog → In Progress → Done) is more visual and easier for community members to understand than milestones. Use it for public-facing work only — internal planning stays on Plane.

### Labels aligned with Forgejo
Use the same label names as Forgejo (`bug`, `enhancement`, `documentation`, etc.) so issues can be cross-referenced without confusion.

## Risks / Trade-offs

- **Risk: Maintaining two issue trackers** → Mitigate by triaging GitHub issues into Plane work items. GitHub is the intake, Plane is the workbench.
- **Risk: GitHub Actions costs** → Free tier is generous (2,000 minutes/month). A single `gradle build` job per PR is well within limits.
- **Risk: Stale project board** → Keep it simple — only move cards when work actually ships. Don't over-track.

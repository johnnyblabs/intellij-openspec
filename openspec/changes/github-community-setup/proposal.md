## Why

The plugin is going open-source on GitHub (`johnnyblabs/intellij-openspec`). A bare repo with just code and a license doesn't invite contributions or build trust. Setting up community infrastructure from day one — issue templates, contribution guidelines, code of conduct, project board — signals a well-maintained project and reduces friction for anyone who wants to engage.

## What Changes

- Add `CONTRIBUTING.md` with dev setup, PR process, and coding standards
- Add `CODE_OF_CONDUCT.md` (Contributor Covenant)
- Add `SECURITY.md` with vulnerability reporting instructions
- Add GitHub issue templates: bug report, feature request
- Add pull request template with checklist
- Configure GitHub repository: labels, topics, branch protection, Discussions
- Set up a GitHub Project board for public work tracking
- Add optional GitHub Actions CI for public build status badge
- Add README badges (build status, Marketplace version, license)

## Capabilities

### New Capabilities

_None_ — this is a community/infrastructure change, not a functional change.

### Modified Capabilities

_None_ — no spec-level behavior changes.

## Impact

- `.github/ISSUE_TEMPLATE/bug_report.md` — new issue template
- `.github/ISSUE_TEMPLATE/feature_request.md` — new issue template
- `.github/PULL_REQUEST_TEMPLATE.md` — new PR template
- `CONTRIBUTING.md` — new file at project root
- `CODE_OF_CONDUCT.md` — new file at project root
- `SECURITY.md` — new file at project root
- `README.md` — add badges and update for public audience
- `.github/workflows/build.yml` — optional GitHub Actions CI (mirror of Forgejo workflow)
- GitHub repository settings — labels, topics, branch protection, Discussions

# Explore context fixtures (OpenSpec CLI 1.15.0)

These are captured with `scripts/capture-explore-context-fixtures.py`. The script builds one project with an isolated HOME and XDG dirs. The project has:

- `openspec/specs/billing/spec.md`, a top-level capability;
- `openspec/specs/identity/user-auth/spec.md`, a nested capability;
- `changes/add-mfa`, a spec-driven change with every artifact and a nested delta (`specs/identity/user-auth/spec.md`);
- `changes/research-sso`, on a forked `research-first` schema with an extra `research` artifact (`research/**/*.md`). Its files are `research/summary.md` and `research/vendors/okta.md`, plus `proposal.md`.

The files are:

- `list-specs.json`: `openspec list --specs --json`. The ids are `billing` and `identity/user-auth`.
- `status-add-mfa.json`, `status-research-sso.json`: `openspec status --change <name> --json`. `artifactPaths` keys follow the schema's declaration order, and each `existingOutputPaths` is absolute and sorted.
- `status-missing.json`: the error envelope for a change that doesn't exist.

The project path is replaced with `/fixture/project`. Re-capture when the CLI version changes, then fix whatever the contract tests report.

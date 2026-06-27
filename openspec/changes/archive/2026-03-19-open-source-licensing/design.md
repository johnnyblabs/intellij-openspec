## Context

The plugin is currently hosted on a private Forgejo instance with no public source code and no license file. JetBrains Marketplace approval requires either a proprietary EULA or an open-source license with a link to source. The "OpenSpec" trademark (USPTO 97326504) is dead/abandoned as of July 2024, so the name is clear.

## Goals / Non-Goals

**Goals:**
- Meet JetBrains Marketplace EULA requirement with Apache 2.0
- Publish source to GitHub so Marketplace listing can link to it
- Keep Forgejo as the primary development remote (CI, issues, PRs)
- Ensure no secrets, credentials, or private configuration leak to the public repo

**Non-Goals:**
- Accepting community contributions (can enable later, not required now)
- Moving CI/CD to GitHub Actions (Forgejo remains primary)
- Dual-licensing or CLA setup

## Decisions

### License: Apache 2.0 over MIT
Apache 2.0 includes an explicit patent grant protecting both author and users. It's what JetBrains uses for their plugin template and is standard in the IntelliJ ecosystem. MIT is simpler but lacks patent protection.

### GitHub as mirror, Forgejo as primary
Push to both remotes. Forgejo stays the source of truth for CI, issues, and PRs. GitHub serves as the public-facing source link for the Marketplace listing. This avoids migrating any infrastructure.

### Git multi-remote setup over GitHub mirror feature
Using `git remote add github <url>` and pushing manually (or via a post-push hook) is simpler and more controllable than GitHub's mirror feature, which requires API tokens and has sync delays.

### Pre-publish audit over continuous scanning
Before the first public push, do a one-time audit of `.gitignore`, git history, and environment files to ensure nothing sensitive is exposed. No need for automated secret scanning at this scale.

## Risks / Trade-offs

- **Risk: Secrets in git history** → Audit git log for any committed `.env`, credentials, or tokens before first public push. If found, rewrite history or start fresh.
- **Risk: Forgejo-specific URLs in docs/code** → Internal hostnames are in scripts and memory files. These are harmless to expose (not reachable externally) but look unprofessional. Review and document as "internal development" where they appear.
- **Risk: Maintenance burden of two remotes** → Mitigated by only pushing to GitHub on releases or significant milestones, not every commit.

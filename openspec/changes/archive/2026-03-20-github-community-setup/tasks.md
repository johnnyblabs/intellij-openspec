## 1. Community Files

- [x] 1.1 Add `CONTRIBUTING.md` — dev setup (JDK 21, Gradle 9, IntelliJ 2024.2+), coding standards, PR process, branch naming conventions
- [x] 1.2 Add `CODE_OF_CONDUCT.md` — Contributor Covenant v2.1
- [x] 1.3 Add `SECURITY.md` — vulnerability reporting via email (not public issues)

## 2. Issue & PR Templates

- [x] 2.1 Create `.github/ISSUE_TEMPLATE/bug_report.md` — environment, steps to reproduce, expected vs actual behavior
- [x] 2.2 Create `.github/ISSUE_TEMPLATE/feature_request.md` — problem, proposed solution, alternatives
- [x] 2.3 Create `.github/ISSUE_TEMPLATE/config.yml` — optional: add link to Discussions for questions
- [x] 2.4 Create `.github/PULL_REQUEST_TEMPLATE.md` — description, testing checklist, documentation checklist

## 3. GitHub Actions CI

- [ ] 3.1 Create `.github/workflows/build.yml` — minimal CI: checkout, JDK 21 setup, `gradle build`, on push/PR to main
- [ ] 3.2 Verify build passes on GitHub Actions after first push

## 4. README Updates

- [x] 4.1 Add badges to `README.md` — license, Marketplace version, build status
- [x] 4.2 Rewrite README for a public audience — remove internal dev references, add screenshots, link to CONTRIBUTING.md
- [x] 4.3 Add "Getting Started" section for users (install from Marketplace)
- [x] 4.4 Add "Development" section for contributors (link to CONTRIBUTING.md)

## 5. GitHub Repository Configuration (manual)

- [ ] 5.1 Add repository topics: `intellij-plugin`, `jetbrains`, `openspec`, `spec-driven-development`, `ai-tools`
- [ ] 5.2 Enable Discussions (General, Q&A, Ideas categories)
- [ ] 5.3 Set up branch protection on `main` — require PR, require status checks
- [ ] 5.4 Create GitHub Project board (Kanban: Backlog, In Progress, Done)
- [ ] 5.5 Add labels: `bug`, `enhancement`, `documentation`, `good first issue`, `help wanted`, `question`, `wontfix`
- [ ] 5.6 Add repository description: "IntelliJ IDEA plugin for the OpenSpec spec-driven development framework"

## 1. Tracker Creation in Propose/New Skills

- [x] 1.1 Add tracker creation step to `.claude/skills/openspec-propose/SKILL.md` — after change creation and proposal writing, source `scripts/.env`, create Forgejo issue + Plane work item via curl, append `tracking` section to `.openspec.yaml`
- [x] 1.2 Add tracker creation step to `.claude/skills/openspec-new-change/SKILL.md` — after change directory creation, same tracker flow as propose
- [x] 1.3 Mirror propose skill changes to `.augment/`, `.codex/`, `.gemini/`, `.github/` skill directories
- [x] 1.4 Mirror new-change skill changes to `.augment/`, `.codex/`, `.gemini/`, `.github/` skill directories

## 2. Tracker Closure in Archive Skill

- [x] 2.1 Add tracker closure step to `.claude/skills/openspec-archive-change/SKILL.md` — before archive summary, read `tracking` from `.openspec.yaml`, close Forgejo issue (PATCH state=closed), update Plane work item to Done state
- [x] 2.2 Mirror archive skill changes to `.augment/`, `.codex/`, `.gemini/`, `.github/` skill directories

## 3. Release Prep Skill

- [x] 3.1 Create `.claude/skills/release-prep/SKILL.md` with full checklist: version validation, changelog check, build verification, active changes check, Forgejo milestone creation + issue validation, Plane cycle creation + work item validation, readiness summary
- [x] 3.2 Mirror release-prep skill to `.augment/`, `.codex/`, `.gemini/`, `.github/` skill directories

## 4. Verification

- [x] 4.1 Dry-run: test tracker creation by reading the propose skill instructions and verifying the curl commands are correct against the live Forgejo/Plane APIs
- [x] 4.2 Verify `.openspec.yaml` tracking section can be read back by the archive skill instructions

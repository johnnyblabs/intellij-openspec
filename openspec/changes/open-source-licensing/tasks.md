## 1. Project Rename (OpenSpecPlugin → intellij-openspec)

### 1a. In-repo file updates

- [x] 1.1 `settings.gradle.kts` — change `rootProject.name` to `intellij-openspec`
- [x] 1.2 `Makefile` — change `PLUGIN_NAME` to `intellij-openspec`
- [x] 1.3 `openspec/config.yaml` — change `name` to `intellij-openspec`
- [x] 1.4 `.forgejo/workflows/build.yaml` — change artifact name from `OpenSpecPlugin` to `intellij-openspec`
- [x] 1.5 `scripts/lib/forgejo-api.sh` — change `FORGEJO_REPO` to `intellij-openspec`
- [x] 1.6 `scripts/setup-signing.sh` — change `REPO` to `johnb/intellij-openspec`
- [x] 1.7 `scripts/sync-status.sh` — update Forgejo URL reference
- [x] 1.8 `README.md` — update clone instructions and project references
- [x] 1.9 `.github/copilot-instructions.md` — update directory reference
- [x] 1.10 `scripts/docs/wiki/Installation.md` — update clone/cd instructions
- [x] 1.11 `scripts/docs/wiki/Build-and-Dev-Setup.md` — update project references
- [x] 1.12 `scripts/docs/data/plane-pages/dev-guide.md` — update clone URL and cd instructions
- [x] 1.13 `MANUAL-TASKS.md` — update Forgejo secrets URL

### 1b. Infrastructure (manual steps)

- [x] 1.14 Forgejo: rename repo from `OpenSpecPlugin` to `intellij-openspec` (Settings → Repository Name)
- [x] 1.15 Local: rename working directory `mv ~/working/OpenSpecPlugin ~/working/intellij-openspec`
- [x] 1.16 Update git remote origin URL to match new Forgejo repo name
- [x] 1.17 Plane: update project references if repo name appears in work items
- [x] 1.18 Verify CI runs successfully after rename
- [x] 1.19 Update Claude Code memory files (`.claude/` project path will change with folder rename)

## 2. License Files

- [x] 2.1 Add Apache 2.0 LICENSE file to project root
- [x] 2.2 Add NOTICE file with copyright attribution (Copyright 2026 John Boyce)

## 3. Pre-Publish Audit & Cleanup

- [x] 3.1 Audit `.gitignore` — ensure `.env`, secrets, signing keys, `.pem`, `.b64`, and IDE-specific files are excluded
- [x] 3.2 Scan git history for accidentally committed credentials or tokens
- [x] 3.3 Genericize `scripts/setup-tokens.sh` — remove default `.geek` URLs, read only from `.env`
- [x] 3.4 Genericize `scripts/setup-signing.sh` — ensure no internal URLs leak (already clean)
- [x] 3.5 Clean `MANUAL-TASKS.md` — remove or genericize `forgejo.geek`, `plane.geek`, runner infrastructure details
- [x] 3.6 Clean `scripts/docs/data/plane-pages/dev-guide.md` — replace internal URLs with public GitHub URL
- [x] 3.7 Clean `scripts/PLANE_API_TEST_RESULTS.md` — remove `plane.geek` references or exclude from public repo
- [x] 3.8 Review all files under `scripts/docs/data/` for internal URLs
- [x] 3.9 Review `.claude/` directory — ensure memory files don't contain sensitive homelab details

## 4. GitHub Repository

- [ ] 4.1 Create public repo `johnnyblabs/intellij-openspec` on GitHub (do NOT initialize with README/license)
- [ ] 4.2 Add GitHub as a second git remote: `git remote add github git@github.com:johnnyblabs/intellij-openspec.git`
- [ ] 4.3 Push main branch to GitHub (only after ALL audit/cleanup tasks are complete)

## 5. Marketplace & Build Updates

- [x] 5.1 Update `docs/marketplace-page.md` — set License to Apache 2.0, add GitHub source link
- [x] 5.2 Update `build.gradle.kts` vendor block with GitHub repository URL
- [x] 5.3 Update `plugin.xml` vendor URL if needed (already public: openspec.johnnyblabs.com)
- [ ] 5.4 Update Marketplace plugin edit page: select Apache 2.0 license, add source code URL

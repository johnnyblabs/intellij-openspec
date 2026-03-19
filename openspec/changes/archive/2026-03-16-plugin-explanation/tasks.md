## 1. Feature Reference (foundation — other docs link to this)

- [x] 1.1 Create `docs/feature-reference.md` with sections: Browsing & Navigation, Workflow Orchestration, AI Integration & Delivery, Editor Integration, Validation, Settings & Configuration
- [x] 1.2 Populate each section with feature descriptions extracted from the current README and marketplace page (single source of truth, no duplication)

## 2. Persona Getting-Started Guides

- [x] 2.1 Create `docs/getting-started-browser.md` — Spec Browser persona: install, open tool window, browse tree, view coverage, navigate via gutter markers (zero AI setup)
- [x] 2.2 Update `docs/getting-started-copilot.md` as the IDE-First Developer guide: clipboard delivery mode, propose a change, generate artifact, paste into Copilot/Cursor/Windsurf/Cline chat panel
- [x] 2.3 Create `docs/getting-started-cli-companion.md` — CLI Companion persona: plugin as spec dashboard alongside Claude Code/Gemini CLI, copy prompts with save-path hints, coverage tracking
- [x] 2.4 Create `docs/getting-started-api.md` — Standalone API User persona: configure Direct API provider, store credentials, Fast-Forward end-to-end workflow

## 3. Cross-Links Between Guides

- [x] 3.1 Add "You might also want to explore" section to each of the four persona guides with contextually relevant links to other guides

## 4. README Landing Page

- [x] 4.1 Rewrite `README.md` as a concise landing page: one-paragraph description, persona table (name, value prop, link to guide), installation, links to feature reference / marketplace / OpenSpec repo
- [x] 4.2 Remove exhaustive content from README (menu reference, troubleshooting, settings docs) — this content now lives in `docs/feature-reference.md`

## 5. Marketplace Page Update

- [x] 5.1 Restructure `docs/marketplace-page.md` to lead with persona value framing ("Four ways to use this plugin") before feature lists
- [x] 5.2 Ensure marketplace persona names and descriptions match README persona table exactly

## 6. Verification

- [x] 6.1 Verify all four persona guides link to `docs/feature-reference.md` sections rather than duplicating feature descriptions
- [x] 6.2 Verify README persona table links resolve to the correct getting-started guides
- [x] 6.3 Verify marketplace and README use consistent persona naming (Spec Browser, IDE-First Developer, CLI Companion, Standalone API User)

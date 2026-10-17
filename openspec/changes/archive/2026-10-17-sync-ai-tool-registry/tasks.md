## 1. Contract

- [x] 1.1 Capture the fixtures with `scripts/capture-ai-tools-fixtures.py`. It dumps `AI_TOOLS` and `TOOL_ID_ALIASES` from every published CLI from 1.3.0 (scratch installs, isolated `HOME`/`XDG_*`), keeping `<version>/ai-tools.json` where the registry changed and always for 1.14.3; captures `init --help`; and records 30 detection cases under `1.14.3/detect/`, each a layout from real `init --tools` runs or edge cases plus the ids upstream's `getAvailableTools` reports. Document provenance in the fixtures README. Verify: 50 ids at 1.14.3; `--tools windsurf` writes `.devin/` on 1.7.0 and 1.13.2; no machine paths in the fixtures.

## 2. Registry

- [x] 2.1 `AiToolRegistry` with one `AiTool` record per registry tool (D1), each with `sinceCli` (D2) and aliases (D4); `AiToolDetectionService` and its callers read it, and the four maps are removed or derived. Verify: a contract test that the records equal `ai-tools.json` (ids, names, dirs, detection paths, legacy dirs) and that each `sinceCli` equals the first captured version containing the id; the `init-help.txt` id list equals the dump. Mutation check: dropping a record, or changing a dir, fails it.

## 3. Behaviour

- [x] 3.1 Detection ports upstream's `getAvailableTools` and shared-root reconciliation, including global tools (D3). Verify: a contract test rebuilds each of the 30 captured layouts on disk and asserts the detected ids equal upstream's, covering bare `.github` (nothing), the old 1.6 directories, Codex with Antigravity, and unmarked `.agents` roots; status tests that the layouts from real inits show as configured. Mutation checks: ignore the marker, use bare `.github`, drop old dirs, `isDirectory` for file detection paths.
- [x] 3.2 Version gating for offering tools and `--tools` (D2), and aliases for a saved preferred tool (D4). Verify: with a faked 1.3.0 CLI, a 1.14.0 tool isn't offered or passed; with 1.14.3, it is; a saved "Windsurf" preference resolves to Devin Desktop and passes `windsurf`.

## 4. Docs and verify

- [x] 4.1 README and `docs/marketplace/getting-started.html` ("detects the 50 AI coding tools OpenSpec 1.14 supports"), the `docs/feature-reference.md` tool table, and the CHANGELOG (`### Fixed`: Windsurf/Devin, Codex, Antigravity and Kimi are recognised where OpenSpec now writes them; Copilot is no longer reported for every GitHub repository; `### Added`: 20 more tools). Verify: docs match the registry; a leak grep over the diff is clean; the Marketplace drift check flags the Getting Started change for re-paste.
- [x] 4.2 Sandbox: open a project initialised with `openspec init --tools windsurf,codex` and confirm both show as configured in the setup wizard and Manage AI Tools, and that a plain GitHub repo doesn't list Copilot. Run `./gradlew build`, `./gradlew verifyPlugin` and `openspec validate sync-ai-tool-registry --strict`. Verify: all pass.
  - Recorded 2026-10-17: build passed (1907 tests) and the verifier reported Compatible on all five IDEs, with only the two documented deprecations. In the IDEA 2024.2 sandbox, a project set up with `openspec init --tools windsurf,codex` plus a plain `.github/workflows` shows Devin Desktop and Codex as configured in Manage AI Tools, with GitHub Copilot not detected.

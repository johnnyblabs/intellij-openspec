# OpenSpec IDE Extension — Feature Comparison Matrix

A comprehensive comparison of the OpenSpec IntelliJ plugin against all known VS Code extensions. Last updated: 2026-03-07.

---

## Extensions Compared

| Extension | IDE | Publisher | Version | Installs | Status |
|---|---|---|---|---|---|
| **OpenSpec IntelliJ Plugin** | IntelliJ IDEA 2024.2+ | johnb | 0.1.0-dev | — | In development |
| **OpenSpec** (Codder13) | VS Code | Denis Bolba | 0.0.5 | 1,972 | Community, proposed official |
| **OpenSpec for Copilot** | VS Code | atman-dev | 1.0.0 | 956 | Community |
| **OpenSpec VSCode** | VS Code | AngDrew | 1.3.0 | 592 | Community |
| **OpenSpec for VSCode** | VS Code | AvantMedia | 0.2.0 | 68 | Community |

---

## Core Workflow

| Feature | IntelliJ | Codder13 | atman-dev | AngDrew | AvantMedia |
|---|---|---|---|---|---|
| Initialize project (`openspec init`) | Yes (menu + toolbar) | No | No | No | No |
| Propose change (create + scaffold) | Yes (dialog with name/desc) | No | Yes | No | No |
| Apply change | Yes (menu action) | No | No | No | No |
| Archive change | Yes (menu action) | No | Yes | No | No |
| List specs/changes | Yes (tree + menu) | No | Partial | Yes | Yes |
| Full lifecycle (init -> archive) | Yes | No | Partial | No | No |

---

## Artifact Generation & DAG

| Feature | IntelliJ | Codder13 | atman-dev | AngDrew | AvantMedia |
|---|---|---|---|---|---|
| Visual artifact pipeline (DAG) | Yes (chip row with arrows) | No | No | No | No |
| Artifact status indicators | Yes (done/ready/blocked icons) | No | No | Partial | Yes (progress) |
| One-click Generate button | Yes (smart default method) | No | No | No | No |
| Generate All (walk full DAG) | Yes (Direct API) | No | No | Yes (fast-forward) | No |
| Delivery: Copy to Clipboard | Yes | Yes (via Copilot) | Yes | No | No |
| Delivery: Open in Editor Tab | Yes | No | No | No | No |
| Delivery: Direct API call | Yes (Claude/OpenAI/Gemini) | No | No | No | No |
| Split button (method dropdown) | Yes | No | No | No | No |
| Post-generation guidance card | Yes (tool-aware) | No | No | No | No |
| Next artifact indicator | Yes | No | No | No | No |
| Scaffolding detection (content-aware) | Yes | No | No | No | No |

---

## AI Integration

| Feature | IntelliJ | Codder13 | atman-dev | AngDrew | AvantMedia |
|---|---|---|---|---|---|
| Direct API: Claude (Anthropic) | Yes | No | No | No | No |
| Direct API: OpenAI | Yes | No | No | No | No |
| Direct API: Gemini | Yes | No | No | No | No |
| Secure credential storage (OS keychain) | Yes (PasswordSafe) | No | No | No | No |
| API connection test | Yes | No | No | No | No |
| AI tool auto-detection | Yes (6 tools) | No | No | No | No |
| Tool type classification (CLI/IDE panel) | Yes | No | No | No | No |
| Tool-aware guidance text | Yes | No | No | No | No |
| Preferred tool selection | Yes (persisted) | No | No | No | No |
| Save-path hint for CLI tools | Yes | No | No | No | No |
| CodeLens for task-to-AI-chat | No | Yes (core feature) | No | No | No |
| Context injection into AI chat | No | Yes (Copilot Chat) | No | No | No |

---

## Tool Window & Tree

| Feature | IntelliJ | Codder13 | atman-dev | AngDrew | AvantMedia |
|---|---|---|---|---|---|
| Dedicated tool window panel | Yes (right sidebar) | No | No | Yes (explorer) | Yes (panel) |
| Specs tree browser | Yes | No | Partial | Yes | No |
| Changes tree browser | Yes | No | No | Yes | Yes |
| Archive tree browser | Yes | No | No | No | No |
| Artifact nodes in tree | Yes (with status icons) | No | No | Partial | Yes |
| Tree auto-refresh on file changes | Yes (file watcher) | No | No | No | Yes |
| Context menu actions on tree nodes | Yes (full action set) | No | No | Partial | No |
| Workflow Action Panel (below tree) | Yes | No | No | No | No |
| Change selector (multi-change) | Yes (combo box) | No | No | No | No |
| Status bar (CLI + AI tools) | Yes | No | No | No | No |
| Console output panel | Yes | No | No | No | No |

---

## Editor Integration

| Feature | IntelliJ | Codder13 | atman-dev | AngDrew | AvantMedia |
|---|---|---|---|---|---|
| Inline validation inspections | Yes (3 inspectors) | No | No | No | No |
| Editor annotations | Yes (2 annotators) | No | No | No | No |
| Line markers on specs | Yes | No | No | No | No |
| Click-to-navigate from tree to file | Yes | No | No | Yes | Yes |
| Spec file syntax awareness | Yes | No | No | No | No |

---

## Validation

| Feature | IntelliJ | Codder13 | atman-dev | AngDrew | AvantMedia |
|---|---|---|---|---|---|
| Built-in spec validation | Yes | No | No | No | No |
| CLI-enhanced validation | Yes (merged results) | No | No | No | No |
| Config.yaml validation | Yes | No | No | No | No |
| Delta spec validation | Yes | No | No | No | No |
| Strict validation mode | Yes (setting) | No | No | No | No |
| Real-time inline validation | Yes (inspections) | No | No | No | No |
| Validation results in console | Yes | No | No | No | No |

---

## Settings & Configuration

| Feature | IntelliJ | Codder13 | atman-dev | AngDrew | AvantMedia |
|---|---|---|---|---|---|
| Dedicated settings panel | Yes (Settings > Tools) | Minimal | No | No | No |
| CLI path configuration | Yes (manual + auto-detect) | No | No | No | No |
| Schema version override | Yes | No | No | No | No |
| Profile selection | Yes | No | No | No | No |
| Auto-refresh toggle | Yes | No | No | No | No |
| AI provider/model selection | Yes | No | No | No | No |
| Preferred delivery method | Yes (persisted) | No | No | No | No |

---

## Scaffolding & Project Setup

| Feature | IntelliJ | Codder13 | atman-dev | AngDrew | AvantMedia |
|---|---|---|---|---|---|
| Init creates full directory structure | Yes | No | No | No | No |
| Propose creates all artifact scaffolds | Yes (proposal + design + tasks + specs/) | No | Yes | No | No |
| Template-based file generation | Yes | No | No | No | No |
| Scaffolding content detection | Yes (strips headings, comments, placeholders) | No | No | No | No |
| Version-aware scaffolding | Yes (adapts to schema version) | No | No | No | No |

---

## Unique Features Per Extension

### IntelliJ Plugin (This Plugin)
- Full IDE-native experience (inspections, annotations, line markers, tool window)
- Multi-provider Direct API generation with secure credential storage
- Visual artifact pipeline with DAG-driven workflow
- Scaffolding detection preventing false "complete" status
- AI tool detection with type-aware guidance (CLI vs IDE panel)
- Built-in + CLI-enhanced validation with inline results
- Works fully offline without CLI (built-in fallback mode)

### Codder13 (VS Code)
- CodeLens integration on `tasks.md` — click a task to open Copilot Chat with full project context injected
- Tight coupling with GitHub Copilot Chat (sends prompt + context automatically)
- Proposed for official OpenSpec adoption (GitHub issue #309)

### atman-dev (VS Code)
- GitHub Copilot prompt management (`.github/prompts/` generation)
- GitHub issue creation from changes
- Design generation workflow

### AngDrew (VS Code)
- Ralph Loop batch processing (automated artifact generation cycles)
- Live monitoring via localhost:4099
- Tied to OpenCode agentic CLI

### AvantMedia (VS Code)
- Real-time progress badge notifications
- Minimal, focused change/task monitoring panel

---

## Competitive Summary

```
                    Feature Depth
                         |
                    HIGH  |  * IntelliJ Plugin
                         |
                         |
                         |          * AngDrew
                         |
                  MEDIUM  |     * atman-dev
                         |
                         |  * Codder13        * AvantMedia
                    LOW  |
                         |
                         +————————————————————————————————
                         Narrow                    Broad
                              Feature Breadth

IntelliJ Plugin: Deepest AND broadest feature set
Codder13:        Narrow but clever (CodeLens + Copilot Chat)
atman-dev:       Moderate breadth (workflow + prompts + GitHub)
AngDrew:         Moderate (batch processing + monitoring)
AvantMedia:      Narrow and simple (monitoring only)
```

---

## Gap Analysis: What Others Have That We Don't (Yet)

| Feature | Who Has It | Priority | Planned |
|---|---|---|---|
| CodeLens on tasks (click to start AI chat) | Codder13 | High | v0.2.0 |
| Auto-inject project context into AI chat | Codder13 | Medium | Investigate |
| GitHub issue creation from changes | atman-dev | Low | — |
| Batch processing loops (Ralph Loop) | AngDrew | Medium | Have "Generate All" |
| Live monitoring server | AngDrew | Low | — |
| Badge notifications | AvantMedia | Low | Have inline status |

**Key gap**: Codder13's CodeLens + Copilot Chat injection is the only feature that is genuinely compelling and missing from our plugin. The IntelliJ equivalent would be gutter icons on task lines that trigger AI chat with context. This aligns with the "Spec Intelligence" direction in v0.2.0.

---

## Platform Advantages: IntelliJ vs VS Code

| Capability | IntelliJ Advantage | VS Code Equivalent |
|---|---|---|
| Inspections framework | Deep, configurable, per-scope analysis | Basic diagnostics |
| Quick-fixes (LocalQuickFix) | Context-aware, undo-able, batched | Code Actions (simpler) |
| Line markers / gutter icons | Rich, clickable, layered | Limited (decorations) |
| Editor annotations | Inline, severity-based | Decorations (text only) |
| Tool window framework | Dockable, resizable, tabbed, persistent | Webview panels |
| Settings framework | Searchable, grouped, validated | Settings JSON |
| PSI / code analysis | Full AST, cross-reference, refactoring | Language Server (external) |
| Credential storage | OS keychain via PasswordSafe | Secrets API (simpler) |
| File watchers | VFS-level, reliable, batched | fs.watch (less reliable) |
| Progress indicators | Modal/background, cancellable, nested | Limited |

IntelliJ's platform gives us capabilities that VS Code extensions fundamentally cannot match in depth. The strategy should be to lean into these platform advantages, not replicate VS Code patterns.

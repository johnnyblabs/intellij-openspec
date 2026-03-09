## Context

The OpenSpec IntelliJ plugin ships with a README that catalogs features but lacks a guided tutorial. Users who install the plugin face a cold start — they see an empty tool window, unfamiliar menus, and no clear path forward. The Copilot integration (via `.github/prompts/`) is particularly opaque: users don't know how clipboard delivery, prompt generation, and Copilot chat work together.

A `docs/getting-started-copilot.md` will serve as the canonical onboarding document, pairing plugin mechanics with a concrete worked example.

## Goals / Non-Goals

**Goals:**
1. Walk a user from plugin install to a completed, archived change in a single document
2. Explain every settings panel option with enough context to configure confidently
3. Show the complete artifact lifecycle (propose → generate → apply → archive) with Copilot as the AI tool
4. Clearly delineate "OpenSpec drives this" vs. "AI generates this" at each step
5. Be accurate to the current plugin codebase — settings names, menu paths, button labels must match reality

**Non-Goals:**
- Covering Direct API mode (separate guide)
- Covering Claude Code or other CLI tools (separate guide)
- Plugin development/contribution guide
- OpenSpec CLI reference (covered by OpenSpec docs)
- Video or animated content

## Decisions

### Decision 1: Document structure — tutorial-first, reference-second

**Choice:** Lead with a worked example (Part 2) that references settings (Part 1) contextually, rather than a pure reference doc.

**Rationale:** Users learn by doing. A settings reference alone doesn't answer "when do I use this?" A tutorial alone doesn't answer "what does this checkbox do?" The hybrid approach covers both. Part 1 (Settings) can be skimmed on first read and returned to later.

**Alternative rejected:** Separate settings reference and tutorial docs — fragments the experience, users have to cross-reference.

### Decision 2: Copilot-specific, not tool-agnostic

**Choice:** Write specifically for GitHub Copilot with concrete Copilot Chat instructions, rather than a generic "paste into your AI tool" guide.

**Rationale:** Generic guides are vague and unhelpful. Copilot users need to know: open Copilot Chat, use `/opsx-propose`, paste the prompt, copy the response, save to the file path shown. Tool-specific guides can be added later for Claude Code, Cursor, etc.

### Decision 3: Annotated flow markers

**Choice:** Use callout blocks (blockquotes with bold labels) to mark each step as either **OpenSpec** (plugin/framework driving the workflow) or **AI** (Copilot generating content).

**Rationale:** The user's core confusion is "what does what?" Clear visual markers at each step resolve this without repeating explanations.

### Decision 4: Single markdown file

**Choice:** One `docs/getting-started-copilot.md` file rather than multiple pages.

**Rationale:** Keeps the guide self-contained and printable. The document is long but structured with a clear table of contents. Users can jump to any section. Multi-page docs fragment context and require navigation.

## Risks / Trade-offs

**Risk:** Document becomes stale as plugin UI evolves.
→ Mitigation: Use exact setting/menu names from code so grep can find references. Add a "Last verified" date at the top.

**Risk:** Document is too long for a "getting started" guide.
→ Mitigation: Clear table of contents, Part 1 can be skipped if user just wants the tutorial. Each section is self-contained.

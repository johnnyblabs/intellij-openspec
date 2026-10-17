## Context

See proposal.md, Why. `SpecSyncService` parses delta sections and main-spec requirement blocks with line regexes (`SECTION_HEADER`, `REQUIREMENT_HEADER`, `REMOVED_BULLET_ENTRY`, the `^##[#]? ` block end in `findRequirementBlock`). It matches names case-insensitively, records problems as `SpecSyncResult.warnings`, and still writes whatever it could apply. `ChangeService.archiveChange` moves the change folder to `changes/archive/<name>` (or `openspec/archive/` when `changes/archive/` is missing).

An openspec-guru analysis of the installed 1.14.3 package pinned upstream's rules (file:line evidence in the tracker notes), confirmed by real archive runs:
- fence masking (`core/parsers/code-fence.js`, `requirement-blocks.js`);
- exact matching after `normalizeRequirementName`, plus a case- and whitespace-folded form used only to refuse near-misses;
- the refusal set and its wording (`core/specs-apply.js`);
- all-or-nothing per change (`core/archive.js`);
- the new-capability skeleton;
- preserving text outside requirements;
- local-date `YYYY-MM-DD-<name>` archive folders, refusing an existing one.

Upstream has no `sync` command; Sync is an agent skill that tells the agent to match `openspec archive`.

## Goals / Non-Goals

**Goals:** for every captured case, the plugin's merged main spec equals upstream's after `openspec archive`, or both refuse; nothing is ever partly applied; the archive folder matches upstream.

**Non-Goals:**
- Delegating the archive write to the CLI (a follow-on change).
- Upstream's post-merge structural validation of the rebuilt spec, which is beyond the plugin's existing post-merge validation.
- `retire_capabilities`.
- Store-backed change roots.

## Decisions

### D1. Mask fences once, search the masked copy, cut from the original

A `CodeFences.mask(String)` helper returns a same-length copy with every line inside a fence blanked, fence lines included, and newlines kept. The rules mirror upstream exactly:
- the opener is `^\s*(`{3,}|~{3,})`;
- a fence closes on the same character, at least as long, with nothing else on the line;
- an unclosed fence masks to the end of the file.

Every structural regex runs on the masked copy: section headers, requirement headers, REMOVED and RENAMED lines, block ends, `####` scenario headers, and `## Requirements`. The text itself is always cut from the original by the same offsets, so the fences are preserved verbatim.

*Alternative considered:* a markdown parser. That's a new dependency, and it would differ from upstream's line rules in edge cases.

### D2. Exact names, with a folded form only for refusals

`normalizeName` strips a trailing `[ \t]+#+[ \t]*$` run and trims; all matching uses it. `foldName` lowercases it and collapses whitespace, and it's used only to detect near-misses so they can be refused. The header token (`### requirement:`) stays case-insensitive, as the `spec-sync` spec already requires. This replaces today's case-insensitive name matching, which is what let a case variant be silently modified or renamed.

### D3. Refusals are separate from warnings, and they stop the whole change

`SpecSyncResult` gains `refusals` next to `warnings`. `computeSync` evaluates every capability of the change, and the change is applicable only if no result has a refusal. `applySync` refuses (throws, writing nothing) when any result carries one. That gives the all-or-nothing behaviour across capabilities, because nothing is written until every capability has been computed and passed.

Refusal messages reuse upstream's wording, for example `<cap> MODIFIED failed for header "### Requirement: X" - not found`. Warnings (REMOVED already gone, ignored REMOVEDs for a new spec) never block.

### D4. Preview and Bulk Archive consume refusals

- **The sync preview dialog** lists refusals above the diff and disables its apply button when there are any.
- **Bulk Archive's gate** (`Archiver.check`) blocks on refusals only. A REMOVED whose target is already gone no longer blocks a change, matching upstream.

### D5. Rebuild the main spec the way upstream does

Port upstream's rebuild:
- keep everything before `## Requirements`, the preamble inside it, and any later `##` sections verbatim;
- keep one blank line between the heading and the first requirement;
- collapse runs of blank lines outside fences;
- append new requirements after the existing ones;
- keep renamed blocks in place.

New capabilities use the exact skeleton from the spec delta. A delta `## Purpose` replaces the placeholder. The captured `main-after.md` files are the oracle: the contract tests compare the plugin's output to them byte for byte, after normalizing line endings.

### D6. Archive folder

`ChangeService.archiveChange` targets `changes/archive/<LocalDate.now()>-<name>`, or `<name>` alone if it already matches `^\d{4}-\d{2}-\d{2}-`. If `changes/archive/` is missing, it's created under `openspec/changes/`. The archive refuses with `Archive '<target>' already exists.` before any spec is written; callers check this first, and the Sync First path re-checks. `OpenSpecFileUtil.getArchiveDir`'s read fallback to a legacy `openspec/archive/` stays, for listing old archives only. The date is injectable through a package-private `Clock` for tests.

### D7. What the port leaves out, and where it deliberately differs

Found while porting, settled by what the CLI does:

- **Bare RENAMED names are not renames.** Upstream reads `FROM:`/`TO:` only with the `### Requirement:` header. A delta whose only entries are bare is refused ("Delta parsing found no operations…"); bare lines beside real entries are ignored, as upstream ignores them. The `spec-sync` "Delta spec parsing" requirement is modified to match. The built-in validator still accepts bare pairs; aligning it is tracked separately.
- **Not ported:** upstream's warnings about delta text the merge never reads (requirements outside a delta section, headings absorbed into a requirement), and its structural check of the main spec before merging. Neither changes what is written or refused for a well-formed spec.
- **Carried Purpose readability** stands in for upstream's re-parse with the case a well-formed delta can hit: an HTML comment in the body falls back to the placeholder, with upstream's warning. Headings of any level are carried, as captured; an unclosed fence fails earlier because it swallows the whole delta.
- **Refusal leaves nothing behind.** On a merge refusal upstream still creates an empty `changes/archive/`; the plugin creates nothing when it refuses.
- **Main specs are read through the VFS** (falling back to disk), like the deltas, so the preview reflects unsaved editor content consistently.

## Risks / Trade-offs

- **Stricter than before for users without the CLI.** Changes that used to "sync" with a silent skip are now refused. → That's the point: it matches every CLI from 1.13.1 on, and refusing is reversible while a corrupted spec isn't. The preview says exactly what to fix.
- **Older CLIs behaved differently** (for example, 1.13.0 added case variants, and before 1.7 a missing REMOVED target refused). → The plugin targets 1.14.3 behaviour for its own merge; the follow-on delegation change gives CLI users their own CLI's behaviour.
- **Byte-for-byte equality is brittle on whitespace.** → Normalize CRLF only. Any other difference is a real divergence, to fix or record explicitly in the test.

## Migration Plan

Code-only; no data migration. Archived folders created before this change keep their undated names. To roll back, revert the commit.

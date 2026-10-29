# Design

## Context

See proposal.md for why. The relevant current state:

- **Fast-Forward.** The panel's submit handler takes the description, derives a name from it (or uses the override), runs `openspec new change <name>` on a background task, then on success either runs Generate All (Direct API) or generates the next ready artifact (clipboard or editor tab). Neither path receives the description. Prompts come from `ArtifactInstruction.buildPrompt(note)` for the single-artifact path and from `ArtifactOrchestrationService.generateAllRemaining`, which calls `DirectApiService.generate(instruction)` per artifact. `buildPrompt(note)` already appends a one-off section, but under a regeneration heading ("Note for this regeneration … this note only says what to change"), which is the wrong framing for a first-time request.
- **Sync.** `SpecSyncService.readDeltaSpecs` lists the direct children of the change's `specs/` folder and reads `<child>/spec.md`. Everything downstream is already path-agnostic: the main-spec path is `openspec/specs/<capability>/spec.md` built by string concatenation, the VFS fallback uses `findFileByRelativePath`, writing uses `Files.writeString` on that path, and `SpecMerge`'s new-capability skeleton titles the file `# <capability> Specification`. With a capability key of `identity/user-auth`, all of them produce the paths and title the CLI produces (captured from a real `openspec archive` on CLI 1.15.0).

## Goals / Non-Goals

**Goals:**
- One prompt section for "the request this change is for", shared by every delivery mode.
- Recursive delta discovery whose keys and order match `openspec archive`.

**Non-Goals:**
- Making clipboard or editor-tab Fast-Forward easier to reach, or handing off to the agent's own `/opsx:ff`. Both belong to the menu-alignment work already tracked for the next release.
- Persisting the request (no `--description`, no README, nothing in `.openspec.yaml`).
- Any change to the CLI-backed archive, validation or Explore paths, which already handle nested capabilities.

## Decisions

**1. A separate request section, not the regeneration note.**
Add `ArtifactInstruction.REQUEST_HEADING` (e.g. "The user's request for this change (what they want to build):") and an overload that takes both a request and a note. Empty or blank request means the prompt is unchanged byte for byte, preserving every existing prompt contract test. The request section comes after the instructions and before any note.
*Alternative:* reuse `buildPrompt(note)`. Rejected: its heading tells the model to treat the text as a delta to existing content, which is wrong for a new change, and the review-before-overwrite flow keys behaviour off the note.

**2. The request lives only for one Fast-Forward run.**
`generateAllRemaining` gains an overload taking a nullable request that it passes to every artifact's Direct API prompt in that run; the existing signature delegates with `null`. For clipboard and editor tab, the panel passes the request into the single generation it triggers. The panel does not store the description on the change, so later Generate, Continue or Regenerate prompts are unchanged. This matches upstream `ff`, where the request is conversation context: once the proposal exists, it carries the intent.
*Alternative:* store the description in a field keyed by change name. Rejected: stale state across refreshes and changes, and nothing upstream persists it either.

**3. The request goes into `DirectApiService.generate`, which builds the prompt.**
`generate(instruction)` builds its prompt itself with `instruction.buildDirectApiPrompt()`. Add `generate(instruction, request)` that passes the request to `buildDirectApiPrompt`, with the existing method delegating with `null`. Building the prompt outside the service was rejected, because multi-file artifact handling should stay in one place.

**4. Recursive walk keyed by relative path, sorted.**
Walk the change's `specs/` folder depth-first, collecting every `spec.md`; key = the containing folder's path relative to `specs/`, joined with `/` on every OS (use `VfsUtilCore.getRelativePath(dir, specsDir, '/')`). Keep the `TreeMap`, whose natural string order gives `flat`, `identity/sessions`, `identity/user-auth`, the order the CLI printed. A folder may hold a `spec.md` and nested capability folders; both are read, as the CLI's glob does.
*Alternative:* ask the CLI (`status --json` lists `specs/**/*.md` outputs). Rejected: Sync is the built-in fallback that must work without the CLI.

**5. Parent folders on create are already handled.**
`applySync` already calls `Files.createDirectories(path.getParent())` before writing, so a new `openspec/specs/identity/user-auth/spec.md` gets its `identity/` folder. A test pins it.

## Risks / Trade-offs

- [A request section shifts byte-level prompt fixtures] → It is only added when a request is present; prompt tests without one stay byte-identical, and a new test pins the section's placement.
- [Windows separators in capability keys] → Use the VFS relative-path API with an explicit `/`, never `java.nio` `relativize().toString()`. Cover with a test that asserts the key is `identity/user-auth`.
- [Hidden or symlinked folders under `specs/`] → Walk only real directories in the VFS and skip dot-folders, as the existing one-level loop effectively did. The CLI ignores them too.

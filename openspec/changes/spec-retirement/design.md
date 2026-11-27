# Design

## Context

Upstream retires in `archive` (`decideSpecOutcome`, `retireSpec` and the audit `contentTheMergeCannotName` in `dist/core/specs-apply.js`), first in 1.8.0. The plugin's merge (`SpecMerge`) follows the detected release for its new-spec layout and its refusals; the validator follows a release the same way. With a CLI at 1.5 or later, the plugin's archive delegates to `openspec archive`; Sync Specs always uses `SpecMerge`.

## Goals / Non-Goals

**Goals:**
- Sync Specs and the plugin's archive retire, or refuse, exactly as the followed release's `openspec archive` does on captured cases.
- The user sees what a retirement deletes before it happens, and what was retired after.

**Non-Goals:**
- A quick fix that adds the marker (a separate change).
- Upstream's deferred-delete mechanics (rename, fingerprint, restore on a later failure): the plugin deletes after the whole sync has been checked, in one write action.

## Decisions

### D1. When it retires

Following upstream's `decideSpecOutcome`, a capability is retired when all of these hold:
- the followed release is 1.8.0 or later;
- the change's `.openspec.yaml` sets `retire_capabilities: true`, read as upstream reads it (an unreadable file, invalid YAML that mentions the key, or an unknown schema count as not declared);
- the merge removed at least one requirement and the rebuilt spec has none, and nothing else would make the release's validator refuse it;
- the main spec exists;
- the release's check finds nothing the merge can't account for.

### D1a. Reading the marker

As upstream's `readBooleanMarker` (unchanged 1.8.0 through 2.2): YAML read with YAML 1.2's core schema (so `yes` is a string and a duplicate key is invalid YAML); every `ChangeMetadataSchema` field checked in its order, so a `true` marker in metadata failing another field is not honored; then the schema resolved as `resolveSchema` does (project `openspec/schemas/<name>/`, then the user's, then built-in `spec-driven`). An override counts only when the installed CLI validates it; with no CLI it isn't honored, so the plugin never deletes on a schema it can't check. A marker present but not honorable counts as not declared, with the CLI's reason where a captured run pins its wording, and without a reason otherwise. (`openspec schemas --json` is not the source: it lists a built-in under an invalid override that archive then rejects.)

### D1b. A capability already retired

A removal from a capability whose main spec is gone: from 1.8 a marked change archives writing no spec (the "ignored for new spec" warning stays); otherwise it's refused. The plugin never writes a spec with no requirements.

### D2. The check, per release

Three variants of upstream's `contentTheMergeCannotName`, chosen by the followed release:
- **A** (1.8.0–1.12.x);
- **B** (1.13.0–2.1.0): indentation-aware list continuation, `+` bullets and up to nine-digit ordered markers;
- **C** (2.1.1 and later): a line starting with `<` is raw HTML only when it opens an HTML block or is a tag alone on its line.

Allowed content: the first `#` title, the `## Purpose` body, the `## Requirements` header, each block's `###` header, its statement before the first scenario, and its scenario bullets. Fenced code is masked. Anything else is a leftover. Each variant is ported rule for rule and contract-tested against that release's captured runs.

### D3. Refusals, in the release's words

- **Leftovers, marker set:** `'<id>' declares retire_capabilities, but the spec holds content the merge cannot safely account for and deleting the file would take with it: "<l1>", "<l2>", "<l3>", and N more line(s). Move it into `## Purpose` or a canonical requirement, or delete the spec by hand.` (Clipped to three lines from 1.11; earlier releases list them all.)
- **No marker:** `Spec must have at least one requirement`, with the hint naming the marker when the marker alone would let it through; from 1.11, a hint listing the leftover lines when there are some. Below 1.8, the error alone.

The exact strings come from the captures, not from reading the dist.

### D4. Deleting

`SpecSyncResult` gains `retired`: its projected content is empty and it is applied by deleting `spec.md`, then each parent folder left empty, stopping before `specs/`. A folder with other files is kept. The delete happens in the same write action as the sync's other writes, after every capability has been checked, so a refusal anywhere writes nothing.

### D5. What the user sees

- **Sync preview:** the capability's tab is titled `<id> (retire)`, with the current spec against an empty side labelled "After sync: file deleted", and a banner: "Retires capability `<id>`: specs/`<id>`/spec.md will be deleted." A refusal uses the existing refusal panel, verbatim, and disables Apply.
- **Archive prompt:** one added line when the merge would retire something: "This also deletes spec file(s): <ids>. Restore with git checkout." Same three buttons; the marker is the explicit opt-in.
- **Result:** "Retired: <ids>." and the restore hint `git checkout HEAD -- openspec/specs/<id>/spec.md`. On the CLI path the ids come from the CLI's `Retiring openspec/specs/<id>/spec.md: all requirements removed.` lines, contract-tested against a captured run; when none parse, the summary is unchanged.

## Risks / Trade-offs

- **The check is large and subtle.** Mitigation: a port per variant, each held to captured runs of its release, including the cases where variants differ (`<args>` prose, `+` bullets, indented continuation).
- **A spec deleted by mistake.** Mitigation: it needs the change's own marker; the preview and the prompt name the file; the result gives the restore command.

## Migration Plan

None.

## Open Questions

None.

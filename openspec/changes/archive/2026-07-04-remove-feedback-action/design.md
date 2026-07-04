# Design — Remove the Send OpenSpec Feedback action

## Context

The action shipped into the unreleased v-next window earlier the same day (change `feedback-action-and-support-doc-fixes`), so this is a pre-release retraction, not a deprecation: delete cleanly, leave a decision record instead of a tombstone UX. The decision boundary it encodes: the plugin surfaces the OpenSpec *workflow*; it does not broker communication with the upstream framework maintainers in either direction.

## Goals / Non-Goals

**Goals:**
- No trace of the action in the shipped plugin, its docs, or its walkthrough.
- The decision is legible everywhere someone might "rediscover" the gap: support-matrix row states *deliberately not surfaced* with the rationale, and the REMOVED spec delta carries reason + migration.

**Non-Goals:**
- No replacement feedback mechanism (the README's issue-tracker link is the plugin's channel; `openspec feedback` in a terminal is upstream's).
- No CHANGELOG removal notice — the feature never shipped, so Unreleased simply stops mentioning it.

## Decisions

1. **Delete, don't gate.** A hidden-by-flag action would keep dead code and an attractive nuisance; the spec-level decision record is the durable artifact, not the code.
2. **The support matrix keeps a `feedback` row** marked as a deliberate non-surface (not removed from the matrix): the row is where a future maintainer would look before re-proposing, so the rationale lives exactly there.
3. **The in-flight walkthrough expansion drops its feedback journey** (suite = five journeys) rather than testing a feature being deleted in the same release window.

## Risks / Trade-offs

- [Someone re-proposes it later] → the matrix row + spec REMOVED reason + memory of record make the prior decision discoverable at every entry point.

## Migration Plan

Single PR removing code/tests/registrations and updating docs; the capability spec is deleted from main specs at archive time via the REMOVED delta.

## Open Questions

None.

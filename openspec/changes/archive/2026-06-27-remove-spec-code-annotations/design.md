# Design

## The scope principle this change enforces

The plugin should surface only concepts the OpenSpec client has. OpenSpec has two, with deliberately different semantics:

| | What it is | Has progress/status? | Plugin surface that's on-model |
|---|---|---|---|
| **Specs** (`openspec/specs/`) | Living source of truth — current reality | **No** — `list`/`show`/`validate` only; CLI refuses `status` for a spec | Spec tree, show, validate, syntax highlighting, delta diff viewer |
| **Changes** (`openspec/changes/`) | Work with a lifecycle | **Yes** — artifacts (4/4) + tasks (X/Y) via `openspec status --change` | Change tree, status/progress, propose/apply/archive, verify |

Completion is a property of **work** (changes), never of **truth** (specs). The `@spec` feature violates this twice: it invents a **code↔spec link** OpenSpec has no concept of, and the Coverage panel then applies **change-style completion %** to specs. Both are out of scope. Removing the `@spec` annotation removes both surfaces, since both consume the same comment.

## Decision

Remove the `@spec` annotation and both surfaces entirely. **No replacement.** There is no OpenSpec-native code↔spec linking to demote to — `verify-change` infers implementation transiently at verify time and stores nothing. Code-to-spec navigation simply is not an OpenSpec concept, so the honest move is deletion, not reframing.

What stays (still on-model, untouched by this change):
- **Syntax highlighting** of RFC 2119 / scenario keywords — renders OpenSpec's *own* spec files; invents no user-facing vocabulary and asks nothing of the user.
- **Delta spec diff viewer** — a view of OpenSpec's own change/delta model.

## Alternatives considered (and rejected)

- **Demote to navigation-only** (keep `@spec` for gutter + a non-graded references view). Rejected: `@spec` itself is the out-of-scope concept; navigation-via-`@spec` still introduces invented vocabulary. This was the prior draft of this change; it fixed the worst symptom (the scorecard) but not the violation.
- **Keep gutter markers only.** Rejected for the same reason — still a plugin-invented annotation.
- **Opt-in "code-backed" denominator** to make the % meaningful. Rejected: still a completeness scorecard over specs, still off-model, and adds spec-side metadata upstream would strip.

## Supersession and git approach

`fix-coverage-language-detection` is implemented and green, but it polishes the very feature this change deletes, and it is **unreleased**. Per the decision, it is **superseded, not archived**.

Topology (verified): A is fully self-contained — its 3 commits (`988cede`, `a184f47`, `6bde737`) live only on `change/fix-coverage-language-detection`; `main` has none of A (no change dir, no CHANGELOG `#18` line, original Java-only `SpecCoverageService`). Marketplace stable 0.2.10 reflects `main` (Java-only). So withdrawing A required no reverts.

Mechanics (done / pending):
- **Done:** this removal lives on `change/remove-spec-code-annotations`, branched off `main`. The working tree therefore holds `main`'s original (v0.2.0 / Java-only) feature — exactly what's live in 0.2.10 — so the delta and code deletions are clean and independent of A's commits.
- **Pending:** delete the abandoned `change/fix-coverage-language-detection` branch (its language-agnostic commits never shipped, so nothing user-facing regresses).
- The editor delta REMOVES the two requirements from `main`'s spec (Java-worded). A's delta (which would have MODIFIED them) never applies — no conflict, because A never reaches `main`.
- **No CHANGELOG revert needed:** A's `#18`-fixed line is not on `main`. This change instead *adds* a "Removed" entry under the unreleased v0.3.0 section.

## GitHub #18 response

#18 reported "Coverage 0% on Kotlin." The resolution changes from "fixed (now language-agnostic)" to "we are removing the Coverage/`@spec` feature." Reply must be vendor-neutral (no internal tracker refs) and should:
- Thank the reporter; confirm the 0% was real (Java-only gating).
- Explain the decision: `@spec` and the Coverage panel are outside what the OpenSpec client models (OpenSpec has no code-annotation or spec-completion concept), so the plugin is retiring them rather than extending them, to stay aligned with OpenSpec.
- Point to OpenSpec's own `verify-change` workflow for a per-change completeness check, which needs no annotations.

## Verification

After implementation: `grep -rn "SpecCoverage\|SpecRefLineMarker\|@spec" src/` returns nothing in `src/main`; `./gradlew test` green (with the two feature tests deleted); `openspec validate remove-spec-code-annotations --strict` green; the tool window opens with no Coverage tab and no errors.
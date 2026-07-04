# Expand the automated UI walkthroughs

## Why

The first three UI smoke journeys automated the walkthrough's rendering spine (tool window, Update cleanup notice, Settings schemas), but the manual checklist still carries stops no automation covers: the validator-parity behavior in a real editor, the feedback dialog's guard against empty submissions, and the archive flow's incomplete-change confirmation. Manual test-drives keep stalling before reaching them — the same gap that motivated the first automation round.

## What Changes

- **Three additional journeys** on the existing Starter/Driver infrastructure, reusing the shared seeded fixture:
  1. *Editor validator parity:* open the seeded lowercase-header spec in the editor and assert via the highlighting daemon that the header draws no complaint; open a keyword-in-header-only fixture and assert the targeted diagnostic appears.
  2. *Feedback dialog guard:* invoke Send OpenSpec Feedback, assert the dialog renders, assert an empty message blocks submission, and leave via cancel — no message is ever sent.
  3. *Archive guard:* invoke Archive on the seeded incomplete change and assert the confirmation/warning surface appears, then cancel — the change directory is not moved.
- **Spec revision:** the `ui-smoke-journeys` suite-size language ("2–3") is updated to reflect a six-journey suite with the same policy (presence-level assertions, manual dispatch + release gating, never a per-PR blocker).
- Fixture gains one file (a keyword-in-header-only spec) added through the shared seeding script so the manual and automated environments stay in lockstep.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `ui-smoke-journeys`: suite grows to six journeys (editor parity, feedback guard, archive guard added); size language revised; policy unchanged.

## Impact

- **Code:** `OpenSpecUiSmokeTest` (or a sibling class) + `scripts/seed-lifecycle-demo.sh` (one more seeded spec file, regenerated committed fixture); possibly a small `@Remote` stub for the highlighting daemon.
- **Tests:** the journeys are the tests; each verified green locally before merge (the suite's own standard).
- **Docs:** testing-layers section journey list; walkthrough template cross-references which stops are automated.
- **Compatibility:** dev/CI-only; no shipped-plugin surface; per-PR CI and coverage floor untouched.

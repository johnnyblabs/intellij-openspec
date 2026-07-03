## Context

Phases 1–3 delivered the plugin's OpenSpec 1.4.x support. Two small defects remain in the tail, and they live in unrelated subsystems:

- **Verify completeness** (`VerificationService`) counts task checkboxes with two regexes — `- [ ]` (incomplete) and `- [x]` (complete) — and derives the total as their sum. An in-progress `- [~]` checkbox matches neither, so it is dropped from both the completed and the total counts. A change with only `[~]` tasks left can therefore report "ready to archive".
- **Version override** (settings UI) exposes a dropdown offering `1.3.0`/`1.4.0`. These feed only the **config-format** version axis, which is deliberately pinned at `1.2.0` and documented as independent of the CLI version (`VersionSupport.java:8-19`). Selecting them is a no-op that is silently ignored. An audit confirmed the override never reaches the CLI-version axis (that axis is auto-detected via `CliDetectionService` and gated separately by `CLI_FLOOR`), so nothing branches on the collapsed value — the only artifact is the misleading UI itself.

## Goals / Non-Goals

**Goals:**
- Verify treats `[~]` partial tasks as not-done: counted toward the total, blocking archive like `[ ]`, and surfaced distinctly.
- The version-override UI stops presenting config-format values the axis does not model.

**Non-Goals:**
- Changing the config-format pin (`1.2.0`) or adding `V1_3`/`V1_4` to `VersionSupport` — that would contradict the deliberate, documented pin.
- Raising the CLI floor above `1.3.0` (graceful degradation is intentional; a followup).
- Reworking the override into a CLI-version knob, or surfacing `init --profile` / workspace open-link (followups).

## Decisions

**1. `[~]` is not-done, tracked as a distinct in-progress bucket.**
Add a third pattern `^\s*-\s*\[~\]` for in-progress tasks. The task total becomes `complete + incomplete + inProgress`; the not-done set is `incomplete + inProgress`. Completeness raises its blocking finding whenever the not-done set is non-empty, and the finding wording distinguishes in-progress from unstarted (e.g. "N not done (M in progress)"). *Alternative rejected:* treating `[~]` as complete — an in-progress task is by definition not done, so counting it complete would let a change archive with open work.

**2. Fix the misleading override presets; leave the model alone.**
Remove `1.3.0`/`1.4.0` from the version-override combo, leaving the empty default (use config value) and the one modeled config-format value. The combo stays editable as an escape hatch for a custom config-format version, but no longer *advertises* values the axis silently discards. `VersionSupport`, `getEffectiveVersion`, and the `1.2.0` pin are untouched. *Alternatives rejected:* removing the override entirely (the escape-hatch has marginal value and removal is a larger UX change); rewiring the override to the CLI axis (out of scope, and the CLI version is already auto-detected).

**3. CLI floor unchanged.** No change to `CLI_FLOOR`/`MIN_CLI_VERSION` (`1.3.0`).

## Risks / Trade-offs

- **[Risk] The task-count change makes Verify stricter for changes that use `[~]`.** → This is the intended correction; it can only *add* a blocking finding that was previously (wrongly) suppressed — it can never turn a real pass into a false fail. Covered by tests asserting `[~]` counts toward not-done.
- **[Risk] Removing dropdown presets is a user-facing settings change.** → The removed values were non-functional (silently ignored), so no capability is lost; the editable field preserves manual entry. Documented in CHANGELOG.
- **[Trade-off] Not modeling 1.3/1.4 at all** leaves the config-format axis single-valued. → Correct by design; the CLI-version axis (the one that actually gates 1.4 behavior) is modeled and detected elsewhere.

## 1. Verify — count `[~]` partial tasks as not-done

- [x] 1.1 In `VerificationService`, add an in-progress task pattern (`^\s*-\s*\[~\]`) alongside the existing incomplete/complete patterns
- [x] 1.2 Update the completeness count so the total is `complete + incomplete + inProgress` and the blocking finding fires whenever `incomplete + inProgress > 0`
- [x] 1.3 Update the completeness finding wording to surface in-progress tasks distinctly (e.g. "N not done (M in progress)")
- [x] 1.4 Add/extend `VerificationServiceTest` cases: a `[~]` task counts toward the total and blocks archive; a mix of `[ ]`/`[x]`/`[~]` reports the correct counts; an all-`[x]` change is still clean

## 2. Version override — drop misleading presets

- [x] 2.1 In the settings panel, remove the `1.3.0`/`1.4.0` presets from the version-override combo, leaving the empty default (and the one modeled config-format value); keep the field editable as an escape hatch
- [x] 2.2 Confirm (via a test or by inspection) that `VersionSupport`, `getEffectiveVersion`, and the `1.2.0` config-format pin are untouched
- [x] 2.3 Add/extend a settings-panel test asserting the version-override combo no longer offers `1.3.0`/`1.4.0`

## 3. Validate & document

- [x] 3.1 Run `openspec validate --strict` for the change and `./gradlew build` (tests + coverage floor) green
- [x] 3.2 Update CHANGELOG.md (user-facing: `[~]` tasks now counted in Verify; version-override no longer lists non-functional versions) and any affected feature-reference/README docs

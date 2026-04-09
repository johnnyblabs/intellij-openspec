## Purpose

The `/release-prep` skill validates that a release is ready to tag by checking version alignment, changelog completeness, build health, change archive status, and tracker state across Forgejo and Plane.

## Requirements

### Requirement: Version and changelog validation

The `/release-prep` skill SHALL verify that `build.gradle.kts` contains the expected version and that `CHANGELOG.md` has a matching entry.

#### Scenario: Version matches
- **WHEN** the user runs `/release-prep v0.2.9`
- **THEN** the skill SHALL check that `build.gradle.kts` has `version = "0.2.9"` and `CHANGELOG.md` has `## v0.2.9`

#### Scenario: Version mismatch
- **WHEN** the version in `build.gradle.kts` does not match the requested release version
- **THEN** the skill SHALL report the mismatch as a blocker

#### Scenario: Missing changelog entry
- **WHEN** `CHANGELOG.md` has no entry for the release version
- **THEN** the skill SHALL report it as a blocker

### Requirement: Build verification

The `/release-prep` skill SHALL run `./gradlew build` and report pass/fail.

#### Scenario: Build passes
- **WHEN** the build succeeds
- **THEN** the skill SHALL report it as passing

#### Scenario: Build fails
- **WHEN** the build fails
- **THEN** the skill SHALL report it as a blocker with the failure output

### Requirement: Change archive validation

The `/release-prep` skill SHALL check that all changes associated with this release are archived (no active changes that reference the release version).

#### Scenario: All changes archived
- **WHEN** no active changes reference the release version
- **THEN** the skill SHALL report changes as clean

#### Scenario: Active changes remain
- **WHEN** active changes exist that should have been archived
- **THEN** the skill SHALL list them as blockers

### Requirement: Tracker state validation

The `/release-prep` skill SHALL check that all Forgejo issues in the release milestone are closed and all Plane work items in the release cycle are in the Done state.

#### Scenario: All issues closed
- **WHEN** every Forgejo issue in the release milestone is closed
- **THEN** the skill SHALL report Forgejo as clean

#### Scenario: Open issues remain
- **WHEN** open Forgejo issues exist in the release milestone
- **THEN** the skill SHALL list them as warnings (not blockers — some issues may span releases)

#### Scenario: All work items done
- **WHEN** every Plane work item in the release cycle is Done
- **THEN** the skill SHALL report Plane as clean

#### Scenario: Incomplete work items
- **WHEN** Plane work items in the release cycle are not Done
- **THEN** the skill SHALL list them as warnings

### Requirement: Milestone and cycle auto-creation

The `/release-prep` skill SHALL create a Forgejo milestone and Plane cycle for the release version if they do not already exist. Relevant issues/work items SHALL be reassigned from the catch-all milestone/cycle to the new release-specific one.

#### Scenario: Milestone does not exist
- **WHEN** no Forgejo milestone matches the release version
- **THEN** the skill SHALL create it and move relevant issues (those with matching archived changes) from the catch-all milestone

#### Scenario: Milestone already exists
- **WHEN** a Forgejo milestone already matches the release version
- **THEN** the skill SHALL use the existing milestone

#### Scenario: Cycle does not exist
- **WHEN** no Plane cycle matches the release version
- **THEN** the skill SHALL create it and move relevant work items from the catch-all cycle

### Requirement: Release readiness summary

The `/release-prep` skill SHALL display a structured readiness report with pass/fail/warning status for each check, and a clear recommendation on whether to proceed with tagging.

#### Scenario: All checks pass
- **WHEN** version, changelog, build, changes, and trackers all pass
- **THEN** the skill SHALL display "All clear. Ready to tag." with instructions for tagging

#### Scenario: Blockers found
- **WHEN** one or more checks are blockers
- **THEN** the skill SHALL display "N blockers found. Fix before tagging." with the blocker details

#### Scenario: Warnings only
- **WHEN** no blockers exist but warnings are present
- **THEN** the skill SHALL display the warnings and ask the user whether to proceed

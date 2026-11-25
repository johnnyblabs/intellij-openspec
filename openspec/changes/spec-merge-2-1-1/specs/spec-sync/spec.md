## ADDED Requirements

### Requirement: A new main spec follows the release's layout

When sync or the plugin's archive creates a new main spec, it SHALL write the Purpose layout of the release the plugin follows (the detected CLI, or the plugin's target release when none is detected), and read the delta's Purpose as that release reads it. The result SHALL be byte-identical to what that release's `openspec archive` writes for the same change. Existing main specs SHALL NOT change.

#### Scenario: CLI 2.1.1 or later
- **WHEN** the followed release is 2.1.1 or later and a delta creates a new capability
- **THEN** the new spec SHALL hold `## Purpose`, a blank line, then the Purpose body, matching the captured 2.1.1 archive output

#### Scenario: Before CLI 2.1.1
- **WHEN** the followed release is 2.1.0 or earlier and a delta creates a new capability
- **THEN** the Purpose body SHALL follow the `## Purpose` line directly, as before, matching the captured 2.1.0 and 1.14.3 archive output

#### Scenario: A byte-order mark or front matter around the delta's Purpose
- **WHEN** the delta starts with a byte-order mark, or has front matter holding a comment opener or a fence, before its `## Purpose`
- **THEN** the new spec's Purpose SHALL be the one that release's archive writes: from 2.1.1 the delta's own Purpose, and before 2.1.1 whatever the captured output of that release shows

#### Scenario: No CLI detected
- **WHEN** no CLI is detected
- **THEN** the new spec SHALL follow the plugin's target release

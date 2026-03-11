## ADDED Requirements

### Requirement: Post-archive commit and push

After archiving a change, the AI tool SHALL commit all changes (implementation code, archived change directory, synced specs) and push to the remote repository.

#### Scenario: Commit after archive
- **WHEN** a change has been archived via the archive workflow
- **THEN** the tool SHALL stage all modified and new files related to the change
- **AND** create a commit with a descriptive message referencing the change name
- **AND** push to the remote repository

#### Scenario: No changes to commit
- **WHEN** a change has been archived but all files were already committed
- **THEN** the tool SHALL skip the commit step and report that no changes were needed

### Requirement: Post-archive Forgejo issue update

After archiving a change, the AI tool SHALL close the related Forgejo issue with a completion comment.

#### Scenario: Matching Forgejo issue exists
- **WHEN** a change has been archived and a matching open Forgejo issue exists (by title or reference)
- **THEN** the tool SHALL add a completion comment summarizing what was done
- **AND** close the issue

#### Scenario: No matching Forgejo issue
- **WHEN** a change has been archived but no matching Forgejo issue is found
- **THEN** the tool SHALL skip the Forgejo update silently without error

### Requirement: Post-archive Plane work item update

After archiving a change, the AI tool SHALL move the related Plane work item to the "Done" state.

#### Scenario: Matching Plane work item exists
- **WHEN** a change has been archived and a matching Plane work item exists (by title or reference)
- **THEN** the tool SHALL update the work item state to "Done"

#### Scenario: No matching Plane work item
- **WHEN** a change has been archived but no matching Plane work item is found
- **THEN** the tool SHALL skip the Plane update silently without error

### Requirement: Post-archive Plane-Forgejo cross-linking

After archiving a change, the AI tool SHALL link the Plane work item to the Forgejo issue via the `external_id` field so both trackers reference each other.

#### Scenario: Both trackers have matching items
- **WHEN** a change has been archived and both a Forgejo issue and Plane work item were found
- **THEN** the tool SHALL set `external_id` to `forgejo-issue-<number>` and `external_source` to `forgejo` on the Plane work item

#### Scenario: Work item already linked
- **WHEN** a change has been archived and the Plane work item already has an `external_id` set
- **THEN** the tool SHALL skip the cross-linking step

#### Scenario: Only one tracker has a match
- **WHEN** a change has been archived but only one of Forgejo or Plane has a matching item
- **THEN** the tool SHALL skip the cross-linking step silently

### Requirement: Post-archive workflow is tool-agnostic

The post-archive workflow steps SHALL be defined in OpenSpec artifacts (config.yaml rules and specs), not in tool-specific configuration files.

#### Scenario: Rule defined in config.yaml
- **WHEN** any AI tool reads the project's openspec/config.yaml
- **THEN** the post-archive rule SHALL be present in the rules section

#### Scenario: All archive instruction files include post-archive steps
- **WHEN** any AI tool reads its archive command/prompt/skill file
- **THEN** the post-archive steps (commit, push, update trackers) SHALL be included

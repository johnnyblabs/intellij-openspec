## ADDED Requirements

### Requirement: Tree state survives a refresh

When the Changes tree is rebuilt for any reason, the plugin SHALL preserve the user's selection and expanded nodes. It SHALL match nodes by identity (node type plus change name, artifact id or file path), never by displayed label. It SHALL clear a selection only when that node is gone, and a failed rebuild SHALL leave the existing tree in place.

#### Scenario: Selection and preview survive a refresh
- **WHEN** a node is selected and the tree is rebuilt with that node still present
- **THEN** the same node SHALL be selected after the rebuild and scrolled into view
- **AND** the preview pane SHALL show that node's refreshed content, without showing the empty state in between

#### Scenario: Expansion survives a label change
- **WHEN** a change node is expanded and the tree is rebuilt with that change's task count, or one of its artifacts' labels, changed
- **THEN** the change node SHALL still be expanded after the rebuild

#### Scenario: Selected node no longer exists
- **WHEN** the selected node is absent from the rebuilt tree, for example because its change was archived or renamed
- **THEN** the selection SHALL be cleared, the preview SHALL show the empty state, and the workflow panel SHALL no longer show that change as active

#### Scenario: Failed rebuild keeps the tree
- **WHEN** building the new tree model fails while a tree is already displayed
- **THEN** the existing tree, its selection and its expansion SHALL remain unchanged, and nothing SHALL be shown to the user
- **AND** the plugin SHALL log the failure and retry the rebuild once after a short delay, so a transient failure (such as a file read mid-write) corrects itself without a further file change
- **AND** only when no tree has loaded yet SHALL the error node replace it

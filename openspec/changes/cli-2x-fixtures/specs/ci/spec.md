## ADDED Requirements

### Requirement: Contract fixtures record filesystem links as data

When a captured CLI layout contains symbolic links or directory junctions, the fixture SHALL record each link as data (its path and target, read without following it) rather than committing a real link. Committed fixtures SHALL contain no real links. Tests that need the links SHALL recreate them from the record, either as a real link, as a copied directory, or as the plain-text file a checkout without link support produces.

#### Scenario: A captured link is recorded, not committed
- **WHEN** a capture produces a tool directory that is a link into a shared skills directory
- **THEN** the fixture SHALL list that link's path and target in its layout record
- **AND** no file under the fixtures tree SHALL be a real link

#### Scenario: A real link in the fixtures fails the build
- **WHEN** a real symbolic link is added anywhere under the contract fixtures tree
- **THEN** the test suite SHALL fail and name the offending path

#### Scenario: Links are recreated in every supported form
- **WHEN** a test recreates a recorded layout
- **THEN** it SHALL be able to produce each link as a real link, a copied directory, or a plain-text file holding the target path
- **AND** where the platform cannot create real links, the real-link form SHALL be skipped rather than failed

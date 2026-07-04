## ADDED Requirements

### Requirement: Every document declares a maintenance class

Every tracked documentation file — each doc under `docs/` and the top-level README, CHANGELOG, and CONTRIBUTING — SHALL declare a maintenance class near the top of the file, one of: **Living** (updated as part of every relevant change), **Snapshot** (reviewed on a stated cadence and may lag between reviews), **Reference** (stable; updated only when the thing it describes changes), or **Retired** (kept for history, explicitly not maintained). The class makes each document's update contract explicit and auditable.

#### Scenario: Each doc carries a recognized maintenance label
- **WHEN** any tracked documentation file is read
- **THEN** it SHALL contain a `Maintenance:` label naming exactly one of the four classes (Living, Snapshot, Reference, Retired)

#### Scenario: A doc without a maintenance label is a hygiene failure
- **WHEN** a documentation-hygiene check scans the tracked docs
- **THEN** it SHALL fail if any doc lacks a `Maintenance:` label or uses a class outside the defined set

### Requirement: A documentation index maps every doc

The project SHALL maintain a documentation index at `docs/README.md` that lists every tracked documentation file with its purpose, primary audience, and maintenance class, and defines the four maintenance classes. The index SHALL disambiguate documents with similar names (in particular the version coverage matrix versus the competitive comparison matrix). The top-level README SHALL link to the index.

#### Scenario: The index covers every doc with no orphans
- **WHEN** the documentation index is compared to the files present under `docs/`
- **THEN** every doc file SHALL have an index entry and every index entry SHALL point to an existing doc

#### Scenario: The index disambiguates the two matrices
- **WHEN** a reader consults the index to find "the matrix"
- **THEN** the index SHALL distinguish the version/coverage matrix (`openspec-support.md`) from the competitive comparison matrix (`feature-comparison-matrix.md`) by purpose

### Requirement: Version facts have a single source of truth

The project SHALL designate one canonical statement of version/support facts — the "Version support" block in `docs/openspec-support.md` (current plugin version, minimum/baseline/supported CLI versions). Other documentation SHALL link to that canonical statement rather than restating specific version numbers, except where a version is intrinsic to the document (such as a CHANGELOG release heading).

#### Scenario: Other docs link rather than restate
- **WHEN** a doc other than the canonical source needs to refer to the supported CLI range or current plugin version
- **THEN** it SHALL link to the canonical Version support block rather than hard-coding the numbers

#### Scenario: A drifted version restatement is a hygiene failure
- **WHEN** a documentation-hygiene check scans for hard-coded plugin/CLI version literals outside the canonical source and allowed exceptions
- **THEN** it SHALL fail if a doc reintroduces a version literal that has drifted from the canonical source

### Requirement: Repo markdown is the canonical documentation tier

The project SHALL treat repository markdown as the canonical documentation source. Any published mirror of the documentation (a project wiki, a team knowledge base) is a generated mirror of the repo markdown and SHALL NOT be hand-edited to diverge from it. The documentation index SHALL state this tiering.

#### Scenario: Canonicality is stated
- **WHEN** a contributor reads the documentation index
- **THEN** it SHALL state that repo markdown is canonical and that wiki/knowledge-base copies are published mirrors that must not diverge

### Requirement: Release preparation enforces documentation currency

The release readiness process SHALL check documentation currency before a release is cut: it SHALL flag any `Snapshot` doc whose most recent change predates the previous release, and it SHALL warn when the release diff changed a tracked code area without a corresponding change to its declared Living doc. These are surfaced as release-readiness findings, not silent omissions.

#### Scenario: Stale Snapshot doc is flagged at release time
- **WHEN** release preparation runs and a `Snapshot` doc has not changed since before the previous release
- **THEN** the process SHALL surface it as a currency finding for review

#### Scenario: Living doc not updated with its code is warned
- **WHEN** a release diff changes a tracked code area that maps to a Living doc, and that doc was not changed in the same release window
- **THEN** the process SHALL warn that the Living doc may be stale

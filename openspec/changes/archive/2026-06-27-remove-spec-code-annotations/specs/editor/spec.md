# editor (delta)

## REMOVED Requirements

### Requirement: Spec gutter markers

**Reason:** Built on the `@spec <domain>:<requirement>` source annotation, which is a plugin invention with no basis in the OpenSpec client (zero upstream references; OpenSpec has no concept of annotating code). Code↔spec linking is not an OpenSpec concept, so the gutter-marker surface is retired entirely with no replacement. `SpecRefLineMarkerProvider` and its `plugin.xml` registration are deleted.

### Requirement: Spec coverage panel

**Reason:** Built on the same plugin-invented `@spec` annotation, and additionally applies change-style completion semantics ("N/M requirements covered, X%") to specs — a category error, since in OpenSpec specs are the living source of truth (no status/progress/completion concept) and completeness is judged transiently per-change by the `verify-change` workflow, not by counting code annotations. The Coverage tab, `SpecCoveragePanel`, `SpecCoverageService`, and `CoverageResult` are deleted with no replacement.

## ADDED Requirements

### Requirement: Per-CLI-version feature-delta analysis grounds support decisions

Before or as part of adding plugin support for an OpenSpec CLI version, the plugin SHALL produce a **per-version feature-delta & plugin-impact analysis** — a durable document under `docs/cli-versions/` cataloguing the features **introduced, modified, deprecated, and removed** in that CLI version relative to the prior supported version. This analysis is the epistemic base of the capability-preservation contract: the plugin cannot faithfully mirror a client version it has not analysed.

The analysis SHALL:
- **Cite upstream.** Every claim about the CLI's behavior SHALL be cited to Fission's upstream OpenSpec documentation (changelog, releases, or docs). Claims that cannot be verified upstream SHALL be quarantined in an explicit "open questions / to verify" section and SHALL NOT be asserted as fact.
- **Assess plugin impact per feature.** For each feature it SHALL record the practical mechanics, an assessment of whether the plugin can and should surface it (including whether a plugin UI component exists or is warranted), and a consumer how-to (through the plugin, or via the CLI where there is no UI surface).
- **Drive decisions.** Plugin support decisions for that CLI version (what to build, what to leave CLI-only, what to gate) SHALL reference this analysis rather than be made from assumption.
- **Stay current.** It SHALL be updated when the plugin's understanding of that version changes, and carry a maintenance label per the documentation-maintenance framework.

The analysis is produced via an OpenSpec **explore** (research) pass over the upstream documentation. A `docs/cli-versions/` index SHALL list the per-version analyses.

#### Scenario: Analysis precedes support for a CLI version
- **WHEN** the plugin adds or changes support for an OpenSpec CLI version
- **THEN** a per-version feature-delta analysis for that version SHALL exist under `docs/cli-versions/`, and the implementing change SHALL reference it

#### Scenario: Upstream claims are cited; unverified claims are quarantined
- **WHEN** the analysis states a fact about the CLI's behavior in a version
- **THEN** that fact SHALL carry a citation to upstream OpenSpec documentation, and any claim not verifiable upstream SHALL appear only under an "open questions / to verify" section, not as an asserted fact

#### Scenario: Each feature carries a supportability and UI assessment
- **WHEN** a feature appears in the analysis
- **THEN** it SHALL record whether the plugin supports it, how (built-in / delegated / read-only / n-a), whether a plugin UI component exists or is warranted, and a consumer how-to

#### Scenario: Decisions reference the analysis
- **WHEN** a plugin change decides to build, defer, or gate support for a CLI-version feature
- **THEN** the decision SHALL be traceable to the per-version analysis rather than to assumption

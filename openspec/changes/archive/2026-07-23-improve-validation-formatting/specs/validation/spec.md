## ADDED Requirements

### Requirement: Validation result presentation

The plugin SHALL present validation results in the OpenSpec console as a structured, navigable report rather than a single uniform-color text block. Results SHALL be grouped by file: each distinct issue file path SHALL be a group header, with that file's issues listed beneath it. Files containing at least one ERROR SHALL be ordered before warning-only files, then info-only files; within a group, issues SHALL be ordered by line ascending. Each issue's reported location (`line`, or the file path in the group header) SHALL render as a clickable hyperlink that opens the file at the reported line in the editor when the path resolves to a file on disk. The presentation SHALL reflect only the existing issue model — severity is exactly ERROR, WARNING, or INFO, and each issue carries a file path, line, message, and rule — and SHALL NOT introduce a per-file pass/fail verdict (file headers are grouping keys only) or any new severity. Each severity SHALL render in a distinct, theme-driven console content type. Rendering SHALL run on the UI thread, consistent with the existing result-display path.

This presentation SHALL be produced by the single shared result-rendering path, so every caller — the toolbar Validate, the Project-View scoped Validate, and any future caller — benefits identically. The at-a-glance notification balloon SHALL remain the summary surface and SHALL NOT enumerate individual issues; the console SHALL be the detailed, navigable surface.

#### Scenario: Header states verdict, target, and counts
- **WHEN** a validation run completes for a target (whole project, a change, or a spec)
- **THEN** the console SHALL show a verdict line naming the target (e.g. `Validation FAILED — Change <name>` or `Validation PASSED — <target>`) followed by a count line summarizing the number of errors, warnings, and infos

#### Scenario: Issues grouped by file, errors-first
- **WHEN** a failing result contains issues across multiple files
- **THEN** the console SHALL present each file as a group header with its issues beneath, ordering files that contain an ERROR before warning-only files and info-only files, and ordering issues within a file by line ascending

#### Scenario: Resolvable location is a clickable hyperlink
- **WHEN** an issue carries a file path that resolves to a file on disk and a 1-based line
- **THEN** the console SHALL render that issue's location as a hyperlink that, when clicked, opens the file with the caret at the reported line (mapping the 1-based line to the editor's 0-based position)

#### Scenario: Unresolvable location degrades to plain text
- **WHEN** an issue carries a path that does not resolve to a file on disk (e.g. a CLI-reported `type/id` pseudo-path) or has no usable line
- **THEN** the console SHALL render that issue as plain, still-severity-colored text with no hyperlink, rather than a dead link or an error

#### Scenario: Severity determines color
- **WHEN** issues of differing severity are rendered
- **THEN** ERROR, WARNING, and INFO SHALL each render in a distinct, theme-driven console content type so severity is distinguishable, and no new severity beyond ERROR/WARNING/INFO SHALL be introduced

#### Scenario: Clean pass shows a concise confirmation
- **WHEN** a validation run passes with zero issues
- **THEN** the console SHALL show a concise pass confirmation naming the target and a "no issues" line, rather than an empty or ambiguous block

#### Scenario: Passing-with-warnings still lists the warnings
- **WHEN** a run passes (no errors) but reports one or more warnings or infos
- **THEN** the console SHALL show the pass verdict and count line and still list the warning/info issues grouped by file

#### Scenario: Presentation invents no per-file verdict
- **WHEN** results are grouped under file headers
- **THEN** the file headers SHALL serve only as grouping keys and SHALL NOT display a per-file valid/invalid badge, because the model carries a single result-level verdict and no per-file verdict

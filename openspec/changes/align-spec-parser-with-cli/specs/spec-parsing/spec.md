## ADDED Requirements

### Requirement: Code-fence exclusion

The spec parser SHALL exclude the contents of fenced code blocks from all structural recognition. A line SHALL be treated as fenced when it falls between a fence-opening line matching a run of three-or-more backticks or tildes (optionally indented, with an optional info string) and the matching fence-closing line whose marker character matches and whose run length is greater than or equal to the opening run; the fence delimiter lines themselves SHALL be excluded. Requirement headers, scenario headers, and normative keywords appearing on fenced lines SHALL NOT be recognized.

#### Scenario: Requirement header inside a fence is ignored
- **WHEN** a line that would otherwise match a requirement header appears inside a fenced code block
- **THEN** the parser SHALL NOT count it as a requirement

#### Scenario: Scenario header inside a fence is ignored
- **WHEN** a level-4 header appears inside a fenced code block
- **THEN** the parser SHALL NOT count it as a scenario

#### Scenario: Normative keyword inside a fence is not normative
- **WHEN** the token `SHALL` or `MUST` appears only inside a fenced code block within a requirement
- **THEN** the parser SHALL NOT treat that requirement as containing a normative keyword

### Requirement: Requirement recognition matches the CLI

The parser SHALL recognize a requirement from a non-fenced ATX level-3 header whose text begins with `Requirement:` (case-insensitive on the `Requirement:` token), located under the spec's requirements section. Header recognition SHALL require the ATX form with the hash run at the start of the line followed by whitespace; setext-underlined headers, indented headers, and trailing-hash closing forms SHALL NOT be recognized. The requirement name SHALL be the header text following the `Requirement:` token.

#### Scenario: Case-insensitive requirement header
- **WHEN** a spec contains a non-fenced level-3 header reading `requirement:` in lowercase followed by a name
- **THEN** the parser SHALL recognize it as a requirement with that name

#### Scenario: Setext-underlined header is not a requirement
- **WHEN** a requirement-like line is underlined with `===` or `---` rather than prefixed with hashes
- **THEN** the parser SHALL NOT recognize it as a requirement

#### Scenario: Indented header is not a requirement
- **WHEN** a level-3 requirement header line is preceded by leading spaces
- **THEN** the parser SHALL NOT recognize it as a requirement

### Requirement: Scenario recognition matches the CLI

The parser SHALL count any non-fenced ATX level-4 header within a requirement as a scenario, regardless of the header text. The parser SHALL NOT recognize the bold `**Scenario:**` inline form as a scenario, and SHALL NOT require the header text to begin with `Scenario:`.

#### Scenario: Any level-4 header counts as a scenario
- **WHEN** a requirement contains a non-fenced level-4 header whose text does not begin with `Scenario:`
- **THEN** the parser SHALL count it as a scenario

#### Scenario: Bold scenario form is not a scenario
- **WHEN** a requirement body contains a bold `**Scenario:**` line rather than a level-4 header
- **THEN** the parser SHALL NOT count it as a scenario

### Requirement: Normative-keyword recognition matches the CLI

The parser SHALL classify a requirement as containing a normative keyword when, and only when, the whole-word token `SHALL` or `MUST` appears in the requirement body. Keyword matching SHALL be case-sensitive (uppercase only), and SHALL be evaluated against the requirement body — the non-fenced, non-blank lines between the requirement header and its first scenario or the next requirement — and not against scenario content or the header line. The tokens `SHOULD` and `MAY` SHALL NOT be treated as normative.

#### Scenario: MUST in the body is normative
- **WHEN** a requirement body contains the uppercase whole word `MUST`
- **THEN** the parser SHALL classify the requirement as containing a normative keyword

#### Scenario: Lowercase keyword is not normative
- **WHEN** a requirement body contains `shall` or `must` in lowercase only
- **THEN** the parser SHALL NOT classify the requirement as containing a normative keyword

#### Scenario: SHOULD and MAY are not normative
- **WHEN** a requirement body contains `SHOULD` or `MAY` but neither `SHALL` nor `MUST`
- **THEN** the parser SHALL NOT classify the requirement as containing a normative keyword

#### Scenario: Keyword only in the header is not counted on the body
- **WHEN** the only occurrence of `SHALL` or `MUST` is on the requirement header line
- **THEN** the parser SHALL NOT classify the requirement body as containing a normative keyword

### Requirement: Structural parity with the CLI

For a main spec file, the parser's recovered title, requirement count, and per-requirement scenario count SHALL match the structure the OpenSpec CLI reports for the same file. This parity SHALL be verifiable against output captured from the real CLI rather than against hand-authored expectations.

#### Scenario: Counts match captured CLI output
- **WHEN** a spec file is parsed and the same file is described by the OpenSpec CLI's structural output
- **THEN** the parser's title, requirement count, and each requirement's scenario count SHALL equal the corresponding values in the CLI output

### Requirement: Line-ending invariance

The parser SHALL produce the same recovered structure regardless of whether the spec file uses LF, CRLF, or a trailing lone CR line ending.

#### Scenario: CRLF parses identically to LF
- **WHEN** the same spec content is parsed once with LF line endings and once with CRLF line endings
- **THEN** the parser SHALL recover identical structure (title, requirements, and scenarios) in both cases

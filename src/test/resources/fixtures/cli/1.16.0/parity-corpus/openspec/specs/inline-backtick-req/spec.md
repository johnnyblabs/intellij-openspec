# Inline Backtick Parity

## Purpose

A single-line triple-backtick span is inline code, not a code fence (CLI 1.16 parity).

## Requirements

### Requirement: Inline Code Does Not Open A Fence

Run the suite with the following inline code span:

```npm test```

#### Scenario: Inline span is text

- **WHEN** the spec is parsed
- **THEN** the inline span does not hide the requirement after it

### Requirement: Later Requirement Stays Visible

The system SHALL keep recognizing requirements after an inline code span.

#### Scenario: Later requirement is counted

- **WHEN** the spec is parsed
- **THEN** this requirement is counted

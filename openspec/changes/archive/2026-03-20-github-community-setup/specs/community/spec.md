## ADDED Requirements

### Requirement: Repository includes contribution guidelines
The project SHALL include a CONTRIBUTING.md at the project root covering development setup, coding standards, and the pull request process.

#### Scenario: New contributor finds setup instructions
- **WHEN** a developer visits the repository for the first time
- **THEN** CONTRIBUTING.md provides steps to clone, build, and run the plugin locally

#### Scenario: PR expectations are documented
- **WHEN** a contributor prepares a pull request
- **THEN** CONTRIBUTING.md describes the expected PR format, review process, and any required checks

### Requirement: Repository includes code of conduct
The project SHALL include a CODE_OF_CONDUCT.md based on the Contributor Covenant v2.1.

#### Scenario: Code of conduct is accessible
- **WHEN** a user visits the repository
- **THEN** CODE_OF_CONDUCT.md is present at the project root and linked from CONTRIBUTING.md

### Requirement: Repository includes security policy
The project SHALL include a SECURITY.md describing how to report security vulnerabilities.

#### Scenario: Vulnerability reporting is documented
- **WHEN** a security researcher discovers a vulnerability
- **THEN** SECURITY.md provides a private reporting channel (email, not public issue)

### Requirement: Issues use structured templates
The repository SHALL provide GitHub issue templates for bug reports and feature requests.

#### Scenario: Bug report template
- **WHEN** a user creates a new issue and selects "Bug Report"
- **THEN** they are presented with a structured form including environment, steps to reproduce, expected behavior, and actual behavior

#### Scenario: Feature request template
- **WHEN** a user creates a new issue and selects "Feature Request"
- **THEN** they are presented with a structured form including problem description, proposed solution, and alternatives considered

### Requirement: Pull requests use a template
The repository SHALL provide a pull request template with a checklist.

#### Scenario: PR template is auto-populated
- **WHEN** a contributor opens a new pull request
- **THEN** the PR body is pre-populated with a checklist covering description, testing, and documentation

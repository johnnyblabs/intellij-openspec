# Licensing

## Purpose
Apache 2.0 licensing and public source code accessibility for the plugin.

## Requirements

### Requirement: Plugin ships with Apache 2.0 license
The project SHALL include an Apache 2.0 LICENSE file at the repository root and a NOTICE file with copyright attribution. All distributed artifacts SHALL reference this license.

#### Scenario: LICENSE file present
- **WHEN** a user clones the repository or downloads the plugin source
- **THEN** a LICENSE file containing the full Apache License 2.0 text exists at the project root

#### Scenario: NOTICE file present
- **WHEN** a user clones the repository
- **THEN** a NOTICE file exists at the project root containing the copyright holder name and year

### Requirement: Source code is publicly accessible
The project SHALL be published to a public GitHub repository. The JetBrains Marketplace listing SHALL link to this repository.

#### Scenario: Marketplace links to source
- **WHEN** a user views the plugin on JetBrains Marketplace
- **THEN** the listing includes a link to the public GitHub repository

#### Scenario: No secrets in public repository
- **WHEN** the repository is pushed to GitHub
- **THEN** no credentials, API keys, tokens, or private server URLs are present in the committed files or git history
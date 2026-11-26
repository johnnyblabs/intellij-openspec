## ADDED Requirements

### Requirement: The npm route states upstream's Node floor

Where an install hint names the npm route, it SHALL state the Node.js version the CLI's package requires: 22.20.0 or later, as `engines.node` declares from CLI 2.1.0.

#### Scenario: npm hint names Node 22.20
- **WHEN** the plugin shows an install hint that includes `npm install -g @fission-ai/openspec@latest`
- **THEN** it SHALL say the npm route needs Node.js 22.20 or later

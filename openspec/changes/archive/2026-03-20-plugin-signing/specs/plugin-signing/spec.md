## ADDED Requirements

### Requirement: Plugin signing configuration

The build SHALL configure `signPlugin` with private key, certificate chain, and password sourced from environment variables. Signing SHALL be skipped gracefully when credentials are not present (local development).

#### Scenario: Signing with credentials present
- **WHEN** `PLUGIN_SIGNING_KEY`, `PLUGIN_SIGNING_CERTIFICATE`, and `PLUGIN_SIGNING_KEY_PASSWORD` environment variables are set
- **THEN** the `signPlugin` task SHALL sign the plugin ZIP archive

#### Scenario: Signing skipped without credentials
- **WHEN** signing environment variables are not set
- **THEN** the build SHALL complete without signing and without error

### Requirement: Signature verification

The build SHALL verify the signed plugin archive after signing to confirm the signature is valid.

#### Scenario: Signature valid
- **WHEN** the plugin ZIP is signed
- **THEN** `verifyPluginSignature` SHALL confirm the signature is valid

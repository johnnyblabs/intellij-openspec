## MODIFIED Requirements

### Requirement: Release pipeline

The release pipeline SHALL be the exclusive mechanism for signing and publishing the plugin to JetBrains Marketplace. It SHALL trigger automatically when a `v*` tag is pushed, and SHALL build, sign, publish, and create a GitHub Release in a single workflow run. The build step SHALL fail if `CHANGELOG.md` does not contain an entry matching the tagged version, preventing publication with stale or missing release notes.

#### Scenario: Tag triggers release
- **WHEN** a tag matching `v*` is pushed to the repository
- **THEN** the release pipeline SHALL start automatically

#### Scenario: Build and test before publish
- **WHEN** the release pipeline runs
- **THEN** it SHALL execute `gradle build` (compile and test) before attempting to sign or publish

#### Scenario: Missing changelog entry blocks release
- **WHEN** the release pipeline runs and `CHANGELOG.md` has no entry matching the tagged version
- **THEN** the build SHALL fail before signing or publishing, preventing stale release notes on the Marketplace

#### Scenario: Plugin signing
- **WHEN** the release pipeline runs
- **THEN** it SHALL sign the plugin using `PLUGIN_SIGNING_KEY`, `PLUGIN_SIGNING_CERTIFICATE`, and `PLUGIN_SIGNING_KEY_PASSWORD` from repository secrets

#### Scenario: Marketplace publishing
- **WHEN** the plugin is signed successfully
- **THEN** the pipeline SHALL publish the signed plugin to JetBrains Marketplace using the `JETBRAINS_MARKETPLACE_TOKEN` secret

#### Scenario: GitHub Release creation
- **WHEN** the plugin is published to the Marketplace
- **THEN** the pipeline SHALL create a GitHub Release for the tag, attaching the signed plugin zip and including release notes

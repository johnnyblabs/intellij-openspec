## Why

JetBrains Marketplace supports plugin signing — signed plugins get a verified badge and JetBrains is moving toward requiring signatures. The plugin already has `signPlugin` and `verifyPluginSignature` Gradle tasks available but no signing key is configured and the CI pipeline doesn't sign builds.

## What Changes

- Generate a signing key pair for JetBrains Marketplace
- Configure `signPlugin` in `build.gradle.kts` with environment variable-based credentials
- Add a `sign` step to the CI workflow that signs the plugin ZIP after build
- Upload the public certificate to JetBrains Marketplace

## Capabilities

### New Capabilities

- `plugin-signing`: Plugin ZIP signing configuration and CI integration

### Modified Capabilities

- `ci`: Add sign step to the build workflow

## Impact

- `build.gradle.kts` — add signing configuration block with env var references
- `.forgejo/workflows/build.yaml` — add sign step after build on main
- Runner secrets — need `PLUGIN_SIGNING_KEY`, `PLUGIN_SIGNING_CERTIFICATE`, `PLUGIN_SIGNING_KEY_PASSWORD`

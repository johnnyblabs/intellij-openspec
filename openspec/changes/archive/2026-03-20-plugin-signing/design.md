## Context

The IntelliJ Platform Gradle Plugin provides `signPlugin` and `verifyPluginSignature` tasks. Signing requires a private key, certificate chain, and password. JetBrains documents the process at the Marketplace docs. The existing `build.gradle.kts` has no signing configuration beyond the empty `pluginVerification` block.

## Goals / Non-Goals

**Goals:**
- Sign every plugin ZIP built on `main` so Marketplace uploads are always signed
- Store credentials as CI secrets, never in the repo
- Verify the signature as a post-sign step

**Non-Goals:**
- Signing local development builds (only CI)
- Automating key rotation
- Changing the publish workflow (that's a separate concern)

## Decisions

### Environment variable-based credentials
**Decision:** Use `PLUGIN_SIGNING_KEY`, `PLUGIN_SIGNING_CERTIFICATE`, and `PLUGIN_SIGNING_KEY_PASSWORD` environment variables read via `providers.environmentVariable()`.
**Rationale:** Follows JetBrains' recommended pattern. Keeps secrets out of the repo. Forgejo Actions supports secrets natively.
**Alternative:** File-based keys checked into a private submodule. Rejected — adds complexity and a second repo to manage.

### Sign only on main
**Decision:** Only sign builds from the `main` branch (matching the existing publish gate).
**Rationale:** PR builds don't need signing — it wastes time and the signed artifact isn't published. Signing only on main keeps PR feedback fast.

### Generate key with JetBrains Marketplace CLI
**Decision:** Use `openssl` to generate the key pair per JetBrains documentation.
**Rationale:** Standard approach, well-documented, no extra tooling needed.

## Risks / Trade-offs

- [Key compromise] → Store private key only in CI secrets. Rotate if compromised. JetBrains allows certificate revocation.
- [CI secret management] → Forgejo Actions secrets are encrypted at rest. Acceptable risk for the trust signal gained.
- [Build time] → Signing adds ~10s. Negligible.

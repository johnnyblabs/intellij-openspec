## 1. Key Generation

- [ ] 1.1 Generate private key and certificate chain using openssl per JetBrains docs
- [ ] 1.2 Upload public certificate to JetBrains Marketplace plugin settings

## 2. Build Configuration

- [x] 2.1 Add `signing` block to `intellijPlatform` in `build.gradle.kts` reading key, certificate, and password from environment variables
- [x] 2.2 Ensure build succeeds without signing credentials (graceful skip for local dev)

## 3. CI Integration

- [ ] 3.1 Add `PLUGIN_SIGNING_KEY`, `PLUGIN_SIGNING_CERTIFICATE`, `PLUGIN_SIGNING_KEY_PASSWORD` as Forgejo repository secrets
- [x] 3.2 Add `signPlugin` step to build workflow on main (after build, before artifact upload)
- [x] 3.3 Add `verifyPluginSignature` step after signing
- [x] 3.4 Update artifact upload to use the signed ZIP

## 4. Verification

- [ ] 4.1 Push to main and confirm signed artifact is produced
- [ ] 4.2 Publish signed build to Marketplace and confirm verified badge appears

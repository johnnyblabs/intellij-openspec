# Manual Tasks — CI & Signing

Two changes have in-repo code complete but need manual steps on your machines.
This guide tells you what to do, where to do it, and why.

---

## Machine Legend

| Label | Machine | Role |
|-------|---------|------|
| **MacBook** | MacBook M3 | Development, key generation, local testing |
| **Server** | Linux Docker host | Forgejo, CI runner (`java-21`), container images |

---

## 1. ci-verify-plugin

Code is done (2 lines removed from `build.yaml`). Just needs verification after push.

### Task: Verify parallel CI jobs

**Where:** MacBook (push) → Server (CI runs) → Forgejo web UI (observe)

| Step | Command / Action | Why |
|------|-----------------|-----|
| Push the branch | `git push -u origin change/v030-phase3-features` | Triggers CI with the updated workflow |
| Check Forgejo CI | Open the push/PR in Forgejo web UI | Confirm `build` and `verify` jobs start simultaneously (no dependency arrow between them) |
| Verify PR status | Open a PR targeting main | Confirm `verify` appears as a required status check |

**Why this matters:** Plugin Verifier was only running after merge to main. Binary compatibility breaks (e.g., a removed IntelliJ API in 2025.1) went undetected until code was already merged. Running on every push catches these in PRs.

---

## 2. plugin-signing

### Task 1: Generate signing key pair

**Where:** MacBook

```bash
# Generate 4096-bit RSA private key
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 \
  -out plugin-signing-key.pem

# Generate certificate signing request
openssl req -new -key plugin-signing-key.pem \
  -out plugin-signing-csr.pem \
  -subj "/CN=johnnyblabs/O=johnnyblabs"

# Self-sign the certificate (10-year validity)
openssl x509 -req -days 3650 \
  -in plugin-signing-csr.pem \
  -signkey plugin-signing-key.pem \
  -out plugin-signing-cert.pem

# Base64-encode for environment variables
base64 -i plugin-signing-key.pem > plugin-signing-key.b64
base64 -i plugin-signing-cert.pem > plugin-signing-cert.b64

echo "Key and certificate generated. Store these files safely."
echo "DO NOT commit .pem or .b64 files to the repo."
```

**Why:** JetBrains Marketplace uses asymmetric signing to verify plugin authenticity. The private key signs the ZIP; the public certificate lets JetBrains (and users) verify it wasn't tampered with. A 10-year cert avoids frequent rotation.

### Task 2: Add secrets to Forgejo

**Where:** MacBook (browser) → Forgejo web UI

1. Go to `http://forgejo.geek/johnb/OpenSpecPlugin/settings/actions/secrets`
2. Add three secrets:

| Secret Name | Value (paste from) | Notes |
|-------------|-------------------|-------|
| `PLUGIN_SIGNING_KEY` | Contents of `plugin-signing-key.b64` | Base64-encoded private key |
| `PLUGIN_SIGNING_CERTIFICATE` | Contents of `plugin-signing-cert.b64` | Base64-encoded certificate |
| `PLUGIN_SIGNING_KEY_PASSWORD` | *(empty string or passphrase)* | Leave empty if key has no passphrase |

**Why:** CI needs the signing credentials at build time but they must never be in the repo. Forgejo Actions secrets are encrypted at rest and only exposed to workflow runs.

### Task 3: Verify signing works

**Where:** MacBook (push) → Server (CI runs) → Forgejo (observe)

1. Merge to main (or push directly if testing)
2. Check Forgejo CI — the build job should now have `Sign plugin` and `Verify plugin signature` steps
3. Download the artifact — filename should end in `-signed.zip`
4. Publish to Marketplace and check for the verified badge

**Why:** Signed plugins get a trust badge on the Marketplace. JetBrains is moving toward requiring signatures — setting this up now avoids a scramble later.

---

## Summary: What goes where

| Task | MacBook | Server | Forgejo UI |
|------|---------|--------|------------|
| Generate signing key | `openssl` commands | | |
| Add Forgejo secrets | | | settings page |
| Verify CI jobs | `git push` | runs the jobs | observe results |

---

## After completing all manual tasks

Mark the remaining checkboxes in each change's `tasks.md`, then archive:

```
/opsx:archive ci-verify-plugin
/opsx:archive plugin-signing
```

## Cleanup

After signing key generation, securely store or delete the `.pem` and `.b64` files:

```bash
# Move to a secure location (NOT the repo)
mv plugin-signing-*.pem plugin-signing-*.b64 ~/secrets/openspec/

# Or delete after adding to Forgejo secrets
rm plugin-signing-key.pem plugin-signing-csr.pem plugin-signing-cert.pem
rm plugin-signing-key.b64 plugin-signing-cert.b64
```

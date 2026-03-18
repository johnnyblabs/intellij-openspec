# Manual Tasks — CI, Signing & Qodana

Three changes have in-repo code complete but need manual steps on your machines.
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

### Task 2: Upload certificate to JetBrains Marketplace

**Where:** MacBook (browser)

1. Go to https://plugins.jetbrains.com/plugin/30678-openspec/edit
2. Navigate to the **Signing** or **Keys** section
3. Upload `plugin-signing-cert.pem` (the public certificate, NOT the private key)

**Why:** The Marketplace needs your public certificate to verify signatures on uploaded ZIPs. This is a one-time step — the cert is valid for 10 years.

### Task 3: Add secrets to Forgejo

**Where:** MacBook (browser) → Forgejo web UI

1. Go to `http://forgejo.geek/johnb/OpenSpecPlugin/settings/actions/secrets`
2. Add three secrets:

| Secret Name | Value (paste from) | Notes |
|-------------|-------------------|-------|
| `PLUGIN_SIGNING_KEY` | Contents of `plugin-signing-key.b64` | Base64-encoded private key |
| `PLUGIN_SIGNING_CERTIFICATE` | Contents of `plugin-signing-cert.b64` | Base64-encoded certificate |
| `PLUGIN_SIGNING_KEY_PASSWORD` | *(empty string or passphrase)* | Leave empty if key has no passphrase |

**Why:** CI needs the signing credentials at build time but they must never be in the repo. Forgejo Actions secrets are encrypted at rest and only exposed to workflow runs.

### Task 4: Verify signing works

**Where:** MacBook (push) → Server (CI runs) → Forgejo (observe)

1. Merge to main (or push directly if testing)
2. Check Forgejo CI — the build job should now have `Sign plugin` and `Verify plugin signature` steps
3. Download the artifact — filename should end in `-signed.zip`
4. Publish to Marketplace and check for the verified badge

**Why:** Signed plugins get a trust badge on the Marketplace. JetBrains is moving toward requiring signatures — setting this up now avoids a scramble later.

---

## 3. qodana-analysis

### Task 1: Install Qodana CLI on runner image

**Where:** Server (Docker host)

Update your `java-21` runner Dockerfile:

```dockerfile
# Add Qodana CLI
RUN curl -fsSL https://jb.gg/qodana-cli/install | bash
```

Then rebuild and restart the runner:

```bash
docker build -t java-21 .
# Restart the Forgejo runner container to pick up the new image
```

Verify it works:

```bash
docker run --rm java-21 qodana version
```

**Why:** Qodana is JetBrains' static analysis engine, specifically tuned for IntelliJ platform code. It catches deprecated API usage, null safety issues, and Java quality problems that generic linters miss. The CLI approach avoids Docker-in-Docker complexity on your runner.

### Task 2: Generate baseline

**Where:** MacBook (if Qodana CLI installed) OR Server

Option A — Run locally on MacBook:
```bash
# Install Qodana CLI
brew install jetbrains/utils/qodana

# Run analysis and generate baseline
qodana scan --save-report --report-dir build/qodana-report

# The SARIF report becomes your baseline
cp build/qodana-report/qodana.sarif.json qodana-baseline.sarif.json
```

Option B — Run on server via Docker:
```bash
docker run --rm \
  -v "$(pwd)":/data/project \
  -v "$(pwd)/build/qodana-report":/data/results \
  jetbrains/qodana-jvm:latest \
  --save-report

cp build/qodana-report/qodana.sarif.json qodana-baseline.sarif.json
```

Then commit the baseline:
```bash
git add qodana-baseline.sarif.json
git commit -m "Add Qodana baseline for existing issues"
```

**Why:** The baseline captures all existing issues so they don't block PRs. Only *new* issues (introduced after the baseline) will fail CI. This lets you adopt Qodana without fixing 100+ existing warnings first — you can chip away at them over time.

### Task 3: Verify Qodana CI

**Where:** MacBook (push) → Server (CI runs) → Forgejo (observe)

1. Open a PR targeting main
2. Confirm the `qodana` job runs (it only runs on PRs, not pushes)
3. Check that it passes (no new issues beyond baseline)
4. Optionally: introduce a deliberate issue (e.g., unused variable) and confirm the job fails

**Why:** Validates the full pipeline works end-to-end before relying on it.

---

## Summary: What goes where

| Task | MacBook | Server | Forgejo UI |
|------|---------|--------|------------|
| Generate signing key | `openssl` commands | | |
| Upload cert to Marketplace | browser | | |
| Add Forgejo secrets | | | settings page |
| Install Qodana on runner | | Dockerfile update | |
| Generate Qodana baseline | `brew install qodana` | or via Docker | |
| Verify CI jobs | `git push` | runs the jobs | observe results |

---

## After completing all manual tasks

Mark the remaining checkboxes in each change's `tasks.md`, then archive:

```
/opsx:archive ci-verify-plugin
/opsx:archive plugin-signing
/opsx:archive qodana-analysis
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

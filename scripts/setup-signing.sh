#!/usr/bin/env zsh
# setup-signing.sh — Generate plugin signing key and configure CI secrets
#
# Idempotent: skips key generation if keys exist, updates secrets in place
#
# Keys stored at: ~/.ssh/openspec/
# Secrets set on: Forgejo and GitHub repos via API
#
# Uses OPENSPEC_PLUGIN_PASSPHRASE env var if set, otherwise prompts interactively

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
source "${SCRIPT_DIR}/lib/common.sh"

check_deps
load_env

KEY_DIR="$HOME/.ssh/openspec"
KEY_FILE="${KEY_DIR}/plugin-signing-key.pem"
CERT_FILE="${KEY_DIR}/plugin-signing-cert.pem"
CSR_FILE="${KEY_DIR}/plugin-signing-csr.pem"

REPO="johnb/intellij-openspec"

echo ""
echo "========================================"
echo "  OpenSpec — Plugin Signing Setup"
echo "========================================"
echo ""

# ------------------------------------------------------------------
# 1. Generate key pair (if not already present)
# ------------------------------------------------------------------
if [[ -f "$KEY_FILE" && -f "$CERT_FILE" ]]; then
  log_success "Signing key already exists at ${KEY_DIR}/"
  log_info "To regenerate, delete ${KEY_DIR}/ and re-run this script"
else
  log_info "Step 1: Generating signing key pair..."
  mkdir -p "$KEY_DIR"
  chmod 700 "$KEY_DIR"

  if [[ -n "${OPENSPEC_PLUGIN_PASSPHRASE:-}" ]]; then
    KEY_PASSWORD="$OPENSPEC_PLUGIN_PASSPHRASE"
    log_info "Using passphrase from OPENSPEC_PLUGIN_PASSPHRASE environment variable"
  else
    echo ""
    echo "  Choose a passphrase for the signing key."
    echo "  This protects the key at rest and will be stored as a CI secret."
    echo "  Tip: set OPENSPEC_PLUGIN_PASSPHRASE in your shell profile to skip this prompt."
    echo ""
    read -rs "KEY_PASSWORD?  Passphrase (hidden): "
    echo ""
    read -rs "KEY_PASSWORD_CONFIRM?  Confirm passphrase (hidden): "
    echo ""

    if [[ "$KEY_PASSWORD" != "$KEY_PASSWORD_CONFIRM" ]]; then
      log_error "Passphrases do not match. Aborting."
      exit 1
    fi

    if [[ -z "$KEY_PASSWORD" ]]; then
      log_error "Passphrase cannot be empty. Aborting."
      exit 1
    fi
  fi

  # Generate private key with passphrase
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 \
    -aes256 -pass "pass:${KEY_PASSWORD}" \
    -out "$KEY_FILE" 2>/dev/null

  chmod 600 "$KEY_FILE"

  # Generate CSR
  openssl req -new -key "$KEY_FILE" -passin "pass:${KEY_PASSWORD}" \
    -out "$CSR_FILE" \
    -subj "/CN=johnnyblabs/O=johnnyblabs" 2>/dev/null

  # Self-sign certificate (10-year validity)
  openssl x509 -req -days 3650 \
    -in "$CSR_FILE" \
    -signkey "$KEY_FILE" -passin "pass:${KEY_PASSWORD}" \
    -out "$CERT_FILE" 2>/dev/null

  chmod 600 "$CERT_FILE"

  # Clean up CSR (not needed after signing)
  rm -f "$CSR_FILE"

  log_success "Key pair generated at ${KEY_DIR}/"
  echo "    Private key: ${KEY_FILE}"
  echo "    Certificate: ${CERT_FILE}"
fi

echo ""

# ------------------------------------------------------------------
# 2. Read passphrase if keys already existed
# ------------------------------------------------------------------
if [[ -z "${KEY_PASSWORD:-}" ]]; then
  if [[ -n "${OPENSPEC_PLUGIN_PASSPHRASE:-}" ]]; then
    KEY_PASSWORD="$OPENSPEC_PLUGIN_PASSPHRASE"
  else
    log_info "Enter the passphrase for the existing signing key:"
    read -rs "KEY_PASSWORD?  Passphrase (hidden): "
    echo ""
  fi
fi

# ------------------------------------------------------------------
# 3. Base64-encode for Forgejo secrets
# ------------------------------------------------------------------
log_info "Step 2: Encoding key and certificate..."

KEY_B64=$(base64 -i "$KEY_FILE")
CERT_B64=$(base64 -i "$CERT_FILE")

log_success "Encoded for Forgejo secrets"
echo ""

# ------------------------------------------------------------------
# 4. Set Forgejo repository secrets
# ------------------------------------------------------------------
log_info "Step 3: Setting CI secrets..."

# --- Forgejo ---
if [[ -n "${FORGEJO_TOKEN:-}" && -n "${FORGEJO_URL:-}" ]]; then
  log_info "Setting Forgejo repository secrets..."
  API_AUTH_HEADER="Authorization: token ${FORGEJO_TOKEN}"

  set_forgejo_secret() {
    local name="$1"
    local value="$2"

    api_call PUT "${FORGEJO_URL}/api/v1/repos/${REPO}/actions/secrets/${name}" \
      "{\"data\": $(echo -n "$value" | jq -Rs .)}"
    local rc=$?

    if [[ $rc -eq 0 || $rc -eq 2 ]]; then
      log_success "  Forgejo: ${name}"
    else
      log_error "  Forgejo: Failed to set ${name}"
      return 1
    fi
  }

  set_forgejo_secret "PLUGIN_SIGNING_KEY" "$KEY_B64"
  set_forgejo_secret "PLUGIN_SIGNING_CERTIFICATE" "$CERT_B64"
  set_forgejo_secret "PLUGIN_SIGNING_KEY_PASSWORD" "$KEY_PASSWORD"
else
  log_info "Skipping Forgejo (FORGEJO_TOKEN or FORGEJO_URL not set)"
fi

# --- GitHub ---
GITHUB_REPO="johnnyblabs/intellij-openspec"
if command -v gh &>/dev/null && gh auth status &>/dev/null; then
  log_info "Setting GitHub repository secrets..."

  echo "$KEY_B64" | gh secret set PLUGIN_SIGNING_KEY --repo "$GITHUB_REPO" && \
    log_success "  GitHub: PLUGIN_SIGNING_KEY"
  echo "$CERT_B64" | gh secret set PLUGIN_SIGNING_CERTIFICATE --repo "$GITHUB_REPO" && \
    log_success "  GitHub: PLUGIN_SIGNING_CERTIFICATE"
  gh secret set PLUGIN_SIGNING_KEY_PASSWORD --repo "$GITHUB_REPO" --body "$KEY_PASSWORD" && \
    log_success "  GitHub: PLUGIN_SIGNING_KEY_PASSWORD"
else
  log_info "Skipping GitHub (gh CLI not authenticated)"
fi

echo ""

# ------------------------------------------------------------------
# Done
# ------------------------------------------------------------------
echo ""
echo "========================================"
log_success "Plugin signing setup complete!"
echo "========================================"
echo ""
echo "  Keys:    ${KEY_DIR}/"
echo "  Secrets: PLUGIN_SIGNING_KEY, PLUGIN_SIGNING_CERTIFICATE, PLUGIN_SIGNING_KEY_PASSWORD"
echo "  Targets: Forgejo + GitHub"
echo ""
echo "  The certificate is embedded in the signed ZIP automatically."
echo "  No manual upload to JetBrains Marketplace is needed."
echo ""
echo "  The next CI build on main will sign the plugin ZIP automatically."
echo ""

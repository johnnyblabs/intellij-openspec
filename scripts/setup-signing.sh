#!/usr/bin/env zsh
# setup-signing.sh — Generate plugin signing key and configure Forgejo secrets
#
# Idempotent: skips key generation if keys exist, updates secrets in place
#
# Keys stored at: ~/.ssh/openspec/
# Secrets set on: Forgejo repo via API

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
source "${SCRIPT_DIR}/lib/common.sh"

check_deps
load_env

KEY_DIR="$HOME/.ssh/openspec"
KEY_FILE="${KEY_DIR}/plugin-signing-key.pem"
CERT_FILE="${KEY_DIR}/plugin-signing-cert.pem"
CSR_FILE="${KEY_DIR}/plugin-signing-csr.pem"

REPO="johnb/OpenSpecPlugin"

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

  echo ""
  echo "  Choose a passphrase for the signing key."
  echo "  This protects the key at rest and will be stored as a Forgejo secret."
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
  log_info "Enter the passphrase for the existing signing key:"
  read -rs "KEY_PASSWORD?  Passphrase (hidden): "
  echo ""
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
log_info "Step 3: Setting Forgejo repository secrets..."

API_AUTH_HEADER="Authorization: token ${FORGEJO_TOKEN}"

set_secret() {
  local name="$1"
  local value="$2"

  api_call PUT "${FORGEJO_URL}/api/v1/repos/${REPO}/actions/secrets/${name}" \
    "{\"data\": $(echo -n "$value" | jq -Rs .)}"
  local rc=$?

  if [[ $rc -eq 0 || $rc -eq 2 ]]; then
    log_success "  ${name}"
  else
    log_error "  Failed to set ${name}"
    return 1
  fi
}

set_secret "PLUGIN_SIGNING_KEY" "$KEY_B64"
set_secret "PLUGIN_SIGNING_CERTIFICATE" "$CERT_B64"
set_secret "PLUGIN_SIGNING_KEY_PASSWORD" "$KEY_PASSWORD"

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
echo ""
echo "  The certificate is embedded in the signed ZIP automatically."
echo "  No manual upload to JetBrains Marketplace is needed."
echo ""
echo "  The next CI build on main will sign the plugin ZIP automatically."
echo ""

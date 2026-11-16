#!/usr/bin/env bash
# Prints a freshly-signed RS256 client-assertion JWT (RFC 7523 private_key_jwt)
# for Bank Mellat's auth endpoint - nothing else, no network call. Meant for
# pasting into a manual API client (Insomnia/Postman) as the Bearer token on
# the login request - see repro-invalid-token.sh for the full curl+openssl
# flow this is extracted from, and the "generate-client-assertion" section of
# SKILL.md for how to wire it into Insomnia.
#
# Usage: .claude/skills/run-mellat-provider-app/generate-client-assertion.sh
# Valid for feign.mellat.auth.client-assertion-validity-seconds (300s/5min by
# default) from the moment you run this - re-run right before you send the
# login request, not minutes in advance.

set -euo pipefail
cd "$(dirname "$0")/../../../"

CLIENT_ID="${MELLAT_CLIENT_ID:-mofid-test}"
AUDIENCE="${MELLAT_AUDIENCE:-http://192.168.10.53:30888/realms/baas-realm}"
PRIVATE_KEY="${MELLAT_PRIVATE_KEY_PATH:-src/main/resources/cert/private.pem}"
VALIDITY_SECONDS="${MELLAT_CLIENT_ASSERTION_VALIDITY_SECONDS:-300}"

b64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

now=$(date +%s)
exp=$((now + VALIDITY_SECONDS))
jti=$(openssl rand -hex 16)

header='{"alg":"RS256","typ":"JWT"}'
payload=$(printf '{"sub":"%s","iss":"%s","aud":"%s","iat":%d,"exp":%d,"jti":"%s"}' \
  "$CLIENT_ID" "$CLIENT_ID" "$AUDIENCE" "$now" "$exp" "$jti")

header_b64=$(printf '%s' "$header" | b64url)
payload_b64=$(printf '%s' "$payload" | b64url)
signing_input="${header_b64}.${payload_b64}"
signature_b64=$(printf '%s' "$signing_input" | openssl dgst -sha256 -sign "$PRIVATE_KEY" -binary | b64url)

echo "${signing_input}.${signature_b64}"

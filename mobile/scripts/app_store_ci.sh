#!/usr/bin/env bash
# Run only inside an approved store deployment job, without logging credentials.
set -euo pipefail
: "${ASC_KEY_ID:?Missing ASC_KEY_ID from ios-testflight environment}"
: "${ASC_ISSUER_ID:?Missing ASC_ISSUER_ID from ios-testflight environment}"
: "${ASC_PRIVATE_KEY:?Missing ASC_PRIVATE_KEY from ios-testflight environment}"
: "${RUNNER_TEMP:?Missing RUNNER_TEMP}"
: "${STORE_MODE:?Missing STORE_MODE}"
: "${STORE_VERSION:?Missing STORE_VERSION}"

umask 077
key_dir="$(mktemp -d "$RUNNER_TEMP/asc-auth.XXXXXX")"
trap 'rm -f -- "$key_dir/key.p8"; rmdir -- "$key_dir"' EXIT
printf '%s\n' "$ASC_PRIVATE_KEY" > "$key_dir/key.p8"
unset ASC_PRIVATE_KEY ASC_PRIVATE_KEY_B64 ASC_BEARER_TOKEN ASC_PROFILE ASC_CONFIG_PATH
export ASC_PRIVATE_KEY_PATH="$key_dir/key.p8"
export ASC_KEY_TYPE=team
export ASC_BYPASS_KEYCHAIN=1

# Verify real credentials against this app before executing any store writes.
asc api GET /v1/apps/6835123279 >/dev/null
echo "App Store Connect authentication verified"
python3 mobile/scripts/app_store.py "$STORE_MODE" --version "$STORE_VERSION" \
  --locale "${STORE_LOCALE:-ko}" --group "${STORE_GROUP:-}" --build-number "${STORE_BUILD:-}"

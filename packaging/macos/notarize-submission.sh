#!/bin/bash
# notarize-submission.sh - Notarize an app bundle or a disk image with an App Store Connect
# API key, then staple the ticket to it.
#
# The API key is the only notarization credential the release job needs: the same key
# authenticates xcodebuild's -allowProvisioningUpdates, so CI holds one credential set.
#
# `notarytool submit --wait` exits 0 even when Apple returns status Invalid, so the status
# line is checked explicitly and the rejection log is printed on failure.
#
# Usage: notarize-submission.sh <path-to-.app-or-.dmg> <AuthKey.p8> <key-id> <issuer-id>

set -euo pipefail

TARGET="${1:?usage: notarize-submission.sh <path-to-.app-or-.dmg> <AuthKey.p8> <key-id> <issuer-id>}"
KEY="${2:?usage: notarize-submission.sh <path-to-.app-or-.dmg> <AuthKey.p8> <key-id> <issuer-id>}"
KEY_ID="${3:?usage: notarize-submission.sh <path-to-.app-or-.dmg> <AuthKey.p8> <key-id> <issuer-id>}"
ISSUER="${4:?usage: notarize-submission.sh <path-to-.app-or-.dmg> <AuthKey.p8> <key-id> <issuer-id>}"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

submit_path="$TARGET"
if [ -d "$TARGET" ]; then
    # notarytool accepts a zip; ditto keeps symlinks and the bundle structure intact.
    submit_path="$tmp/$(basename "$TARGET").zip"
    ditto -c -k --keepParent "$TARGET" "$submit_path"
fi

set +e
submission="$(xcrun notarytool submit "$submit_path" \
    --key "$KEY" --key-id "$KEY_ID" --issuer "$ISSUER" --wait 2>&1)"
submit_status=$?
set -e
echo "$submission"

if [ "$submit_status" -ne 0 ]; then
    echo "notarytool submit failed for $(basename "$TARGET")." >&2
    exit 1
fi

# The outcome is the trailing "  status: <value>" line; "Current status: ..." progress
# lines do not match this pattern.
if ! grep -qE '^ *status: Accepted' <<< "$submission"; then
    id="$(awk '/^ *id: /{print $2; exit}' <<< "$submission")"
    if [ -n "$id" ]; then
        echo "--- notarization log for $id ---"
        xcrun notarytool log "$id" --key "$KEY" --key-id "$KEY_ID" --issuer "$ISSUER" 2>&1 || true
    fi
    echo "Notarization of $(basename "$TARGET") was not accepted." >&2
    exit 1
fi

xcrun stapler staple "$TARGET"
xcrun stapler validate "$TARGET"

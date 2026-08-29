#!/bin/bash
# notarize-app.sh - Notarize an app bundle and staple the ticket to it.
#
# notarizeDmg attaches a ticket to the disk image only.  Once a user drags the app out
# of the DMG, Gatekeeper falls back to an online check on first launch.  Notarizing and
# stapling the bundle before it is packaged closes that gap, so the first launch also
# works offline.  jpackage copies the app image into the DMG without re-signing it, so
# the ticket stapled here survives into the disk image.
#
# `notarytool submit --wait` exits 0 even when Apple returns status Invalid, which turns
# a rejection into a confusing "Record not found" failure at the stapling step.  The
# status is therefore checked explicitly and the rejection log printed on failure.
#
# The app-specific password is read from stdin so it never appears in the argument list.
#
# Usage: notarize-app.sh <path-to-.app> <apple-id> <team-id>  # password on stdin

set -euo pipefail

APP="${1:?usage: notarize-app.sh <path-to-.app> <apple-id> <team-id>}"
APPLE_ID="${2:?usage: notarize-app.sh <path-to-.app> <apple-id> <team-id>}"
TEAM_ID="${3:?usage: notarize-app.sh <path-to-.app> <apple-id> <team-id>}"

if ! IFS= read -r password; then
    echo "No app-specific password on stdin." >&2
    exit 1
fi

# A ticket only validates against the signature it was issued for, so a surviving one
# means this exact build is already notarized and the round trip can be skipped.
if xcrun stapler validate "$APP" >/dev/null 2>&1; then
    echo "$(basename "$APP") already carries a valid notarization ticket."
    exit 0
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# ditto preserves the bundle structure and symlinks; plain zip does not.
archive="$tmp/$(basename "$APP").zip"
ditto -c -k --keepParent "$APP" "$archive"

# Captured rather than streamed so the status can be inspected; without disarming
# errexit around it a failing notarytool would abort before its output is ever shown.
set +e
submission="$(printf '%s\n' "$password" | xcrun notarytool submit "$archive" \
    --apple-id "$APPLE_ID" --team-id "$TEAM_ID" --wait 2>&1)"
submit_status=$?
set -e
echo "$submission"

if [ "$submit_status" -ne 0 ]; then
    echo "notarytool submit failed for $(basename "$APP")." >&2
    exit 1
fi

# notarytool reports the outcome in a trailing "  status: <value>" line.  "Current
# status: ..." progress lines do not match this pattern.
if ! grep -qE '^ *status: Accepted' <<< "$submission"; then
    id="$(awk '/^ *id: /{print $2; exit}' <<< "$submission")"
    if [ -n "$id" ]; then
        echo "--- notarization log for $id ---"
        printf '%s\n' "$password" | xcrun notarytool log "$id" \
            --apple-id "$APPLE_ID" --team-id "$TEAM_ID" 2>&1 || true
    fi
    echo "Notarization of $(basename "$APP") was not accepted." >&2
    exit 1
fi

xcrun stapler staple "$APP"

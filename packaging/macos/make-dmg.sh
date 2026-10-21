#!/bin/bash
# make-dmg.sh - Package a signed, notarized and stapled .app into a compressed disk image.
#
# The app is copied with ditto so its stapled ticket and code signature survive. The disk
# image is then signed with the Developer ID Application identity; notarize-submission.sh
# notarizes and staples the image afterwards.
#
# Usage: make-dmg.sh <path-to-.app> <output.dmg> <volume-name> <signing-identity>

set -euo pipefail

APP="${1:?usage: make-dmg.sh <path-to-.app> <output.dmg> <volume-name> <signing-identity>}"
OUT="${2:?usage: make-dmg.sh <path-to-.app> <output.dmg> <volume-name> <signing-identity>}"
VOLNAME="${3:?usage: make-dmg.sh <path-to-.app> <output.dmg> <volume-name> <signing-identity>}"
IDENTITY="${4:?usage: make-dmg.sh <path-to-.app> <output.dmg> <volume-name> <signing-identity>}"

stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

ditto "$APP" "$stage/$(basename "$APP")"
ln -s /Applications "$stage/Applications"

mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
hdiutil create -volname "$VOLNAME" -srcfolder "$stage" -ov -format UDZO "$OUT"

codesign --force --timestamp --sign "$IDENTITY" "$OUT"
codesign --verify --verbose=2 "$OUT"

echo "Built and signed $(basename "$OUT")"

#!/bin/bash
# sign-app.sh - Sign an unsigned macOS .app with Developer ID: nested code first, then the app with
# its entitlements, hardened runtime and a secure timestamp, with the provisioning profile embedded.
#
# This is what lets the release job keep the signing key away from the build. The archive is made
# with CODE_SIGNING_ALLOWED=NO on a runner that never sees the key; Gradle and every Kotlin plugin
# and dependency run there. Signing happens on a fresh runner that runs only Apple's tools and this
# script. It also works locally against the login keychain:
#
#   packaging/macos/sign-app.sh DHBW-Horb.app "Developer ID Application: … (56T6W6Z755)" \
#     ~/Downloads/DHBW_Horb_Developer_ID.provisionprofile iosApp/iosApp/iosApp-macOS.entitlements
#
# The entitlements are the source file from the project with $(AppIdentifierPrefix) expanded, plus
# the application and team identifier that Xcode adds when it signs with a profile. A restricted
# entitlement (keychain-access-groups) that the profile does not grant keeps the app from launching,
# so the profile is checked before anything is signed.
#
# Usage: sign-app.sh <path-to-.app> <signing-identity> <profile.provisionprofile> <entitlements> [keychain]

set -euo pipefail

usage="usage: sign-app.sh <path-to-.app> <signing-identity> <profile.provisionprofile> <entitlements> [keychain]"
APP="${1:?$usage}"
IDENTITY="${2:?$usage}"
PROFILE="${3:?$usage}"
ENTITLEMENTS_SRC="${4:?$usage}"
KEYCHAIN="${5:-}"

fail() { echo "sign-app.sh: $*" >&2; exit 1; }

[ -d "$APP/Contents/MacOS" ] || fail "$APP is not an app bundle."
[ -f "$PROFILE" ] || fail "profile $PROFILE not found."
[ -f "$ENTITLEMENTS_SRC" ] || fail "entitlements $ENTITLEMENTS_SRC not found."

# The team is part of the identity's name: "Developer ID Application: Name (TEAMID)".
TEAM_ID="$(sed -n 's/^Developer ID Application: .*(\([A-Z0-9]\{10\}\))$/\1/p' <<< "$IDENTITY")"
[ -n "$TEAM_ID" ] || fail "'$IDENTITY' is not a Developer ID Application identity."

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

BUNDLE_ID="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP/Contents/Info.plist")"
APP_ID="$TEAM_ID.$BUNDLE_ID"

# --- Profile ------------------------------------------------------------------------------------
security cms -D -i "$PROFILE" > "$tmp/profile.plist"
pb() { /usr/libexec/PlistBuddy -c "Print :$1" "$tmp/profile.plist" 2>/dev/null || true; }
[ "$(pb Entitlements:com.apple.application-identifier)" = "$APP_ID" ] \
    || fail "profile is for '$(pb Entitlements:com.apple.application-identifier)', the app is '$APP_ID'."
[ "$(pb ProvisionsAllDevices)" = "true" ] || fail "profile '$(pb Name)' is not a Developer ID profile."
[ -n "$(pb Entitlements:keychain-access-groups)" ] || fail "profile '$(pb Name)' does not grant keychain-access-groups."
echo "Profile: $(pb Name) ($(pb UUID)), expires $(pb ExpirationDate)"

# macOS refuses to launch an app whose signing certificate is not listed in its embedded profile,
# even though the signature itself verifies. Catch that here instead of after notarization.
# find-identity lists only certificates with their private key, i.e. what codesign can use; a
# certificate of the same name without a key (an older one, or the CI one) is not picked up.
find_args=(-v -p codesigning)
if [ -n "$KEYCHAIN" ]; then find_args+=("$KEYCHAIN"); fi
SIGNING_SHA1="$(security find-identity "${find_args[@]}" \
    | grep -F "\"$IDENTITY\"" | sed -n 's/^ *[0-9]*) \([0-9A-F]\{40\}\) .*/\1/p' | sort -u)"
[ -n "$SIGNING_SHA1" ] || fail "no signing identity '$IDENTITY' found."
[ "$(wc -l <<< "$SIGNING_SHA1")" -eq 1 ] \
    || fail "several identities are named '$IDENTITY'; codesign could not choose either."
listed=0
i=0
while cert_b64="$(plutil -extract "DeveloperCertificates.$i" raw -o - "$tmp/profile.plist" 2>/dev/null)"; do
    cert_sha1="$(base64 -D <<< "$cert_b64" | shasum -a 1 | cut -d' ' -f1 | tr 'a-f' 'A-F')"
    if [ "$cert_sha1" = "$SIGNING_SHA1" ]; then listed=1; fi
    i=$((i + 1))
done
[ "$listed" -eq 1 ] || fail "the signing certificate ($SIGNING_SHA1) is not in profile '$(pb Name)'; \
regenerate the profile with this certificate selected."

# --- Entitlements -------------------------------------------------------------------------------
ENTITLEMENTS="$tmp/entitlements.plist"
sed "s/\$(AppIdentifierPrefix)/$TEAM_ID./g" "$ENTITLEMENTS_SRC" > "$ENTITLEMENTS"
if grep -q '\$(' "$ENTITLEMENTS"; then
    fail "unexpanded build setting left in $ENTITLEMENTS_SRC: $(grep -o '\$([A-Za-z_]*)' "$ENTITLEMENTS" | sort -u | tr '\n' ' ')"
fi
/usr/libexec/PlistBuddy -c "Add :com.apple.application-identifier string $APP_ID" "$ENTITLEMENTS"
/usr/libexec/PlistBuddy -c "Add :com.apple.developer.team-identifier string $TEAM_ID" "$ENTITLEMENTS"
plutil -lint "$ENTITLEMENTS" > /dev/null

# --- Nested code --------------------------------------------------------------------------------
# Only frameworks and dylibs are expected (Shared.framework). App extensions, XPC services or login
# items would each need entitlements and a profile of their own, so they stop the script instead of
# being signed wrongly.
for dir in PlugIns XPCServices Library/LoginItems Helpers; do
    if [ -d "$APP/Contents/$dir" ] && [ -n "$(ls -A "$APP/Contents/$dir")" ]; then
        fail "$APP/Contents/$dir is not empty; nested bundles need their own signing setup."
    fi
done

sign() {
    local args=(--force --timestamp --options runtime --sign "$IDENTITY")
    if [ -n "$KEYCHAIN" ]; then args+=(--keychain "$KEYCHAIN"); fi
    codesign "${args[@]}" "$@"
}

if [ -d "$APP/Contents/Frameworks" ]; then
    while IFS= read -r -d '' item; do
        echo "Signing $(basename "$item")"
        sign "$item"
    done < <(find "$APP/Contents/Frameworks" -maxdepth 1 \( -name '*.framework' -o -name '*.dylib' \) -print0)
fi

# --- The app ------------------------------------------------------------------------------------
cp "$PROFILE" "$APP/Contents/embedded.provisionprofile"
echo "Signing $(basename "$APP")"
sign --entitlements "$ENTITLEMENTS" "$APP"

# --- Verify -------------------------------------------------------------------------------------
codesign --verify --deep --strict --verbose=2 "$APP"
details="$(codesign -dvv "$APP" 2>&1)"
grep -q 'flags=0x10000(runtime)' <<< "$details" || fail "hardened runtime is not set."
grep -q '^Timestamp=' <<< "$details" || fail "the signature has no secure timestamp."
grep -q "^TeamIdentifier=$TEAM_ID\$" <<< "$details" || fail "the signature is not from team $TEAM_ID."
signed_entitlements="$(codesign -d --entitlements - --xml "$APP" 2>/dev/null)"
for key in com.apple.security.app-sandbox keychain-access-groups com.apple.application-identifier; do
    grep -q "$key" <<< "$signed_entitlements" || fail "the signed app lacks the $key entitlement."
done
echo "Signed and verified: $(basename "$APP")"

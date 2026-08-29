#!/bin/bash
# sign-jar-natives.sh - Sign macOS native libraries the Compose plugin misses inside JARs.
#
# The Compose Gradle plugin does sign native libraries bundled inside JARs, but its
# MacJarSignFileCopyingProcessor recognises them only by the extensions .dylib and
# .jnilib.  jkeychain ships its Mach-O as osxkeychain.so, so it stays unsigned and
# Apple's notary service rejects the entire submission with "The binary is not signed."
#
# This signs every still-unsigned Mach-O carrying a .so extension, writes it back into
# its JAR, and re-signs the app bundle, whose seal the JAR rewrite invalidates.
#
# Usage: sign-jar-natives.sh <path-to-.app> <signing-identity>

set -euo pipefail

APP="${1:?usage: sign-jar-natives.sh <path-to-.app> <signing-identity>}"
IDENTITY="${2:?usage: sign-jar-natives.sh <path-to-.app> <signing-identity>}"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

signed_any=0

while IFS= read -r jar; do
    entries="$(unzip -Z1 "$jar" 2>/dev/null | grep -E '\.so$' || true)"
    [ -n "$entries" ] || continue

    while IFS= read -r entry; do
        [ -n "$entry" ] || continue

        rm -rf "$tmp/extract"
        mkdir -p "$tmp/extract"
        unzip -qq -o "$jar" "$entry" -d "$tmp/extract"
        lib="$tmp/extract/$entry"

        # The same JARs carry Linux .so files; only Mach-O concerns the notary service.
        file -b "$lib" | grep -q 'Mach-O' || continue
        # Whatever the plugin already handled keeps its signature.
        if codesign --verify "$lib" >/dev/null 2>&1; then
            continue
        fi

        codesign --force --timestamp --options runtime --sign "$IDENTITY" "$lib"
        ( cd "$tmp/extract" && zip -q "$jar" "$entry" )
        echo "Signed $(basename "$jar")/$entry"
        signed_any=1
    done <<< "$entries"
done < <(find "$APP/Contents/app" -name '*.jar')

if [ "$signed_any" -eq 0 ]; then
    echo "No unsigned Mach-O libraries inside JARs; app bundle left untouched."
    exit 0
fi

# Rewriting a JAR invalidates the bundle seal in _CodeSignature/CodeResources, so the
# bundle has to be signed again.  Reuse the entitlements already on the bundle instead
# of assuming which ones the plugin applied.
entitlements="$tmp/entitlements.plist"
codesign -d --entitlements - --xml "$APP" > "$entitlements" 2>/dev/null

codesign --force --timestamp --options runtime \
    --entitlements "$entitlements" --sign "$IDENTITY" "$APP"
codesign --verify --deep --strict "$APP"

echo "Re-signed $(basename "$APP")"

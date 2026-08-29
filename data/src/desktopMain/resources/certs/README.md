# Bundled trust anchors (desktop only)

Dualis (`dualis.dhbw.de`) is served by HARICA, whose 2021 TLS roots are **not** in the JDK's
`cacerts` — Zulu 25 ships only `haricarootca2015` and `haricaeccrootca2015`. Browsers and macOS
trust the 2021 roots, so the site looks fine everywhere except in the app, where the JDK cannot
build a certification path and every request fails with

    PKIX path building failed: unable to find valid certification path to requested target

The desktop build therefore adds these two roots **on top of** the JDK's default trust store
(`DesktopTrustStore.kt`). Nothing is disabled or weakened; the default anchors keep working and no
hostname or expiry check is skipped.

| File | Subject | SHA-256 |
|---|---|---|
| `harica-tls-rsa-root-ca-2021.pem` | `CN=HARICA TLS RSA Root CA 2021, O=Hellenic Academic and Research Institutions CA, C=GR` | `D9:5D:0E:8E:DA:79:52:5B:F9:BE:B1:1B:14:D2:10:0D:32:94:98:5F:0C:62:D9:FA:BD:9C:D9:99:EC:CB:7B:1D` |
| `harica-tls-ecc-root-ca-2021.pem` | `CN=HARICA TLS ECC Root CA 2021, O=Hellenic Academic and Research Institutions CA, C=GR` | `3F:99:CC:47:4A:CF:CE:4D:FE:D5:87:94:66:5E:47:8D:15:47:73:9F:2E:78:0F:1B:B4:CA:9B:13:30:97:D4:01` |

Both are publicly trusted roots (Mozilla, Apple, Microsoft) valid until 2045. The RSA one is the
anchor Dualis actually chains to today; the ECC one is here because HARICA issues from both and a
certificate renewal could switch chains, which would otherwise break the desktop app again with no
code change on our side. The **client** authentication roots of the same generation are deliberately
not bundled — nothing here authenticates with a certificate.

`DesktopTrustStoreTest` checks the fingerprints, so replacing a file with a different certificate
fails the build.

## Refreshing

Take them from a trust store that already has them, then check the fingerprint against the table
above and against HARICA's published values at <https://repo.harica.gr>:

```bash
security find-certificate -a -c "HARICA TLS" -p \
  /System/Library/Keychains/SystemRootCertificates.keychain
openssl x509 -in harica-tls-rsa-root-ca-2021.pem -noout -subject -fingerprint -sha256
```

They become removable once the JDK baseline ships the 2021 roots in `cacerts`; check with
`keytool -list -cacerts -storepass changeit | grep -i harica`.

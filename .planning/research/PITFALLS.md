# Domain Pitfalls: KMP SSL Trust

**Domain:** Multiplatform SSL/TLS
**Researched:** October 2024

## Critical Pitfalls

Mistakes that cause connectivity failures or major security vulnerabilities.

### Pitfall 1: SSL Compatibility Constraints (iOS 13+)
**What goes wrong:** iOS enforces strict SSL requirements (e.g., certificates valid for > 825 days, missing SAN extension).
**Why it happens:** Apple's security policies for `NSURLSession` are more rigid than traditional JVM.
**Consequences:** Even if a custom CA is trusted, the connection fails with a "Requirements not met" error.
**Prevention:** Ensure custom/legacy CAs meet modern security standards or use `SecTrustSetExceptions` (not recommended).

### Pitfall 2: PEM vs. DER Format Confusion
**What goes wrong:** Passing a PEM string (with headers) to an API expecting binary DER data.
**Why it happens:** iOS's `SecCertificateCreateWithData` expects **DER** (binary). Android's `CertificateFactory` handles both.
**Consequences:** iOS will fail to load the certificate and fail silently or throw a null pointer.
**Prevention:** Bundle certificates as binary `.der` files or strip PEM headers and base64-decode them manually before passing to iOS APIs.

### Pitfall 3: Intermediate Chain Missing
**What goes wrong:** Bundling only the Root CA when the server doesn't send the full intermediate chain.
**Why it happens:** Servers often only send the leaf cert.
**Consequences:** The client cannot bridge the gap to the root, and the handshake fails.
**Prevention:** Always bundle the full intermediate chain if the server is misconfigured. Use `openssl s_client -showcerts` to verify.

## Moderate Pitfalls

### Pitfall 1: Clock Desynchronization
**What goes wrong:** SSL handshake fails because the device time is incorrect.
**Prevention:** Provide clear user messaging when an SSL error occurs, suggesting they check their device time/date.

### Pitfall 2: Pinning Expiry (Bricking)
**What goes wrong:** Hardcoding a certificate hash that expires, preventing users from ever connecting again.
**Prevention:** Always include a **backup pin** from a different CA or the next planned certificate. Never pin without an update strategy.

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| iOS Development | App Transport Security (ATS) blocks. | Configure `NSAppTransportSecurity` exceptions in `Info.plist` if needed for non-HTTPS dev servers. |
| Android Release | R8/ProGuard stripping SSL classes. | Add keep rules for OkHttp and Ktor SSL classes. |
| JVM/Desktop | System-wide trust store changes. | Never use `System.setProperty` for trust anchors; always use `engine { https { ... } }`. |

## Sources

- [Apple Support: New requirements for trusted certificates](https://support.apple.com/en-us/HT210176)
- [OkHttp: Certificate Pinning Pitfalls](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)

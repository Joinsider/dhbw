# Research Summary: KMP Custom SSL Trust & Certificate Pinning

**Domain:** Kotlin Multiplatform Networking
**Researched:** October 2024
**Overall confidence:** HIGH

## Executive Summary

Handling custom trust anchors (CAs) and certificate pinning in Kotlin Multiplatform (KMP) requires platform-specific implementations because Ktor (the standard networking library) delegates SSL/TLS handling to native engines (OkHttp on Android, Darwin on iOS, and CIO/JSSE on Desktop). 

For **legacy or non-standard CAs** (like the HARICA root used by DHBW or self-signed certs), the best practice is to bundle the certificate in the app and explicitly add it to the trust store of the platform-specific engine. 

For **Certificate Pinning**, while some engines have built-in support (OkHttp), others (Darwin, CIO) require manual validation within authentication challenge handlers or custom trust managers.

## Key Findings

**Stack:** Ktor 2.x/3.x with engine-specific configurations (OkHttp, Darwin, CIO).
**Architecture:** Use the `expect`/`actual` pattern to provide a configured `HttpClient` from platform-specific modules.
**Critical pitfall:** Newer Root CAs (e.g., HARICA 2021) are missing from older Android versions (< 14) and potentially older iOS versions, requiring manual bundling to prevent connection failures.

## Implications for Roadmap

Based on research, the following structure is recommended for implementing secure, custom trust in a KMP project:

1.  **Infrastructure: Resource Management**
    - Set up a way to access raw certificate bytes (`.der` or `.pem`) in `commonMain` (e.g., using `composeResources` or a custom resource provider).

2.  **Platform Implementation: Android**
    - Use `network_security_config.xml` for static CAs (best practice for Android).
    - Use `OkHttp` engine config for dynamic pinning.

3.  **Platform Implementation: iOS**
    - Implement `handleChallenge` in the `Darwin` engine to manually validate the server trust against bundled anchors.

4.  **Platform Implementation: Desktop/JVM**
    - Configure the `CIO` engine with a custom `X509TrustManager` that uses a `KeyStore` initialized with the bundled certificate.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Ktor Engines | HIGH | Ktor documentation and community patterns are well-established. |
| Android Trust | HIGH | Network Security Config is the official, stable way since API 24. |
| iOS Trust | HIGH | `NSURLSessionDelegate` (via Darwin engine) is the standard iOS approach. |
| Desktop/CIO | HIGH | CIO engine provides direct access to JSSE `TrustManager`. |

## Gaps to Address

- **Wasm/JS Support:** SSL trust in browsers is managed by the browser itself; KMP has very limited control here beyond what the Fetch API allows.
- **Certificate Rotation:** A strategy for updating bundled certificates (e.g., via a remote config or app update) must be planned to avoid bricking the app when a CA expires.

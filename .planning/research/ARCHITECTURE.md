# Architecture Patterns: KMP Secure Networking

**Domain:** Multiplatform SSL & Networking
**Researched:** October 2024

## Recommended Architecture

The application should use a **Platform-Injected HttpClient Factory**. This allows common code to use a single `HttpClient` while each platform injects its own security configuration (CAs, pinning) into the engine.

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `HttpClientFactory` | `expect` / `actual` provider of configured client. | `Ktor Engines` |
| `ResourceProvider` | Common access to certificate binary data (`.der`/`.pem`). | `Platform FS` |
| `SslConfigurator` | (Platform-specific) Logic to add trust anchors or pins. | `OkHttp`/`Darwin`/`CIO` |

### Data Flow

1.  **Common Code**: Requests a `HttpClient` from the factory.
2.  **Factory (Platform-Specific)**:
    - Reads certificate from resources.
    - Configures the engine (OkHttp/Darwin/CIO) with the certificate.
    - Sets up pinning if required.
3.  **HttpClient**: Performs requests using the securely configured engine.

## Patterns to Follow

### Pattern 1: `expect`/`actual` Engine Configuration
**What:** Define a common function to create the client, with platform-specific engine setup.
**When:** Whenever SSL trust or pinning is required.

**Example (Common):**
```kotlin
expect fun createHttpClient(caData: ByteArray?): HttpClient
```

**Example (iOS - Darwin):**
```kotlin
actual fun createHttpClient(caData: ByteArray?): HttpClient = HttpClient(Darwin) {
    engine {
        handleChallenge { _, _, challenge, completionHandler ->
            val serverTrust = challenge.protectionSpace.serverTrust
            if (caData != null && serverTrust != null) {
                val caCert = SecCertificateCreateWithData(null, caData.toNSData() as CFDataRef)
                SecTrustSetAnchorCertificates(serverTrust, listOf(caCert).toCFArray())
                SecTrustSetAnchorCertificatesOnly(serverTrust, true)
                // Evaluate and proceed...
            }
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
        }
    }
}
```

## Anti-Patterns to Avoid

### Anti-Pattern 1: Hardcoding Certificates in Code
**What:** Storing certificate strings directly in Kotlin files.
**Why bad:** Makes updates difficult; hard to manage binary formats across platforms.
**Instead:** Store in `commonMain/resources` and read as bytes.

### Anti-Pattern 2: Global Trust Management (JVM)
**What:** Using `System.setProperty("javax.net.ssl.trustStore", ...)` on Desktop.
**Why bad:** Affects the entire JVM process, not just the app's networking.
**Instead:** Pass a specific `TrustManager` to the Ktor engine.

## Sources

- [Ktor: Expect/Actual Patterns](https://ktor.io/docs/client-engines.html#expect-actual)
- [Apple: SSL/TLS Trust Management](https://developer.apple.com/documentation/security/certificate_key_and_trust_services/trust/verifying_a_trust_object)

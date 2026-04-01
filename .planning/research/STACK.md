# Technology Stack for KMP Custom SSL Trust

**Project:** KMP Networking Reference
**Researched:** October 2024

## Recommended Stack

### Core Framework
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Ktor | 2.x/3.x | HTTP Client | Standard, type-safe networking for KMP. |
| OkHttp | 4.12.0+ | Android Engine | Highly flexible SSL configuration (pinning, trust managers). |
| Darwin | (Built-in) | iOS Engine | Leverages native `NSURLSession` for system-level trust integration. |
| CIO | (Built-in) | JVM/Desktop Engine | Coroutine-based, allows direct JSSE `TrustManager` configuration. |

### Supporting Libraries
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Ktor-Certificate-Pinning | 1.0.0+ | Pinning DSL | When a unified DSL for Android/iOS pinning is preferred over manual code. |
| Compose Resources | 1.6.0+ | Resource Handling | For bundling `.der` or `.pem` certificates in `commonMain` resources. |

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Android Engine | OkHttp | CIO | OkHttp has much better support for pinning and advanced SSL debugging on Android. |
| iOS Engine | Darwin | CIO (Native) | CIO on Native uses the platform's root store but has less flexibility than Darwin's `handleChallenge`. |
| Trust Method | Bundle certs | Trust all certs | Trusting all is a major security vulnerability (MITM). |

## Installation

### Core Dependencies
```kotlin
// commonMain
implementation("io.ktor:ktor-client-core:2.3.12")

// androidMain
implementation("io.ktor:ktor-client-okhttp:2.3.12")

// iosMain
implementation("io.ktor:ktor-client-darwin:2.3.12")

// desktopMain (JVM)
implementation("io.ktor:ktor-client-cio:2.3.12")
```

## Implementation Summary

### iOS (Darwin) Certificate Configuration
```kotlin
HttpClient(Darwin) {
    engine {
        handleChallenge { session, task, challenge, completionHandler ->
            val serverTrust = challenge.protectionSpace.serverTrust
            if (serverTrust != null) {
                // Use SecTrustSetAnchorCertificates to add custom root
                // Use SecTrustEvaluate to check validity
                completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.create(serverTrust))
            } else {
                completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            }
        }
    }
}
```

### Desktop (CIO) Certificate Configuration
```kotlin
HttpClient(CIO) {
    engine {
        https {
            // Assign a custom X509TrustManager initialized with bundled cert in a KeyStore
            trustManager = myCustomX509TrustManager
        }
    }
}
```

## Sources

- [Ktor: Engines](https://ktor.io/docs/client-engines.html)
- [Ktor Certificate Pinning Library](https://github.com/alistairsykes/Ktor-Certificate-Pinning)
- [Android Network Security Configuration](https://developer.android.com/training/articles/security-config)

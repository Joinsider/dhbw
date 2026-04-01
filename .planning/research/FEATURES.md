# Feature Landscape: Secure KMP Networking

**Domain:** SSL Trust & Security
**Researched:** October 2024

## Table Stakes

Features users expect. Missing = product feels incomplete or broken.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Domain-Specific Trust | Connect to internal or legacy CAs without system-wide trust. | Medium | Requires platform-specific engine configuration. |
| SSL Validation | Ensure connections are secure and not MITM'd. | Low | Handled by engines, must be configured correctly for custom CAs. |
| Legacy Device Support | Apps must work on older OS versions where Root CAs are missing. | Low | Bundling roots is the primary solution. |

## Differentiators

Features that set product apart. Not expected, but valued.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Certificate Pinning | Prevents attacks even if a public CA is compromised. | Medium/High | Requires manual hash validation in some engines. |
| Dynamic Trust Anchors | Ability to update trusted CAs without an app update. | High | Requires a secure remote config system. |
| Multi-Domain Trust | Different trust anchors for different backends. | Medium | Best achieved with OkHttp/Darwin configurations. |

## Anti-Features

Features to explicitly NOT build.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Blind Trust (Disable SSL) | Major security vulnerability, credentials exposed. | Proper CA bundling and domain-scoped configuration. |
| Global System Trust Mod | Potentially impacts other apps or the entire system. | Scope custom CAs to the specific app or backend domains. |

## Feature Dependencies

```
Certificate Bundling (Resource) → Custom Engine Config → Secure Connection to Non-standard CA
```

## MVP Recommendation

Prioritize:
1.  **Bundled Trust Anchors (CAs):** Essential for connecting to internal/legacy systems.
2.  **Platform-Specific Engine Config:** Using `expect`/`actual` to implement trust on Android, iOS, and Desktop.

## Sources

- [Ktor: SSL Configuration](https://ktor.io/docs/client-engines.html#ssl-config)
- [Android Security Tips: Custom CAs](https://developer.android.com/training/articles/security-tips#CustomCa)

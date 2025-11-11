# Add project specific ProGuard rules here.

# Keep rules for Google Error Prone annotations (used by Tink/Security Crypto)
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Keep DualisApplication and all its members (accessed via reflection in SecureStorage)
-keep class de.joinside.dhbw.DualisApplication {
    *;
}
-keep class de.joinside.dhbw.DualisApplication$Companion {
    *;
}


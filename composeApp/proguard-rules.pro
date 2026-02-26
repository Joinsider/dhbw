# Add project specific ProGuard rules here.

# Don't obfuscate - keep class names readable
-dontobfuscate

# Keep rules for Google Error Prone annotations (used by Tink/Security Crypto)
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Keep DualisApplication and all its members (accessed via reflection in SecureStorage)
-keep class de.fampopprol.dhbwhorb.DualisApplication {
    *;
}
-keep class de.fampopprol.dhbwhorb.DualisApplication$Companion {
    *;
}

# Keep Room Database Implementations (Generated code)
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep class **_Impl { *; }
-keepclassmembers class **_Impl { *; }

# Keep all Room generated classes - more specific patterns
-keep class de.fampopprol.dhbwhorb.data.storage.database.AppDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep Room-generated DAOs
-keep interface de.fampopprol.dhbwhorb.data.storage.database.** { *; }
-keep class de.fampopprol.dhbwhorb.data.storage.database.**_Impl { *; }

# Keep your specific application data classes
-keep class de.fampopprol.dhbwhorb.data.** { *; }
-keepclassmembers class de.fampopprol.dhbwhorb.data.** { *; }

# Keep Room runtime
-keep class androidx.room.** { *; }
-keepclassmembers class androidx.room.** { *; }

# General Room/SQLite keeps
-keep class org.sqlite.** { *; }
-keep interface org.sqlite.** { *; }

# Prevent obfuscation of Room classes
-keepnames class * extends androidx.room.RoomDatabase
-keepnames class androidx.room.RoomDatabase
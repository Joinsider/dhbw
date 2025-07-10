# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# JSoup rules
-keep public class org.jsoup.** {
    public *;
}

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep Java time classes (used for date parsing)
-keep class java.time.** { *; }
-dontwarn java.time.**
-keep class sun.util.calendar.** { *; }

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep your model classes
-keep class de.fampopprol.dhbwhorb.data.dualis.models.** { *; }
-keep class de.fampopprol.dhbwhorb.data.cache.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Samsung-specific rules for credential storage
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# Google Tink optional dependencies - suppress warnings for missing classes
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn org.joda.time.Instant

# Keep Samsung Knox classes if present
-keep class com.samsung.android.knox.** { *; }
-dontwarn com.samsung.android.knox.**

# Keep DataStore classes
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.core.** { *; }

# Keep credential manager classes
-keep class de.fampopprol.dhbwhorb.data.security.** { *; }

# Samsung biometric authentication
-keep class androidx.biometric.** { *; }
-keep class android.hardware.biometrics.** { *; }

# General crypto and security classes
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-dontwarn java.security.**
-dontwarn javax.crypto.**

# Samsung device management
-keep class android.app.admin.** { *; }
-keep class android.os.UserManager { *; }

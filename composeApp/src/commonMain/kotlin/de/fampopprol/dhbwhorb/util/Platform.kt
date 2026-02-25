package de.fampopprol.dhbwhorb.util

enum class PlatformType {
    ANDROID,
    IOS,
    DESKTOP,
    MACOS
}

expect fun getPlatform(): PlatformType

fun isMobilePlatform(): Boolean {
    return when (getPlatform()) {
        PlatformType.ANDROID, PlatformType.IOS -> true
        PlatformType.DESKTOP, PlatformType.MACOS -> false
    }
}


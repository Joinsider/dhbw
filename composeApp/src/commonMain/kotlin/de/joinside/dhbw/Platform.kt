package de.joinside.dhbw

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
package de.fampopprol.dhbwhorb

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
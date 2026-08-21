package de.fampopprol.dhbwhorb.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Builds the shared [HttpClient] for the current platform.
 *
 * The factory returns the finished client rather than only an engine, because some platforms need
 * to configure the **engine** and not just the client — desktop has to hand OkHttp a trust manager
 * (see `DesktopTrustStore.kt`), and engine configuration is typed to the engine, which a
 * star-projected [HttpClientConfig] cannot express.
 *
 * Everything that is the same everywhere — cookies, timeouts — is passed in from `dataModule` as
 * [configure], so it stays defined once.
 */
expect object HttpClientFactory {
    fun create(configure: HttpClientConfig<*>.() -> Unit): HttpClient
}

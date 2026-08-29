package de.fampopprol.dhbwhorb.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Desktop implementation of the HttpClientFactory.
 *
 * The JDK's `cacerts` does not contain the HARICA 2021 roots that Dualis chains to, so every
 * request fails with "PKIX path building failed" unless OkHttp is given a trust manager that adds
 * them to the JDK defaults. See `DesktopTrustStore.kt` and `resources/certs/README.md`.
 */
actual object HttpClientFactory {
    actual fun create(configure: HttpClientConfig<*>.() -> Unit): HttpClient {
        val trustManager = dualisTrustManager()
        return HttpClient(OkHttp) {
            configure()
            engine {
                config {
                    sslSocketFactory(dualisSslContext(trustManager).socketFactory, trustManager)
                }
            }
        }
    }
}

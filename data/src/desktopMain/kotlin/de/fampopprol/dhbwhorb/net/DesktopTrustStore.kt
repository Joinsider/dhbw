package de.fampopprol.dhbwhorb.net

import io.github.aakira.napier.Napier
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val TAG = "DesktopTrustStore"

/**
 * Root certificates bundled with the desktop build, loaded from `resources/certs/`.
 *
 * See the README next to those files for why they are needed: the JDK's `cacerts` has HARICA's
 * 2015 roots but not the 2021 ones Dualis chains to, so the desktop app — and only the desktop
 * app — cannot build a certification path to it. Android and Apple use their platform trust
 * stores, which have them.
 */
private val BUNDLED_ROOT_CERTIFICATES = listOf(
    "/certs/harica-tls-rsa-root-ca-2021.pem",
    "/certs/harica-tls-ecc-root-ca-2021.pem",
)

/**
 * The JDK's default trust anchors **plus** [BUNDLED_ROOT_CERTIFICATES].
 *
 * Public only so `DesktopTrustStoreTest` in `:composeApp` can reach it — the test suite still lives
 * there (see the P1 note). Nothing outside this file and `HttpClientFactory.desktop.kt` should
 * call it.
 *
 * This only ever adds anchors. Certificate chains, hostnames and validity dates are still checked
 * by the JDK's own implementation — a trust manager that skipped any of that would turn a
 * connection error into a silent downgrade, which is worse than the error it replaces.
 */
fun dualisTrustManager(): X509TrustManager {
    val defaultTrustManager = defaultTrustManager()
    val bundled = loadBundledRootCertificates()
    if (bundled.isEmpty()) {
        // Resource missing from the packaged app: keep the default behaviour rather than fail to
        // start. The connection error that follows is at least the honest one.
        Napier.e("No bundled root certificates found — using the JDK trust store alone", tag = TAG)
        return defaultTrustManager
    }

    val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
    defaultTrustManager.acceptedIssuers.forEachIndexed { index, certificate ->
        store.setCertificateEntry("jdk-$index", certificate)
    }
    bundled.forEachIndexed { index, certificate ->
        store.setCertificateEntry("bundled-$index", certificate)
        Napier.d("Added bundled trust anchor: ${certificate.subjectX500Principal.name}", tag = TAG)
    }

    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(store)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}

/** An [SSLContext] backed by [dualisTrustManager]. */
fun dualisSslContext(trustManager: X509TrustManager): SSLContext =
    SSLContext.getInstance("TLSv1.2").apply { init(null, arrayOf(trustManager), null) }

fun defaultTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    // A null KeyStore means "the JDK default", which is exactly what we want to extend.
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}

fun loadBundledRootCertificates(): List<X509Certificate> {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    return BUNDLED_ROOT_CERTIFICATES.mapNotNull { path ->
        val stream = object {}.javaClass.getResourceAsStream(path)
        if (stream == null) {
            Napier.e("Bundled certificate $path is not on the classpath", tag = TAG)
            return@mapNotNull null
        }
        stream.use { certificateFactory.generateCertificate(it) as X509Certificate }
    }
}

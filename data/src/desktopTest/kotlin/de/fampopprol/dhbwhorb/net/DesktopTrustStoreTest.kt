package de.fampopprol.dhbwhorb.net

import java.security.MessageDigest
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dualis is served by HARICA, whose 2021 roots are missing from the JDK's `cacerts` — the desktop
 * app could not reach it at all ("PKIX path building failed") while Android and Apple, which use
 * their platform trust stores, were fine. The fix bundles those two roots; these tests are what
 * keeps the bundle honest.
 */
class DesktopTrustStoreTest {

    private val expectedFingerprints = mapOf(
        "HARICA TLS RSA Root CA 2021" to
            "D95D0E8EDA79525BF9BEB11B14D2100D3294985F0C62D9FABD9CD999ECCB7B1D",
        "HARICA TLS ECC Root CA 2021" to
            "3F99CC474ACFCE4DFED58794665E478D1547739F2E780F1BB4CA9B133097D401",
    )

    @Test
    fun `the bundled certificates are the ones this app means to trust`() {
        val bundled = loadBundledRootCertificates()
        assertEquals(
            expectedFingerprints.size,
            bundled.size,
            "a bundled certificate is missing from the classpath — the desktop build would fall " +
                "back to the JDK trust store and fail against Dualis",
        )

        bundled.forEach { certificate ->
            val commonName = certificate.commonName()
            val expected = expectedFingerprints[commonName]
            assertTrue(expected != null, "unexpected bundled certificate: $commonName")
            assertEquals(
                expected,
                certificate.sha256Fingerprint(),
                "$commonName does not have the fingerprint recorded in certs/README.md — a trust " +
                    "anchor was replaced, which is never a routine change",
            )
        }
    }

    @Test
    fun `the bundled certificates are self-signed roots, not intermediates`() {
        loadBundledRootCertificates().forEach { certificate ->
            assertEquals(
                certificate.subjectX500Principal,
                certificate.issuerX500Principal,
                "${certificate.commonName()} is not a root — only trust anchors belong here",
            )
        }
    }

    @Test
    fun `the trust manager adds anchors and removes none`() {
        val default = defaultTrustManager().acceptedIssuers.toSet()
        val extended = dualisTrustManager().acceptedIssuers.toSet()

        assertTrue(
            extended.containsAll(default),
            "the JDK's own trust anchors must survive — this may only ever add",
        )
        val added = (extended - default).map { it.commonName() }.toSet()
        assertEquals(expectedFingerprints.keys, added)
    }

    @Test
    fun `the JDK still lacks the roots that make this necessary`() {
        // The day a JDK baseline ships them, this fails and the bundle can go. That is the point:
        // a workaround should announce its own expiry rather than outlive the problem.
        val defaultCommonNames = defaultTrustManager().acceptedIssuers.map { it.commonName() }
        expectedFingerprints.keys.forEach { name ->
            assertTrue(
                name !in defaultCommonNames,
                "$name is in the JDK trust store now — drop it from resources/certs/ and from " +
                    "this test",
            )
        }
    }

    private fun X509Certificate.sha256Fingerprint(): String =
        MessageDigest.getInstance("SHA-256").digest(encoded)
            .joinToString("") { "%02X".format(it) }

    private fun X509Certificate.commonName(): String =
        Regex("CN=([^,]+)").find(subjectX500Principal.name)?.groupValues?.get(1).orEmpty()
}

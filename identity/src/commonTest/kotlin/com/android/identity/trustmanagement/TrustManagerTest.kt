package com.android.identity.trustmanagement

import com.android.identity.asn1.ASN1Integer
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.X500Name
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509KeyUsage
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours


class TrustManagerTest {

    val caCertificate: X509Cert
    val intermediateCertificate: X509Cert
    val dsCertificate: X509Cert

    val dsValidInThePastCertificate: X509Cert
    val dsValidInTheFutureCertificate: X509Cert

    val ca2Certificate: X509Cert
    val ds2Certificate: X509Cert

    init {
        val now = Clock.System.now()

        val caKey = Crypto.createEcPrivateKey(EcCurve.P384)
        caCertificate =
            X509Cert.Builder(
                publicKey = caKey.publicKey,
                signingKey = caKey,
                signatureAlgorithm = caKey.curve.defaultSigningAlgorithm,
                serialNumber = ASN1Integer(1L),
                subject = X500Name.fromName("CN=Test TrustManager CA"),
                issuer = X500Name.fromName("CN=Test TrustManager CA"),
                validFrom = now - 1.hours,
                validUntil = now + 1.hours
            )
                .includeSubjectKeyIdentifier()
                .setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
                .build()

        val intermediateKey = Crypto.createEcPrivateKey(EcCurve.P384)
        intermediateCertificate = X509Cert.Builder(
            publicKey = intermediateKey.publicKey,
            signingKey = caKey,
            signatureAlgorithm = caKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager Intermediate CA"),
            issuer = caCertificate.subject,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(caCertificate)
            .setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
            .build()

        val dsKey = Crypto.createEcPrivateKey(EcCurve.P384)
        dsCertificate = X509Cert.Builder(
            publicKey = dsKey.publicKey,
            signingKey = intermediateKey,
            signatureAlgorithm = intermediateKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager DS"),
            issuer = intermediateCertificate.subject,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(intermediateCertificate)
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()

        val dsValidInThePastKey = Crypto.createEcPrivateKey(EcCurve.P384)
        dsValidInThePastCertificate = X509Cert.Builder(
            publicKey = dsValidInThePastKey.publicKey,
            signingKey = intermediateKey,
            signatureAlgorithm = intermediateKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager DS Valid In The Past"),
            issuer = intermediateCertificate.subject,
            validFrom = now - 3.hours,
            validUntil = now - 1.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(intermediateCertificate)
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()

        val dsValidInTheFutureKey = Crypto.createEcPrivateKey(EcCurve.P384)
        dsValidInTheFutureCertificate = X509Cert.Builder(
            publicKey = dsValidInTheFutureKey.publicKey,
            signingKey = intermediateKey,
            signatureAlgorithm = intermediateKey.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager Valid In The Future"),
            issuer = intermediateCertificate.subject,
            validFrom = now + 1.hours,
            validUntil = now + 3.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(intermediateCertificate)
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()

        val ca2Key = Crypto.createEcPrivateKey(EcCurve.P384)
        ca2Certificate =
            X509Cert.Builder(
                publicKey = ca2Key.publicKey,
                signingKey = ca2Key,
                signatureAlgorithm = ca2Key.curve.defaultSigningAlgorithm,
                serialNumber = ASN1Integer(1L),
                subject = X500Name.fromName("CN=Test TrustManager CA2"),
                issuer = X500Name.fromName("CN=Test TrustManager CA2"),
                validFrom = now - 1.hours,
                validUntil = now + 1.hours
            )
                .includeSubjectKeyIdentifier()
                .setKeyUsage(setOf(X509KeyUsage.KEY_CERT_SIGN))
                .build()

        val ds2Key = Crypto.createEcPrivateKey(EcCurve.P384)
        ds2Certificate = X509Cert.Builder(
            publicKey = ds2Key.publicKey,
            signingKey = ca2Key,
            signatureAlgorithm = ca2Key.curve.defaultSigningAlgorithm,
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test TrustManager DS2"),
            issuer = ca2Certificate.subject,
            validFrom = now - 1.hours,
            validUntil = now + 1.hours
        )
            .includeSubjectKeyIdentifier()
            .setAuthorityKeyIdentifierToCertificate(ca2Certificate)
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()
    }

    @Test
    fun testTrustManagerHappyFlow() {
        val trustManager = TrustManager()

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        trustManager.addTrustPoint(TrustPoint(caCertificate))

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain.size)
            assertEquals(caCertificate, it.trustChain.last())
        }
    }

    @Test
    fun testTrustManagerValidInThePast() {
        val trustManager = TrustManager()

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        trustManager.addTrustPoint(TrustPoint(caCertificate))

        trustManager.verify(listOf(dsValidInThePastCertificate)).let {
            assertEquals("Certificate is no longer valid", it.error?.message)
            assertFalse(it.isTrusted)
            assertEquals(3, it.trustChain.size)
            assertEquals(caCertificate, it.trustChain.last())
        }
    }

    @Test
    fun testTrustManagerValidInTheFuture() {
        val trustManager = TrustManager()

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        trustManager.addTrustPoint(TrustPoint(caCertificate))

        trustManager.verify(listOf(dsValidInTheFutureCertificate)).let {
            assertEquals("Certificate is not yet valid", it.error?.message)
            assertFalse(it.isTrusted)
            assertEquals(3, it.trustChain.size)
            assertEquals(caCertificate, it.trustChain.last())
        }
    }

    @Test
    fun testTrustManagerHappyFlowWithOnlyIntermediateCertifcate() {
        val trustManager = TrustManager()

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(2, it.trustChain.size)
            assertEquals(intermediateCertificate, it.trustChain.last())
        }
    }

    @Test
    fun testTrustManagerHappyFlowWithChainOfTwo() {
        val trustManager = TrustManager()

        trustManager.addTrustPoint(TrustPoint(caCertificate))

        trustManager.verify(listOf(dsCertificate, intermediateCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain.size)
            assertEquals(caCertificate, it.trustChain.last())
        }
    }

    @Test
    fun testTrustManagerTrustPointNotCaCert() {
        val trustManager = TrustManager()

        trustManager.addTrustPoint(TrustPoint(dsCertificate))

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(1, it.trustChain.size)
            assertEquals(dsCertificate, it.trustChain.last())
        }
    }

    @Test
    fun testTrustManagerHappyFlowMultipleCerts() {
        val trustManager = TrustManager()

        trustManager.addTrustPoint(TrustPoint(intermediateCertificate))
        trustManager.addTrustPoint(TrustPoint(caCertificate))
        trustManager.addTrustPoint(TrustPoint(ca2Certificate))

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(3, it.trustChain.size)
            assertEquals(caCertificate, it.trustChain.last())
        }

        trustManager.verify(listOf(ds2Certificate)).let {
            assertEquals(null, it.error)
            assertTrue(it.isTrusted)
            assertEquals(2, it.trustChain.size)
            assertEquals(ca2Certificate, it.trustChain.last())
        }
    }

    @Test
    fun testTrustManagerNoTrustPoints() {
        val trustManager = TrustManager()

        trustManager.verify(listOf(dsCertificate)).let {
            assertEquals("No trusted root certificate could not be found", it.error?.message)
            assertFalse(it.isTrusted)
            assertEquals(0, it.trustChain.size)
        }
    }
}
/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.identity.mdoc.request

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.toDataItem
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.create
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// TODO: Add test which generates the exact bytes of TestVectors#ISO_18013_5_ANNEX_D_DEVICE_REQUEST
//

// NOTE: This is a Jvm test b/c we need to X509Cert.create() is JVM-only for now.

class DeviceRequestGeneratorTest {
    @BeforeTest
    fun setUp() {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    fun testDeviceRequestBuilder() {
        val encodedSessionTranscript = Cbor.encode(Bstr(byteArrayOf(0x01, 0x02)))
        val mdlItemsToRequest: MutableMap<String, Map<String, Boolean>> = HashMap()
        val mdlNsItems: MutableMap<String, Boolean> = HashMap()
        mdlNsItems["family_name"] = true
        mdlNsItems["portrait"] = false
        mdlItemsToRequest[MDL_NAMESPACE] = mdlNsItems
        val aamvaNsItems: MutableMap<String, Boolean> = HashMap()
        aamvaNsItems["real_id"] = false
        mdlItemsToRequest[AAMVA_NAMESPACE] = aamvaNsItems
        val mvrItemsToRequest: MutableMap<String, Map<String, Boolean>> = HashMap()
        val mvrNsItems: MutableMap<String, Boolean> = HashMap()
        mvrNsItems["vehicle_number"] = true
        mvrItemsToRequest[MVR_NAMESPACE] = mvrNsItems
        val readerKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val validFrom = Clock.System.now()
        val validUntil = Instant.fromEpochMilliseconds(
            validFrom.toEpochMilliseconds() + 30L * 24 * 60 * 60 * 1000
        )
        val readerCert = X509Cert.create(
            readerKey.publicKey,
            readerKey,
            null,
            Algorithm.ES256,
            "1",
            "CN=Test Key",
            "CN=Test Key",
            validFrom,
            validUntil,
            setOf(),
            listOf()
        )
        val readerCertChain = X509CertChain(listOf(readerCert))
        val mdlRequestInfo: MutableMap<String, ByteArray> = HashMap()
        mdlRequestInfo["foo"] = Cbor.encode(Tstr("bar"))
        mdlRequestInfo["bar"] = Cbor.encode(42.toDataItem())
        val encodedDeviceRequest = DeviceRequestGenerator(encodedSessionTranscript)
            .addDocumentRequest(
                MDL_DOCTYPE,
                mdlItemsToRequest,
                mdlRequestInfo,
                readerKey,
                readerKey.curve.defaultSigningAlgorithm,
                readerCertChain
            )
            .addDocumentRequest(
                MVR_DOCTYPE,
                mvrItemsToRequest,
                null,
                readerKey,
                readerKey.curve.defaultSigningAlgorithm,
                readerCertChain
            )
            .generate()
        val deviceRequest = DeviceRequestParser(
            encodedDeviceRequest,
            encodedSessionTranscript
        )
            .parse()
        assertEquals("1.0", deviceRequest.version)
        val documentRequests = deviceRequest.docRequests
        assertEquals(2, documentRequests.size.toLong())
        val it = deviceRequest.docRequests.iterator()
        var docRequest = it.next()
        assertTrue(docRequest.readerAuthenticated)
        assertEquals(MDL_DOCTYPE, docRequest.docType)
        assertEquals(2, docRequest.namespaces.size.toLong())
        assertEquals(2, docRequest.getEntryNames(MDL_NAMESPACE).size.toLong())
        assertTrue(docRequest.getIntentToRetain(MDL_NAMESPACE, "family_name"))
        assertFalse(docRequest.getIntentToRetain(MDL_NAMESPACE, "portrait"))
        assertFailsWith<IllegalArgumentException> {
            docRequest.getIntentToRetain(MDL_NAMESPACE, "non-existent")
        }
        assertEquals(1, docRequest.getEntryNames(AAMVA_NAMESPACE).size.toLong())
        assertFalse(docRequest.getIntentToRetain(AAMVA_NAMESPACE, "real_id"))
        assertFailsWith<IllegalArgumentException> {
            docRequest.getIntentToRetain("non-existent", "non-existent")
        }
        assertFailsWith<IllegalArgumentException> {
            docRequest.getEntryNames("non-existent")
        }
        assertEquals(1, docRequest.readerCertificateChain!!.certificates.size.toLong())
        assertEquals(readerCertChain, docRequest.readerCertificateChain)
        val requestInfo = docRequest.requestInfo
        assertNotNull(requestInfo)
        assertEquals(2, requestInfo.keys.size.toLong())
        assertContentEquals(Cbor.encode(Tstr("bar")), requestInfo["foo"])
        assertContentEquals(Cbor.encode(42.toDataItem()), requestInfo["bar"])
        docRequest = it.next()
        assertTrue(docRequest.readerAuthenticated)
        assertEquals(MVR_DOCTYPE, docRequest.docType)
        assertEquals(1, docRequest.namespaces.size.toLong())
        assertEquals(1, docRequest.getEntryNames(MVR_NAMESPACE).size.toLong())
        assertTrue(docRequest.getIntentToRetain(MVR_NAMESPACE, "vehicle_number"))
        assertEquals(1, docRequest.readerCertificateChain!!.certificates.size.toLong())
        assertEquals(readerCertChain, docRequest.readerCertificateChain)
        assertEquals(0, docRequest.requestInfo.size.toLong())
    }

    companion object {
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private const val AAMVA_NAMESPACE = "org.aamva.18013.5.1"
        private const val MVR_DOCTYPE = "org.iso.18013.7.1.mVR"
        private const val MVR_NAMESPACE = "org.iso.18013.7.1"
    }
}

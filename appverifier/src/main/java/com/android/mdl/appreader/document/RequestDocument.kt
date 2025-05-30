package com.android.mdl.appreader.document

import java.io.Serializable


data class RequestDocument(
    val docType: String,
    var itemsToRequest: Map<String, Map<String, Boolean>>
) : Serializable
{
    companion object {
        const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        const val MDL_NAMESPACE = "org.iso.18013.5.1"
        const val PHOTO_ID_DOCTYPE = "org.iso.23220.photoID.1"
        const val ISO_23220_2_NAMESPACE = "org.iso.23220.1"
        const val PHOTO_ID_NAMESPACE = "org.iso.23220.photoID.1"
        const val DTC_NAMESPACE = "org.iso.23220.dtc.1"
        const val PAYMENT_AUTH_DOCTYPE = "payment.auth.1"
        const val PAYMENT_AUTH_NAMESPACE = "payment.auth.1"
        const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
        const val EU_PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
    }
}
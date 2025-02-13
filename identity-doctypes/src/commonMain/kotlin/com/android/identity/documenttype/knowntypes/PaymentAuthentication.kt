package com.android.identity.documenttype.knowntypes

import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.Icon
import com.android.identity.documenttype.knowntypes.PhotoID.ISO_23220_2_NAMESPACE
import com.android.identity.documenttype.knowntypes.PhotoID.PHOTO_ID_DOCTYPE
import kotlinx.datetime.LocalDate

/**
 * Object containing the metadata of the Payment Instrument Document Type.
 */
object PaymentAuthentication {
    const val PAYMENT_AUTH_DOCTYPE = "payment.auth.1"       // EVO - update
    const val PAYMENT_AUTH_NAMESPACE = "payment.auth.1"

    /**
     * Build the Payment Instrument Document Type.
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("Payment Authentication Doc")
            .addMdocDocumentType(PAYMENT_AUTH_DOCTYPE)
            .addMdocAttribute(
                DocumentAttributeType.String,
                "payment_auth_number",
                "Payment Authentication",
                "Payment Authentication (card/account)",
                true,
                PAYMENT_AUTH_NAMESPACE,
                Icon.ACCOUNT_BALANCE,                                     //EVO - what is this?
                SampleData.PAYMENT_AUTH_NUMBER.toDataItem()
            )

            // Finally for the sample requests.
            //
            .addSampleRequest(
                id = "SCA",
                displayName ="Strong Customer Authentication",
                mdocDataElements = mapOf(PAYMENT_AUTH_NAMESPACE to mapOf())
            )

            .build()
    }
}

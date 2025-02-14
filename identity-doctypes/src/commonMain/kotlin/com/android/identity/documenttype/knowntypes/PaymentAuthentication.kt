package com.android.identity.documenttype.knowntypes

import com.android.identity.cbor.toDataItem
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.Icon
import com.android.identity.documenttype.StringOption

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
                "payment authentication (card/account)",
                true,
                PAYMENT_AUTH_NAMESPACE,
                Icon.ACCOUNT_BALANCE,                                     //EVO - what is this?
                SampleData.PAYMENT_AUTH_NUMBER.toDataItem()
            )

            .addMdocAttribute(
                DocumentAttributeType.String,
                "payment_auth_expiry",
                "Payment Authentication Expiry",
                "expiry date of payment authentication object",
                true,
                PAYMENT_AUTH_NAMESPACE,
                Icon.ACCOUNT_BALANCE,                                     //EVO - what is this?
                SampleData.PAYMENT_AUTH_EXPIRY.toDataItem()
            )

            .addMdocAttribute(
                DocumentAttributeType.String,
                "transaction_amount",
                "Transaction Amount",
                "transaction amount",
                true,
                PAYMENT_AUTH_NAMESPACE,
                Icon.ACCOUNT_BALANCE,                                     //EVO - what is this?
                SampleData.TRANSACTION_AMOUNT.toDataItem()
            )

            .addMdocAttribute(
                DocumentAttributeType.String,
                "transaction_currency_code",
                "Transaction Currency Code",
                "transaction currency code of transaction amount",
                true,
                PAYMENT_AUTH_NAMESPACE,
                Icon.ACCOUNT_BALANCE,                                     //EVO - what is this?
                SampleData.TRANSACTION_CURRENCY_CODE.toDataItem()
            )

            .addMdocAttribute(
                DocumentAttributeType.String,
                "merchant_name",
                "Merchant Name",
                "name of merchant at which transaction is conducted",
                true,
                PAYMENT_AUTH_NAMESPACE,
                Icon.ACCOUNT_BALANCE,                                     //EVO - what is this?
                SampleData.MERCHANT_NAME.toDataItem()
            )

            .addMdocAttribute(
                DocumentAttributeType.StringOptions(
                    listOf(
                        StringOption("V", "Visa"),
                        StringOption("M", "Mastercard"),
                        StringOption("A", "American Express"),
                        StringOption("D", "Discover Network"),
                        StringOption("J", "JCB"),
                        StringOption("U", "Union Pay"),
                        StringOption("I", "iDeal"),
                        StringOption("S", "SEPA")
                    )
                ),
                "payment_scheme",
                "Payment Scheme",
                "governing payment scheme of issued credential",
                true,
                PAYMENT_AUTH_NAMESPACE,
                Icon.ACCOUNT_BALANCE,                                     //EVO - what is this?
                SampleData.PAYMENT_SCHEME.toDataItem()
            )

            .addMdocAttribute(
                DocumentAttributeType.StringOptions(
                    listOf(
                        StringOption("D", "Debit"),
                        StringOption("C", "Credit")
                    )
                ),
                "payment_type",
                "Payment Type",
                "indicates the payment type (credit/debit)",
                true,
                PAYMENT_AUTH_NAMESPACE,
                Icon.ACCOUNT_BALANCE,                                     //EVO - what is this?
                SampleData.PAYMENT_TYPE.toDataItem()
            )

            // Finally for the sample requests.
            //
            .addSampleRequest(
                id = "sca",
                displayName ="Strong Customer Authentication",
                mdocDataElements = mapOf(
                    PAYMENT_AUTH_NAMESPACE to mapOf(
                        "payment_auth_number" to false,
                        "transaction_amount" to false,
                        "transaction_currency_code" to false,
                        "merchant_name" to false
                    )
                )
            )

            .addSampleRequest(
                id = "payment_initiation",
                displayName ="Payment Initiation",
                mdocDataElements = mapOf(
                    PAYMENT_AUTH_NAMESPACE to mapOf(
                        "payment_auth_number" to false,
                        "payment_auth_expiry" to false,
                        "transaction_amount" to false,
                        "transaction_currency_code" to false,
                        "merchant_name" to false,
                        "payment_scheme" to false,
                        "payment_type" to false
                    )
                )
            )

            .addSampleRequest(
                id = "full",
                displayName ="All Data Elements",
                mdocDataElements = mapOf(
                    PAYMENT_AUTH_NAMESPACE to mapOf()
                )
            )

            .build()
    }
}

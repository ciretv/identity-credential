package com.android.mdl.appreader.home

import android.content.Context
import android.app.AlertDialog
import android.widget.EditText
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.android.mdl.appreader.R
import com.android.identity.shared.GlobalData

@Stable
@Immutable
data class RequestingDocumentState(
    val olderThan18: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_over_18),
    val olderThan21: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_over_21),
    val mandatoryFields: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_mandatory_fields),
    val fullMdl: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_full),
    val mdlForUsTransportation: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_us_transportation, true),
    val custom: DocumentElementsRequest = DocumentElementsRequest(R.string.mdl_custom),
    val photoID: DocumentElementsRequest = DocumentElementsRequest(R.string.photoID_full),
    val euPid: DocumentElementsRequest = DocumentElementsRequest(R.string.eu_pid_full),
    val paymentAuthentication_sca: DocumentElementsRequest = DocumentElementsRequest(R.string.payment_authentication_sca),
    val payment_initiation: DocumentElementsRequest = DocumentElementsRequest(R.string.payment_initiation),
) {

    val isCustomMdlRequest: Boolean
        get() = custom.isSelected

    val hasMdlElementsSelected: Boolean
        get() = olderThan18.isSelected
                || olderThan21.isSelected
                || mandatoryFields.isSelected
                || fullMdl.isSelected
                || mdlForUsTransportation.isSelected
                || custom.isSelected

    fun getCurrentRequestSelection(context: Context, onComplete: (String) -> Unit) {
        val selection = StringBuilder()

        if (olderThan18.isSelected) selection.append("Over 18; ")
        if (olderThan21.isSelected) selection.append("Over 21; ")
        if (mandatoryFields.isSelected) selection.append("mDL Mandatory Fields; ")
        if (fullMdl.isSelected) selection.append("Driving Licence; ")
        if (mdlForUsTransportation.isSelected) selection.append("mDL for US transportation; ")
        if (custom.isSelected) selection.append("mDL Custom; ")
        if (photoID.isSelected) selection.append("Photo ID; ")
        if (euPid.isSelected) selection.append("European Personal ID; ")
        if (paymentAuthentication_sca.isSelected) {
            showTransactionInputDialogs(context, requiresSchemeAndType=false) { amount, currency, merchant, scheme, type ->
                selection.append("Payment Initiation; Amount: $amount; Currency: $currency; Merchant: $merchant; ")

                if (scheme != null) selection.append("Payment Scheme: $scheme; ")
                if (type != null) selection.append("EPayment Type: $type; ")

                onComplete(selection.toString())
            }
            } else {
                onComplete(selection.toString())
            }
        if (payment_initiation.isSelected) {
            showTransactionInputDialogs(context, requiresSchemeAndType=true) { amount, currency, merchant, scheme, type ->
                selection.append("Payment Initiation; Amount: $amount; Currency: $currency; Merchant: $merchant; ")

                if (scheme != null) selection.append("Payment Scheme: $scheme; ")
                if (type != null) selection.append("EPayment Type: $type; ")

                onComplete(selection.toString())
            }
        } else {
            onComplete(selection.toString())
        }
    }

    private fun showTransactionInputDialogs(
        context: Context,
        requiresSchemeAndType: Boolean, // Condition to determine if PAN & Expiry are needed
        onComplete: (amount: String, currency: String, merchant: String, scheme: String?, type: String?) -> Unit
    ) {
        val amountEditText = EditText(context).apply { hint = "Enter Transaction Amount" }

        AlertDialog.Builder(context)
            .setTitle("Transaction Amount")
            .setView(amountEditText)
            .setPositiveButton("Next") { _, _ ->
                val transactionAmount = amountEditText.text.toString()
                GlobalData.requestTransactionAmount = transactionAmount

                val currencyEditText = EditText(context).apply { hint = "Enter Currency Code" }
                AlertDialog.Builder(context)
                    .setTitle("Currency Code")
                    .setView(currencyEditText)
                    .setPositiveButton("Next") { _, _ ->
                        val transactionCurrency = currencyEditText.text.toString()
                        GlobalData.requestTransactionCurrency = transactionCurrency

                        val merchantEditText = EditText(context).apply { hint = "Enter Merchant Name" }
                        AlertDialog.Builder(context)
                            .setTitle("Merchant Name")
                            .setView(merchantEditText)
                            .setPositiveButton("Next") { _, _ ->
                                val merchantName = merchantEditText.text.toString()
                                GlobalData.requestMerchantName = merchantName

                                // If PAN & Expiry are required, show additional dialogs
                                if (requiresSchemeAndType) {
                                    val schemeEditText = EditText(context).apply { hint = "Enter Payment Scheme" }
                                    AlertDialog.Builder(context)
                                        .setTitle("Payment Scheme (e.g., V, M, D ,A")
                                        .setView(schemeEditText)
                                        .setPositiveButton("Next") { _, _ ->
                                            val scheme = schemeEditText.text.toString().takeIf { it.isNotBlank() } // Null if blank

                                            val typeEditText = EditText(context).apply { hint = "Enter Payment Type" }
                                            AlertDialog.Builder(context)
                                                .setTitle("Payment Type (e.g., C, D)")
                                                .setView(typeEditText)
                                                .setPositiveButton("Submit") { _, _ ->
                                                    val type = typeEditText.text.toString().takeIf { it.isNotBlank() } // Null if blank

                                                    // Store optional values
                                                    if (scheme != null) GlobalData.requestScheme = scheme
                                                    if (type != null) GlobalData.requestType = type

                                                    onComplete(transactionAmount, transactionCurrency, merchantName, scheme, type)
                                                }
                                                .setNegativeButton("Skip") { _, _ ->
                                                    onComplete(transactionAmount, transactionCurrency, merchantName, scheme, null)
                                                }
                                                .show()
                                        }
                                        .setNegativeButton("Skip") { _, _ ->
                                            onComplete(transactionAmount, transactionCurrency, merchantName, null, null)
                                        }
                                        .show()
                                } else {
                                    // If PAN & Expiry are not required, call onComplete immediately
                                    onComplete(transactionAmount, transactionCurrency, merchantName, null, null)
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    fun isMdlRequest(request: DocumentElementsRequest): Boolean {
        return request.title == olderThan18.title
                || request.title == olderThan21.title
                || request.title == mandatoryFields.title
                || request.title == fullMdl.title
                || request.title == mdlForUsTransportation.title
                || request.title == custom.title
    }
}

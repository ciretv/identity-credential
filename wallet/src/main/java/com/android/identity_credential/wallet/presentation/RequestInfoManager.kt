package com.android.identity_credential.wallet.presentation

object RequestInfoManager {
    private var requestInfo: Map<String, ByteArray>? = null
    private var docType: String = ""

    fun setRequestInfo(info: Map<String, ByteArray>?) {
        requestInfo = info
    }

    fun setDocType(info: String) {
        docType = info
    }

    fun getRequestInfo(): Map<String, ByteArray>? {
        return requestInfo
    }

    fun getDocType(): String {
        return docType
    }
}

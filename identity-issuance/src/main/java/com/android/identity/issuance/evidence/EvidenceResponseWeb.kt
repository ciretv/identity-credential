package com.android.identity.issuance.evidence

data class EvidenceResponseWeb(
    /** Portion of the URL used for redirecting (not including EvidenceRequestWeb.redirectUri) */
    val response: String
) : EvidenceResponse()
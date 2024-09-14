package com.android.identity.issuance.wallet

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowJoin
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.handler.FlowDispatcherLocal
import com.android.identity.flow.handler.FlowExceptionMap
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.ApplicationSupport
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.IssuingAuthorityException
import com.android.identity.issuance.LandingUrlUnknownException
import com.android.identity.issuance.WalletServer
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.issuance.common.AbstractIssuingAuthorityState
import com.android.identity.issuance.hardcoded.IssuingAuthorityState
import com.android.identity.issuance.hardcoded.ProofingState
import com.android.identity.issuance.hardcoded.RegistrationState
import com.android.identity.issuance.hardcoded.RequestCredentialsState
import com.android.identity.issuance.hardcoded.register
import com.android.identity.issuance.funke.FunkeIssuingAuthorityState
import com.android.identity.issuance.funke.FunkeProofingState
import com.android.identity.issuance.funke.FunkeRegistrationState
import com.android.identity.issuance.funke.FunkeRequestCredentialsState
import com.android.identity.issuance.funke.register
import com.android.identity.issuance.register
import kotlinx.io.bytestring.buildByteString
import kotlin.random.Random

@FlowState(
    flowInterface = WalletServer::class,
    path = "root",
    creatable = true
)
@CborSerializable
class WalletServerState(
    var clientId: String = ""
) {
    companion object {
        private const val TAG = "WalletServerState"

        private fun devConfig(env: FlowEnvironment): IssuingAuthorityConfiguration {
            val resources = env.getInterface(Resources::class)!!
            val logo = resources.getRawResource("default/logo.png")!!
            val art = resources.getRawResource("default/card_art.png")!!
            return IssuingAuthorityConfiguration(
                identifier = "utopia_dev",
                issuingAuthorityName = "Utopia DMV (not configured)",
                issuingAuthorityLogo = logo.toByteArray(),
                issuingAuthorityDescription = "Utopia Driver's License",
                pendingDocumentInformation = DocumentConfiguration(
                    displayName = "Pending",
                    typeDisplayName = "Driving License",
                    cardArt = art.toByteArray(),
                    requireUserAuthenticationToViewDocument = true,
                    mdocConfiguration = null,
                    sdJwtVcDocumentConfiguration = null
                ),
                numberOfCredentialsToRequest = 3,
                minCredentialValidityMillis = 30 * 24 * 3600L,
                maxUsesPerCredentials = 1
            )
        }

        fun registerExceptions(exceptionMapBuilder: FlowExceptionMap.Builder) {
            IssuingAuthorityException.register(exceptionMapBuilder)
            LandingUrlUnknownException.register(exceptionMapBuilder)
        }

        fun registerAll(dispatcher: FlowDispatcherLocal.Builder) {
            WalletServerState.register(dispatcher)
            ApplicationSupportState.register(dispatcher)
            AuthenticationState.register(dispatcher)
            IssuingAuthorityState.register(dispatcher)
            ProofingState.register(dispatcher)
            RegistrationState.register(dispatcher)
            RequestCredentialsState.register(dispatcher)
            FunkeIssuingAuthorityState.register(dispatcher)
            FunkeProofingState.register(dispatcher)
            FunkeRegistrationState.register(dispatcher)
            FunkeRequestCredentialsState.register(dispatcher)
        }
    }

    @FlowMethod
    fun authenticate(env: FlowEnvironment): AuthenticationState {
        return AuthenticationState(nonce = buildByteString { Random.nextBytes(16) })
    }

    @FlowJoin
    fun completeAuthentication(env: FlowEnvironment, authenticationState: AuthenticationState) {
        check(authenticationState.authenticated)
        check(authenticationState.clientId.isNotEmpty())
        this.clientId = authenticationState.clientId
    }

    @FlowMethod
    fun applicationSupport(env: FlowEnvironment): ApplicationSupportState {
        check(clientId.isNotEmpty())
        if (env.getInterface(ApplicationSupport::class) != null) {
            // If this interface resolves, it means we are running in-app. But ApplicationSupport
            // is not going to work properly when run in-app.
            throw IllegalStateException("Only server-side ApplicationSupport must be used")
        }
        return ApplicationSupportState(clientId)
    }

    @FlowMethod
    fun getIssuingAuthorityConfigurations(env: FlowEnvironment): List<IssuingAuthorityConfiguration> {
        check(clientId.isNotEmpty())
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val issuingAuthorityList = settings.getStringList("issuingAuthorityList")
        val fromConfig = if (issuingAuthorityList.isEmpty()) {
            listOf(devConfig(env))
        } else {
            issuingAuthorityList.map { idElem ->
                IssuingAuthorityState.getConfiguration(env, idElem)
            }
        }
        return fromConfig + listOf(
            FunkeIssuingAuthorityState.getConfiguration(env, CredentialFormat.SD_JWT_VC),
            FunkeIssuingAuthorityState.getConfiguration(env, CredentialFormat.MDOC_MSO)
        )
    }

    @FlowMethod
    fun getIssuingAuthority(env: FlowEnvironment, identifier: String): AbstractIssuingAuthorityState {
        check(clientId.isNotEmpty())
        return when (identifier) {
            "funkeSdJwtVc" -> FunkeIssuingAuthorityState(clientId, CredentialFormat.SD_JWT_VC)
            "funkeMdocMso" -> FunkeIssuingAuthorityState(clientId, CredentialFormat.MDOC_MSO)
            else -> IssuingAuthorityState(clientId, identifier)
        }
    }
}

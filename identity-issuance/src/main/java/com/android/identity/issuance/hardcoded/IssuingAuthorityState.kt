package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborInt
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.DiagnosticOption
import com.android.identity.cbor.RawCbor
import com.android.identity.cbor.Simple
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemDateTimeString
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.CoseNumberLabel
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.EcPublicKeyDoubleCoordinate
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.documenttype.knowntypes.GermanPersonalID
import com.android.identity.documenttype.knowntypes.PhotoID
import com.android.identity.documenttype.knowntypes.PaymentAuthentication
import com.android.identity.flow.annotation.FlowJoin
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.Configuration
import com.android.identity.flow.server.Resources
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.DocumentState
import com.android.identity.issuance.IssuingAuthority
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.MdocDocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.IssuingAuthorityNotification
import com.android.identity.issuance.SdJwtVcDocumentConfiguration
import com.android.identity.issuance.WalletApplicationCapabilities
import com.android.identity.issuance.WalletServerSettings
import com.android.identity.issuance.common.AbstractIssuingAuthorityState
import com.android.identity.flow.cache
import com.android.identity.flow.server.getTable
import com.android.identity.issuance.evidence.DirectAccessDocumentConfiguration
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseGermanEidResolved
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import com.android.identity.issuance.evidence.EvidenceResponseIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.fromCbor
import com.android.identity.issuance.proofing.defaultCredentialConfiguration
import com.android.identity.issuance.wallet.AuthenticationState
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.mrtd.MrtdNfcData
import com.android.identity.mrtd.MrtdNfcDataDecoder
import com.android.identity.sdjwt.Issuer
import com.android.identity.sdjwt.SdJwtVcGenerator
import com.android.identity.sdjwt.util.JsonWebKey
import com.android.identity.storage.StorageTableSpec
import com.android.identity.util.Logger
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

private const val MDL_DOCTYPE = DrivingLicense.MDL_DOCTYPE
private const val MDL_NAMESPACE = DrivingLicense.MDL_NAMESPACE
private const val AAMVA_NAMESPACE = DrivingLicense.AAMVA_NAMESPACE

private const val EUPID_DOCTYPE = EUPersonalID.EUPID_DOCTYPE
private const val EUPID_NAMESPACE = EUPersonalID.EUPID_NAMESPACE

private const val PHOTO_ID_DOCTYPE = PhotoID.PHOTO_ID_DOCTYPE
private const val PHOTO_ID_NAMESPACE = PhotoID.PHOTO_ID_NAMESPACE
private const val ISO_23220_2_NAMESPACE = PhotoID.ISO_23220_2_NAMESPACE

private const val PAYMENT_AUTH_DOCTYPE = PaymentAuthentication.PAYMENT_AUTH_DOCTYPE
private const val PAYMENT_AUTH_NAMESPACE = PaymentAuthentication.PAYMENT_AUTH_NAMESPACE

/**
 * State of [IssuingAuthority] RPC implementation.
 */
@FlowState(
    flowInterface = IssuingAuthority::class
)
@CborSerializable
class IssuingAuthorityState(
    val clientId: String = "",
    val authorityId: String = ""
) : AbstractIssuingAuthorityState() {
    companion object {
        private const val TAG = "IssuingAuthorityState"
        
        private const val TYPE_EU_PID = "EuPid"
        private const val TYPE_DRIVING_LICENSE = "DrivingLicense"
        private const val TYPE_PHOTO_ID = "PhotoId"
        private const val TYPE_PAYMENT_AUTH = "PaymentAuthentication"

        suspend fun getConfiguration(env: FlowEnvironment, id: String): IssuingAuthorityConfiguration {
            return env.cache(IssuingAuthorityConfiguration::class, id) { configuration, resources ->
                val settings = WalletServerSettings(configuration)
                val prefix = "issuingAuthority.$id"
                val logoPath = settings.getString("${prefix}.logo") ?: "default/logo.png"
                val logo = resources.getRawResource(logoPath)!!
                val artPath =
                    settings.getString("${prefix}.cardArt") ?: "default/card_art.png"
                val art = resources.getRawResource(artPath)!!
                val requireUserAuthenticationToViewDocument = settings.getBool(
                    "${prefix}.requireUserAuthenticationToViewDocument",
                    false
                )
                val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE

                IssuingAuthorityConfiguration(
                    identifier = id,
                    issuingAuthorityName = settings.getString("${prefix}.name") ?: "Untitled",
                    issuingAuthorityLogo = logo.toByteArray(),
                    issuingAuthorityDescription = settings.getString("${prefix}.description")
                        ?: "Unknown",
                    pendingDocumentInformation = DocumentConfiguration(
                        displayName = "Pending",
                        typeDisplayName = when (type) {
                            TYPE_DRIVING_LICENSE -> "Driving License"
                            TYPE_EU_PID -> "EU Personal ID"
                            TYPE_PHOTO_ID -> "Photo ID"
                            TYPE_PAYMENT_AUTH -> "Payment Authentication"
                            else -> throw IllegalArgumentException("Unknown type $type")
                        },
                        cardArt = art.toByteArray(),
                        requireUserAuthenticationToViewDocument = requireUserAuthenticationToViewDocument,
                        mdocConfiguration = null,
                        directAccessConfiguration = null,
                        sdJwtVcDocumentConfiguration = null
                    ),
                    numberOfCredentialsToRequest = 3,
                    minCredentialValidityMillis = 30 * 24 * 3600L,
                    maxUsesPerCredentials = 1
                )
            }
        }

        // TODO: calling app should pass in DocumentTypeRepository, we shouldn't create it here
        val documentTypeRepository = DocumentTypeRepository().apply {
            addDocumentType(DrivingLicense.getDocumentType())
            addDocumentType(GermanPersonalID.getDocumentType())
            addDocumentType(EUPersonalID.getDocumentType())
            addDocumentType(PhotoID.getDocumentType())
            addDocumentType(PaymentAuthentication.getDocumentType())
        }

        val documentTableSpec = StorageTableSpec(
            name = "HardcodedIssuerDocument",
            supportPartitions = true,
            supportExpiration = false
        )
    }

    @FlowMethod
    suspend fun register(env: FlowEnvironment): RegistrationState {
        val documentId = createIssuerDocument(env,
            IssuerDocument(
                RegistrationResponse(false),
                DocumentCondition.PROOFING_REQUIRED,
                mutableMapOf(),
                null,
                mutableListOf()
            )
        )
        return RegistrationState(documentId)
    }

    @FlowMethod
    suspend fun getConfiguration(env: FlowEnvironment): IssuingAuthorityConfiguration {
        return getConfiguration(env, authorityId)
    }

    @FlowJoin
    suspend fun completeRegistration(env: FlowEnvironment, registrationState: RegistrationState) {
        updateIssuerDocument(
            env,
            registrationState.documentId,
            IssuerDocument(
                registrationState.response!!,
                DocumentCondition.PROOFING_REQUIRED,
                mutableMapOf(),           // collectedEvidence - initially empty
                null,  // no initial document configuration
                mutableListOf()           // cpoRequests - initially empty
            ))
    }

    @FlowMethod
    suspend fun getState(env: FlowEnvironment, documentId: String): DocumentState {
        val now = Clock.System.now()

        if (!issuerDocumentExists(env, documentId)) {
            return DocumentState(
                now,
                DocumentCondition.NO_SUCH_DOCUMENT,
                0,
                0
            )
        }

        val issuerDocument = loadIssuerDocument(env, documentId)

        if (issuerDocument.state == DocumentCondition.PROOFING_PROCESSING) {
            issuerDocument.state =
                if (checkEvidence(issuerDocument.collectedEvidence)) {
                    DocumentCondition.CONFIGURATION_AVAILABLE
                } else {
                    DocumentCondition.PROOFING_FAILED
                }
            updateIssuerDocument(env, documentId, issuerDocument)
        }

        // This information is helpful for when using the admin web interface.
        Logger.i(TAG, "Returning state for clientId=$clientId " +
                "issuingAuthorityId=$authorityId documentId=$documentId")

        // We create and sign Credential requests immediately so numPendingCredentialRequests is
        // always 0
        return DocumentState(
            now,
            issuerDocument.state,
            0,
            issuerDocument.simpleCredentialRequests.count()
        )
    }

    @FlowMethod
    suspend fun proof(env: FlowEnvironment, documentId: String): ProofingState {
        val issuerDocument = loadIssuerDocument(env, documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING
        issuerDocument.collectedEvidence.clear()
        // TODO: propagate developer mode
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        return ProofingState(clientId, documentId, authorityId, settings.developerMode)
    }

    @FlowJoin
    suspend fun completeProof(env: FlowEnvironment, state: ProofingState) {
        val issuerDocument = loadIssuerDocument(env, state.documentId)
        issuerDocument.state = DocumentCondition.PROOFING_PROCESSING
        issuerDocument.collectedEvidence.putAll(state.evidence)
        updateIssuerDocument(env, state.documentId, issuerDocument)
    }

    @FlowMethod
    suspend fun getDocumentConfiguration(env: FlowEnvironment, documentId: String): DocumentConfiguration {
        val issuerDocument = loadIssuerDocument(env, documentId)
        check(issuerDocument.state == DocumentCondition.CONFIGURATION_AVAILABLE)
        issuerDocument.state = DocumentCondition.READY
        if (issuerDocument.documentConfiguration == null) {
            issuerDocument.documentConfiguration =
                generateDocumentConfiguration(env, issuerDocument.collectedEvidence)
        }
        updateIssuerDocument(env, documentId, issuerDocument)
        return issuerDocument.documentConfiguration!!
    }

    @FlowMethod
    suspend fun requestCredentials(env: FlowEnvironment, documentId: String): RequestCredentialsState {
        val issuerDocument = loadIssuerDocument(env, documentId)
        check(issuerDocument.state == DocumentCondition.READY)

        val storage = env.getTable(AuthenticationState.walletAppCapabilitiesTableSpec)
        val walletApplicationCapabilities = storage.get(clientId)?.let {
                WalletApplicationCapabilities.fromCbor(it.toByteArray())
            } ?: throw IllegalStateException("WalletApplicationCapabilities not found")

        val credentialConfiguration = defaultCredentialConfiguration(
            documentId,
            walletApplicationCapabilities,
            issuerDocument.collectedEvidence
        )
        return RequestCredentialsState(documentId, credentialConfiguration)
    }

    @FlowJoin
    suspend fun completeRequestCredentials(env: FlowEnvironment, state: RequestCredentialsState) {
        val issuerDocument = loadIssuerDocument(env, state.documentId)
        for (request in state.credentialRequests) {
            // Skip if we already have a request for the authentication key
            if (hasCpoRequestForAuthenticationKey(issuerDocument,
                    request.secureAreaBoundKeyAttestation.publicKey)) {
                continue
            }
            val authenticationKey = request.secureAreaBoundKeyAttestation.publicKey
            val presentationData = createPresentationData(
                env,
                state.format!!,
                issuerDocument.documentConfiguration!!,
                authenticationKey
            )
            val simpleCredentialRequest = SimpleCredentialRequest(
                authenticationKey,
                CredentialFormat.MDOC_MSO,
                presentationData,
            )
            issuerDocument.simpleCredentialRequests.add(simpleCredentialRequest)
        }
        updateIssuerDocument(env, state.documentId, issuerDocument)
    }

    @FlowMethod
    suspend fun getCredentials(env: FlowEnvironment, documentId: String): List<CredentialData> {
        val now = Clock.System.now()
        val validFrom = now
        val validUntil = now + 30.days
        val issuerDocument = loadIssuerDocument(env, documentId)
        val availableCPOs = mutableListOf<CredentialData>()
        for (cpoRequest in issuerDocument.simpleCredentialRequests) {
            availableCPOs.add(
                CredentialData(
                    cpoRequest.authenticationKey,
                    validFrom,
                    validUntil,
                    cpoRequest.format,
                    cpoRequest.data,
                )
            )
        }
        issuerDocument.simpleCredentialRequests.clear()
        updateIssuerDocument(env, documentId, issuerDocument)
        return availableCPOs
    }

    @FlowMethod
    suspend fun developerModeRequestUpdate(
        env: FlowEnvironment,
        documentId: String,
        requestRemoteDeletion: Boolean,
        notifyApplicationOfUpdate: Boolean
    ) {
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val prefix = "issuingAuthority.$authorityId"
        val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE

        if (!settings.developerMode) {
            throw IllegalStateException("Must be in developer mode for this feature to work")
        }

        if (requestRemoteDeletion) {
            deleteIssuerDocument(env, documentId, notifyApplicationOfUpdate)
        } else {
            val issuerDocument = loadIssuerDocument(env, documentId)
            issuerDocument.state = DocumentCondition.CONFIGURATION_AVAILABLE

            // The update consists of just slapping an extra 0 at the end of `administrative_number`
            val namespace = when (type) {
                TYPE_DRIVING_LICENSE -> MDL_NAMESPACE
                TYPE_EU_PID -> EUPID_NAMESPACE
                TYPE_PHOTO_ID -> PHOTO_ID_NAMESPACE
                TYPE_PAYMENT_AUTH -> PAYMENT_AUTH_NAMESPACE
                else -> throw IllegalArgumentException("Unknown type $type")
            }
            val newAdministrativeNumber = try {
                issuerDocument.documentConfiguration!!.mdocConfiguration!!.staticData
                    .getDataElementString(namespace, "administrative_number")
            } catch (e: Throwable) {
                ""
            } + "0"

            val builder = NameSpacedData.Builder(
                issuerDocument.documentConfiguration!!.mdocConfiguration!!.staticData
            )
            builder.putEntryString(
                namespace,
                "administrative_number",
                newAdministrativeNumber
            )

            issuerDocument.documentConfiguration = DocumentConfiguration(
                issuerDocument.documentConfiguration!!.displayName,
                issuerDocument.documentConfiguration!!.typeDisplayName,
                issuerDocument.documentConfiguration!!.cardArt,
                issuerDocument.documentConfiguration!!.requireUserAuthenticationToViewDocument,
                MdocDocumentConfiguration(
                    issuerDocument.documentConfiguration!!.mdocConfiguration!!.docType,
                    builder.build()
                ),
                issuerDocument.documentConfiguration!!.sdJwtVcDocumentConfiguration,
                issuerDocument.documentConfiguration!!.directAccessConfiguration
            )
            updateIssuerDocument(env, documentId, issuerDocument, notifyApplicationOfUpdate)
        }
    }

    suspend fun administrativeActionUpdateAdministrativeNumber(
        env: FlowEnvironment,
        documentId: String,
        administrativeNumber: String
    ) {
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val prefix = "issuingAuthority.$authorityId"
        val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE

        val issuerDocument = loadIssuerDocument(env, documentId)
        issuerDocument.state = DocumentCondition.CONFIGURATION_AVAILABLE

        val builder = NameSpacedData.Builder(
            issuerDocument.documentConfiguration!!.mdocConfiguration!!.staticData
        )
        val namespace = when (type) {
            TYPE_DRIVING_LICENSE -> MDL_NAMESPACE
            TYPE_EU_PID -> EUPID_NAMESPACE
            TYPE_PHOTO_ID -> PHOTO_ID_NAMESPACE
            TYPE_PAYMENT_AUTH -> PAYMENT_AUTH_NAMESPACE
            else -> throw IllegalArgumentException("Unknown type $type")
        }
        builder.putEntryString(
            namespace,
            "administrative_number",
            administrativeNumber
        )
        issuerDocument.documentConfiguration = DocumentConfiguration(
            issuerDocument.documentConfiguration!!.displayName,
            issuerDocument.documentConfiguration!!.typeDisplayName,
            issuerDocument.documentConfiguration!!.cardArt,
            issuerDocument.documentConfiguration!!.requireUserAuthenticationToViewDocument,
            MdocDocumentConfiguration(
                issuerDocument.documentConfiguration!!.mdocConfiguration!!.docType,
                builder.build()
            ),
            issuerDocument.documentConfiguration!!.sdJwtVcDocumentConfiguration,
            issuerDocument.documentConfiguration!!.directAccessConfiguration
        )
        updateIssuerDocument(env, documentId, issuerDocument, true)
    }

    private fun createPresentationData(
        env: FlowEnvironment,
        format: CredentialFormat,
        documentConfiguration: DocumentConfiguration,
        authenticationKey: EcPublicKey
    ): ByteArray = when (format) {
        CredentialFormat.MDOC_MSO -> createPresentationDataMdoc(env, documentConfiguration, authenticationKey, false)
        CredentialFormat.SD_JWT_VC -> createPresentationDataSdJwt(env, documentConfiguration, authenticationKey)
        CredentialFormat.DirectAccess -> createPresentationDataMdoc(env, documentConfiguration, authenticationKey, true)
    }

    private fun createPresentationDataMdoc(
        env: FlowEnvironment,
        documentConfiguration: DocumentConfiguration,
        authenticationKey: EcPublicKey,
        isDirectAccess: Boolean
    ): ByteArray {
        val now = Clock.System.now()

        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val prefix = "issuingAuthority.$authorityId"
        val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE

        // Create AuthKeys and MSOs, make sure they're valid for 30 days. Also make
        // sure to not use fractional seconds as 18013-5 calls for this (clauses 7.1
        // and 9.1.2.4)
        //
        val timeSigned = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validFrom = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validUntil = validFrom + 30.days

        // Generate an MSO and issuer-signed data for this authentication key.
        val docType = when (type) {
            TYPE_DRIVING_LICENSE -> MDL_DOCTYPE
            TYPE_EU_PID -> EUPID_DOCTYPE
            TYPE_PHOTO_ID -> PHOTO_ID_DOCTYPE
            TYPE_PAYMENT_AUTH -> PAYMENT_AUTH_DOCTYPE
            else -> throw IllegalArgumentException("Unknown type $type")
        }
        val msoGenerator = MobileSecurityObjectGenerator(
            "SHA-256",
            docType,
            authenticationKey
        )
        msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)
        val randomProvider = Random.Default
        val staticData = if (isDirectAccess) {
            documentConfiguration.directAccessConfiguration!!.staticData
        } else {
            documentConfiguration.mdocConfiguration!!.staticData
        }
        val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
            staticData,
            randomProvider,
            16,
            null
        )
        for (nameSpaceName in issuerNameSpaces.keys) {
            val digests = MdocUtil.calculateDigestsForNameSpace(
                nameSpaceName,
                issuerNameSpaces,
                Algorithm.SHA256
            )
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
        }

        val deviceKeyAuthorizedDataElements: MutableMap<String, List<String>> = HashMap()
        deviceKeyAuthorizedDataElements["payment.auth.1"] = listOf("merchant_name", "transaction_amount", "transaction_currency_code")
        msoGenerator.setDeviceKeyAuthorizedDataElements(deviceKeyAuthorizedDataElements)

        val resources = env.getInterface(Resources::class)!!
        val documentSigningKeyCert = X509Cert.fromPem(
            resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey
        )

        val mso = msoGenerator.generate()
        val taggedEncodedMso = Cbor.encode(Tagged(Tagged.ENCODED_CBOR, Bstr(mso)))
        val protectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_ALG),
            Algorithm.ES256.coseAlgorithmIdentifier.toDataItem()
        ))
        val unprotectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
            X509CertChain(listOf(
                X509Cert(documentSigningKeyCert.encodedCertificate))
            ).toDataItem()
        ))
        val encodedIssuerAuth = Cbor.encode(
            Cose.coseSign1Sign(
                documentSigningKey,
                taggedEncodedMso,
                true,
                Algorithm.ES256,
                protectedHeaders,
                unprotectedHeaders
            ).toDataItem()
        )

        val issuerProvidedAuthenticationData = if (isDirectAccess) {
            val pemEncodedCert = resources.getStringResource("owf_identity_credential_reader_cert.pem")!!
            val trustedReaderCert = X509Cert.fromPem(pemEncodedCert)
            val readerAuth = CborArray.builder()
                .add((trustedReaderCert.ecPublicKey as EcPublicKeyDoubleCoordinate).asUncompressedPointEncoding)
                .end().build()

            CborMap.builder().apply {
                for ((namespace, bytesList) in issuerNameSpaces) {
                    putArray(namespace).let { innerBuilder ->
                        bytesList.forEach { encodedIssuerSignedItemMetadata ->
                            innerBuilder.add(RawCbor(encodedIssuerSignedItemMetadata))
                        }
                    }
                }
            }.end().build().let { digestIdMappingItem ->
                Cbor.encode(
                    CborMap.builder()
                        .put("docType", docType)
                        .put("issuerNameSpaces", digestIdMappingItem)
                        .put("issuerAuth", RawCbor(encodedIssuerAuth))
                        .put("readerAccess", readerAuth)
                        .end()
                        .build()
                )
            }
        } else {
            StaticAuthDataGenerator(
                issuerNameSpaces,
                encodedIssuerAuth
            ).generate()
        }

        return issuerProvidedAuthenticationData
    }

    private fun createPresentationDataSdJwt(
        env: FlowEnvironment,
        documentConfiguration: DocumentConfiguration,
        authenticationKey: EcPublicKey
    ): ByteArray {
        // For now, just use the mdoc data element names and pretty print its value
        //
        val identityAttributes = buildJsonObject {
            for (nsName in documentConfiguration.mdocConfiguration!!.staticData.nameSpaceNames) {
                for (deName in documentConfiguration.mdocConfiguration!!.staticData.getDataElementNames(nsName)) {
                    val value = Cbor.decode(
                        documentConfiguration.mdocConfiguration!!.staticData.getDataElement(nsName, deName)
                    )
                    // TODO: This will need support for bstr once we support that in a DocumentType.
                    when (value) {
                        is Tstr -> put(deName, value.asTstr)
                        is CborInt -> put(deName, value.asNumber)
                        is Simple -> {
                            if (value == Simple.TRUE) {
                                put(deName, true)
                            } else if (value == Simple.FALSE){
                                put(deName, false)
                            } else {
                                put(deName, value.toString())
                            }
                        }
                        else -> {
                            put(
                                deName,
                                Cbor.toDiagnostics(
                                    value,
                                    setOf(
                                        DiagnosticOption.PRETTY_PRINT,
                                        DiagnosticOption.BSTR_PRINT_LENGTH,
                                        DiagnosticOption.EMBEDDED_CBOR
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }

        val sdJwtVcGenerator = SdJwtVcGenerator(
            random = Random.Default,
            payload = identityAttributes,
            docType = EUPersonalID.EUPID_VCT,
            issuer = Issuer("https://example-issuer.com", Algorithm.ES256, "key-1")
        )

        val now = Clock.System.now()

        val timeSigned = now
        val validFrom = now
        val validUntil = validFrom + 30.days

        sdJwtVcGenerator.publicKey = JsonWebKey(authenticationKey)
        sdJwtVcGenerator.timeSigned = timeSigned
        sdJwtVcGenerator.timeValidityBegin = validFrom
        sdJwtVcGenerator.timeValidityEnd = validUntil

        // Just use the mdoc Document Signing key for now
        //
        val resources = env.getInterface(Resources::class)!!
        val documentSigningKeyCert = X509Cert.fromPem(
            resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey
        )
        val sdJwt = sdJwtVcGenerator.generateSdJwt(documentSigningKey)

        return sdJwt.toString().toByteArray()
    }

    private suspend fun generateDocumentConfiguration(
        env: FlowEnvironment,
        collectedEvidence: Map<String, EvidenceResponse>
    ): DocumentConfiguration {
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val prefix = "issuingAuthority.$authorityId"
        val type = settings.getString("$prefix.type") ?: TYPE_DRIVING_LICENSE
        return when (type) {
            TYPE_DRIVING_LICENSE -> generateMdlDocumentConfiguration(env, collectedEvidence)
            TYPE_EU_PID -> generateEuPidDocumentConfiguration(env, collectedEvidence)
            TYPE_PHOTO_ID -> generatePhotoIdDocumentConfiguration(env, collectedEvidence)
            TYPE_PAYMENT_AUTH -> generatePaymentAuthDocumentConfiguration(env, collectedEvidence)
            else -> throw IllegalArgumentException("Unknown type $type")
        }
    }

    private suspend fun generateEuPidDocumentConfiguration(
        env: FlowEnvironment,
        collectedEvidence: Map<String, EvidenceResponse>
    ): DocumentConfiguration {
        val now = Clock.System.now()
        val issueDate = now
        val resources = env.getInterface(Resources::class)!!
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val expiryDate = now + 365.days * 5

        val prefix = "issuingAuthority.$authorityId"
        val artPath = settings.getString("${prefix}.cardArt") ?: "default/card_art.png"
        val issuingAuthorityName = settings.getString("${prefix}.name") ?: "Default Issuer"
        val art = resources.getRawResource(artPath)!!

        val credType = documentTypeRepository.getDocumentTypeForMdoc(EUPID_DOCTYPE)!!
        val staticData: NameSpacedData
        val staticDataCompressed: NameSpacedData

        val path = (collectedEvidence["path"] as EvidenceResponseQuestionMultipleChoice).answerId
        if (path == "hardcoded") {
            val imageFormat = collectedEvidence["devmode_image_format"]
            val jpeg2k = imageFormat is EvidenceResponseQuestionMultipleChoice &&
                    imageFormat.answerId == "devmode_image_format_jpeg2000"
            staticData = fillInSampleData(resources, jpeg2k, credType).build()
            staticDataCompressed = staticData
        } else {
            val icaoPassiveData = collectedEvidence["passive"]
            val icaoTunnelData = collectedEvidence["tunnel"]
            val mrtdData = if (icaoTunnelData is EvidenceResponseIcaoNfcTunnelResult)
                MrtdNfcData(icaoTunnelData.dataGroups, icaoTunnelData.securityObject)
            else if (icaoPassiveData is EvidenceResponseIcaoPassiveAuthentication)
                MrtdNfcData(icaoPassiveData.dataGroups, icaoPassiveData.securityObject)
            else
                throw IllegalStateException("Should not happen")

            val decoded = MrtdNfcDataDecoder().decode(mrtdData)
            val firstName = decoded.firstName
            val lastName = decoded.lastName
            val sex = when (decoded.gender) {
                "MALE" -> 1L
                "FEMALE" -> 2L
                else -> 0L
            }

            val timeZone = TimeZone.currentSystemDefault()
            val dateOfBirth = LocalDate.parse(
                input = decoded.dateOfBirth,
                format = LocalDate.Format {
                    yearTwoDigits(now.toLocalDateTime(timeZone).year - 99)
                    monthNumber()
                    dayOfMonth()
                }
            )
            val dateOfBirthInstant = dateOfBirth.atStartOfDayIn(timeZone)

            val ageOver18 = now > dateOfBirthInstant.plus(18, DateTimeUnit.YEAR, timeZone)
            val ageOver21 = now > dateOfBirthInstant.plus(21, DateTimeUnit.YEAR, timeZone)

            val portrait = decoded.photo ?: resources.getRawResource("img_erika_portrait.jpg")!!
            val portraitCompressed = decoded.photo ?: resources.getRawResource("img_erika_portrait_compressed.jpg")!!
            val signatureOrUsualMark = decoded.signature ?: resources.getRawResource("img_erika_signature.jpg")!!

            val nsBuilder = NameSpacedData.Builder()
                .putEntryString(EUPID_NAMESPACE, "family_name", lastName)
                .putEntryString(EUPID_NAMESPACE, "given_name", firstName)
                .putEntry(
                    EUPID_NAMESPACE,
                    "birth_date",
                    Cbor.encode(dateOfBirth.toDataItemFullDate())
                )
                .putEntryString(EUPID_NAMESPACE, "birth_place", "The Netherlands, Leiden")
                .putEntryString(EUPID_NAMESPACE, "nationality", "NL")
                .putEntryString(EUPID_NAMESPACE, "resident_address", "De Heyderweg 2, Leiden")
                .putEntryString(EUPID_NAMESPACE, "resident_country", "NL")
                .putEntryString(EUPID_NAMESPACE, "resident_state", "Zuid-Holland")
                .putEntryString(EUPID_NAMESPACE, "resident_city", "Leiden")
                .putEntryString(EUPID_NAMESPACE, "resident_postal_code", "2314 XZ")
                .putEntryString(EUPID_NAMESPACE, "resident_street", "De Heyderweg")
                .putEntryString(EUPID_NAMESPACE, "resident_house_number", "2")
                .putEntryString(EUPID_NAMESPACE, "personal_administrative_number", "9876543210")
                .putEntry(
                    EUPID_NAMESPACE,
                    "portrait_capture_date",
                    Cbor.encode("2020-03-14".toDataItemDateTimeString())
                )
                .putEntryString(EUPID_NAMESPACE, "family_name_birth", "Mustermann")
                .putEntryString(EUPID_NAMESPACE, "given_name_birth", "Erika")
                .putEntryNumber(EUPID_NAMESPACE, "sex", sex)
                .putEntryString(EUPID_NAMESPACE, "email_address", "erika@mustermann.nl")
                .putEntryString(EUPID_NAMESPACE, "mobile_phone_number", "+31717993005")
                .putEntry(
                    EUPID_NAMESPACE,
                    "expiry_date",
                    Cbor.encode(expiryDate.toDataItemDateTimeString())
                )
                .putEntryString(EUPID_NAMESPACE, "issuing_authority", issuingAuthorityName)
                .putEntryString(EUPID_NAMESPACE, "issuing_country", "ZZ")
                .putEntryString(EUPID_NAMESPACE, "document_number", "0123456789")
                .putEntry(
                    EUPID_NAMESPACE,
                    "issuance_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString())
                )
                .putEntryBoolean(EUPID_NAMESPACE, "age_over_18", ageOver18)
                .putEntryNumber(
                    EUPID_NAMESPACE,
                    "age_in_years",
                    dateOfBirth.yearsUntil(now.toLocalDateTime(timeZone).date).toLong()
                )
                .putEntryNumber(EUPID_NAMESPACE, "age_birth_year", dateOfBirth.year.toLong())

            staticData = nsBuilder
                .putEntryByteString(EUPID_NAMESPACE, "portrait", portrait.toByteArray()) // ✅ Corrected to match mDL
                .build()

            staticDataCompressed = nsBuilder
                .putEntryByteString(EUPID_NAMESPACE, "portrait", portraitCompressed.toByteArray()) // ✅ Matches mDL approach
                .build()
        }

        val firstName = staticData.getDataElementString(EUPID_NAMESPACE, "given_name")
        return DocumentConfiguration(
            displayName = "$firstName's Personal ID",
            typeDisplayName = "EU Personal ID",
            cardArt = art.toByteArray(),
            requireUserAuthenticationToViewDocument =
            settings.getBool("${prefix}.requireUserAuthenticationToViewDocument"),
            mdocConfiguration = MdocDocumentConfiguration(
                docType = EUPID_DOCTYPE,
                staticData = staticData,
            ),
            sdJwtVcDocumentConfiguration = SdJwtVcDocumentConfiguration(
                vct = EUPersonalID.EUPID_VCT,
                keyBound = true
            ),
            directAccessConfiguration = null
        )
    }

    private suspend fun generateMdlDocumentConfiguration(
        env: FlowEnvironment,
        collectedEvidence: Map<String, EvidenceResponse>
    ): DocumentConfiguration {
        val now = Clock.System.now()
        val issueDate = now
        val resources = env.getInterface(Resources::class)!!
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val expiryDate = now + 365.days * 5

        val prefix = "issuingAuthority.$authorityId"
        val artPath = settings.getString("${prefix}.cardArt") ?: "default/card_art.png"
        val issuingAuthorityName = settings.getString("${prefix}.name") ?: "Default Issuer"
        val art = resources.getRawResource(artPath)!!

        val credType = documentTypeRepository.getDocumentTypeForMdoc(MDL_DOCTYPE)!!
        val staticData: NameSpacedData
        val staticDataCompressed: NameSpacedData

        val path = (collectedEvidence["path"] as EvidenceResponseQuestionMultipleChoice).answerId
        if (path == "hardcoded") {
            val imageFormat = collectedEvidence["devmode_image_format"]
            val jpeg2k = imageFormat is EvidenceResponseQuestionMultipleChoice &&
                    imageFormat.answerId == "devmode_image_format_jpeg2000"
            staticData = fillInSampleData(resources, jpeg2k, credType).build()
            staticDataCompressed = staticData
        } else if (path == "germanEid") {
            // Make sure we set at least all the mandatory data elements
            val germanEid = collectedEvidence["germanEidCard"] as EvidenceResponseGermanEidResolved
            val personalData = getPersonalData(env, germanEid)
            val firstName = personalData["GivenNames"]!!.jsonPrimitive.content
            val lastName = personalData["FamilyNames"]!!.jsonPrimitive.content
            val dateOfBirth = parseDateOfBirth(personalData["DateOfBirth"]!!.jsonPrimitive.content)
            val timeZone = TimeZone.currentSystemDefault()
            val dateOfBirthInstant = dateOfBirth.atStartOfDayIn(timeZone)
            // over 18/21 is calculated purely based on calendar date (not based on the birth time zone)
            val ageOver18 = now > dateOfBirthInstant.plus(18, DateTimeUnit.YEAR, timeZone)
            val ageOver21 = now > dateOfBirthInstant.plus(21, DateTimeUnit.YEAR, timeZone)
            val portrait = resources.getRawResource("img_erika_portrait.jpg")!!
            val portraitCompressed = resources.getRawResource("img_erika_portrait_compressed.jpg")!!
            val signatureOrUsualMark = resources.getRawResource("img_erika_signature.jpg")!!

            // Make sure we set at least all the mandatory data elements
            //
            val nsBuilder = NameSpacedData.Builder()
                .putEntryString(MDL_NAMESPACE, "given_name", firstName)
                .putEntryString(MDL_NAMESPACE, "family_name", lastName)
                .putEntry(MDL_NAMESPACE, "birth_date",
                    Cbor.encode(dateOfBirth.toDataItemFullDate()))
                .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark",
                    signatureOrUsualMark.toByteArray())
                .putEntry(MDL_NAMESPACE, "issue_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString()))
                .putEntry(MDL_NAMESPACE, "expiry_date",
                    Cbor.encode(expiryDate.toDataItemDateTimeString())
                )
                // TODO
                .putEntryString(MDL_NAMESPACE, "issuing_authority",
                    issuingAuthorityName)
                .putEntryString(MDL_NAMESPACE, "issuing_country", "ZZ")
                .putEntryString(MDL_NAMESPACE, "un_distinguishing_sign", "UTO")
                .putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
                .putEntryString(MDL_NAMESPACE, "administrative_number", "123456789")
                .putEntry(MDL_NAMESPACE, "driving_privileges",
                    Cbor.encode(CborArray.builder().end().build()))

                .putEntryBoolean(MDL_NAMESPACE, "age_over_18", ageOver18)
                .putEntryBoolean(MDL_NAMESPACE, "age_over_21", ageOver21)

                .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
                .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)

            staticData = nsBuilder
                .putEntryByteString(MDL_NAMESPACE, "portrait", portrait.toByteArray())
                .build()
            staticDataCompressed = nsBuilder
                .putEntryByteString(MDL_NAMESPACE, "portrait", portraitCompressed.toByteArray())
                .build()
        } else {
            val icaoPassiveData = collectedEvidence["passive"]
            val icaoTunnelData = collectedEvidence["tunnel"]
            val mrtdData = if (icaoTunnelData is EvidenceResponseIcaoNfcTunnelResult)
                MrtdNfcData(icaoTunnelData.dataGroups, icaoTunnelData.securityObject)
            else if (icaoPassiveData is EvidenceResponseIcaoPassiveAuthentication)
                MrtdNfcData(icaoPassiveData.dataGroups, icaoPassiveData.securityObject)
            else
                throw IllegalStateException("Should not happen")
            val decoded = MrtdNfcDataDecoder().decode(mrtdData)
            val firstName = decoded.firstName
            val lastName = decoded.lastName
            val sex = when (decoded.gender) {
                "MALE" -> 1L
                "FEMALE" -> 2L
                else -> 0L
            }
            val timeZone = TimeZone.currentSystemDefault()
            val dateOfBirth = LocalDate.parse(input = decoded.dateOfBirth,
                format = LocalDate.Format {
                    // date of birth cannot be in future
                    yearTwoDigits(now.toLocalDateTime(timeZone).year - 99)
                    monthNumber()
                    dayOfMonth()
                })
            val dateOfBirthInstant = dateOfBirth.atStartOfDayIn(timeZone)
            // over 18/21 is calculated purely based on calendar date (not based on the birth time zone)
            val ageOver18 = now > dateOfBirthInstant.plus(18, DateTimeUnit.YEAR, timeZone)
            val ageOver21 = now > dateOfBirthInstant.plus(21, DateTimeUnit.YEAR, timeZone)
            val portrait = decoded.photo ?: resources.getRawResource("img_erika_portrait.jpg")!!
            val portraitCompressed = decoded.photo ?: resources.getRawResource("img_erika_portrait_compressed.jpg")!!
            val signatureOrUsualMark = decoded.signature ?: resources.getRawResource("img_erika_signature.jpg")!!

            // Make sure we set at least all the mandatory data elements
            //
            val nsBuilder = NameSpacedData.Builder()
                .putEntryString(MDL_NAMESPACE, "given_name", firstName)
                .putEntryString(MDL_NAMESPACE, "family_name", lastName)
                .putEntry(MDL_NAMESPACE, "birth_date",
                    Cbor.encode(dateOfBirth.toDataItemFullDate()))
                .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark",
                    signatureOrUsualMark.toByteArray())
                .putEntryNumber(MDL_NAMESPACE, "sex", sex)
                .putEntry(MDL_NAMESPACE, "issue_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString()))
                .putEntry(MDL_NAMESPACE, "expiry_date",
                    Cbor.encode(expiryDate.toDataItemDateTimeString())
                )
                // TODO
                .putEntryString(MDL_NAMESPACE, "issuing_authority",
                    issuingAuthorityName)
                .putEntryString(MDL_NAMESPACE, "issuing_country", "ZZ")
                .putEntryString(MDL_NAMESPACE, "un_distinguishing_sign", "UTO")
                .putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
                .putEntryString(MDL_NAMESPACE, "administrative_number", "123456789")
                .putEntry(MDL_NAMESPACE, "driving_privileges",
                    Cbor.encode(CborArray.builder().end().build()))

                .putEntryBoolean(MDL_NAMESPACE, "age_over_18", ageOver18)
                .putEntryBoolean(MDL_NAMESPACE, "age_over_21", ageOver21)

                .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
                .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)
                .putEntryNumber(AAMVA_NAMESPACE, "sex", sex)

            staticData = nsBuilder
                .putEntryByteString(MDL_NAMESPACE, "portrait", portrait.toByteArray())
                .build()
            staticDataCompressed = nsBuilder
                .putEntryByteString(MDL_NAMESPACE, "portrait", portraitCompressed.toByteArray())
                .build()
        }

        var directAccessConfiguration: DirectAccessDocumentConfiguration? = null
        if (collectedEvidence["directAccess"] != null) {
            val includeDirectAccess = (collectedEvidence["directAccess"] as EvidenceResponseQuestionMultipleChoice).answerId
            directAccessConfiguration = if (includeDirectAccess == "yes") {
                DirectAccessDocumentConfiguration(
                    docType = MDL_DOCTYPE,
                    staticData = staticDataCompressed,
                )
            } else null
        }


        val firstName = staticData.getDataElementString(MDL_NAMESPACE, "given_name")
        return DocumentConfiguration(
            displayName = "$firstName's Driving License",
            typeDisplayName = "Driving License",
            cardArt = art.toByteArray(),
            requireUserAuthenticationToViewDocument =
                settings.getBool("${prefix}.requireUserAuthenticationToViewDocument"),
            mdocConfiguration = MdocDocumentConfiguration(
                docType = MDL_DOCTYPE,
                staticData = staticData,
            ),
            sdJwtVcDocumentConfiguration = null,
            directAccessConfiguration = directAccessConfiguration
        )
    }

    private suspend fun generatePhotoIdDocumentConfiguration(
        env: FlowEnvironment,
        collectedEvidence: Map<String, EvidenceResponse>
    ): DocumentConfiguration {
        val now = Clock.System.now()
        val issueDate = now
        val resources = env.getInterface(Resources::class)!!
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val expiryDate = now + 365.days * 5

        val prefix = "issuingAuthority.$authorityId"
        val artPath = settings.getString("${prefix}.cardArt") ?: "default/card_art.png"
        val issuingAuthorityName = settings.getString("${prefix}.name") ?: "Default Issuer"
        val art = resources.getRawResource(artPath)!!

        val credType = documentTypeRepository.getDocumentTypeForMdoc(PHOTO_ID_DOCTYPE)!!
        val staticData: NameSpacedData

        val path = (collectedEvidence["path"] as EvidenceResponseQuestionMultipleChoice).answerId
        if (path == "hardcoded") {
            val imageFormat = collectedEvidence["devmode_image_format"]
            val jpeg2k = imageFormat is EvidenceResponseQuestionMultipleChoice &&
                    imageFormat.answerId == "devmode_image_format_jpeg2000"
            staticData = fillInSampleData(resources, jpeg2k, credType).build()
        } else if (path == "germanEid") {
            // Make sure we set at least all the mandatory data elements
            val germanEid = collectedEvidence["germanEidCard"] as EvidenceResponseGermanEidResolved
            val personalData = getPersonalData(env, germanEid)
            val firstName = personalData["GivenNames"]!!.jsonPrimitive.content
            val lastName = personalData["FamilyNames"]!!.jsonPrimitive.content
            val dateOfBirth = parseDateOfBirth(personalData["DateOfBirth"]!!.jsonPrimitive.content)
            val timeZone = TimeZone.currentSystemDefault()
            val dateOfBirthInstant = dateOfBirth.atStartOfDayIn(timeZone)
            // over 18/21 is calculated purely based on calendar date (not based on the birth time zone)
            val ageOver18 = now > dateOfBirthInstant.plus(18, DateTimeUnit.YEAR, timeZone)
            val ageOver21 = now > dateOfBirthInstant.plus(21, DateTimeUnit.YEAR, timeZone)
            val portrait = resources.getRawResource("img_erika_portrait.jpg")!!
            val signatureOrUsualMark = resources.getRawResource("img_erika_signature.jpg")!!

            // Make sure we set at least all the mandatory data elements
            //
            staticData = NameSpacedData.Builder()
                .putEntryString(PHOTO_ID_NAMESPACE, "given_name_unicode", firstName)
                .putEntryString(PHOTO_ID_NAMESPACE, "family_name_unicode", lastName)
                .putEntry(PHOTO_ID_NAMESPACE, "birthdate",
                    Cbor.encode(dateOfBirth.toDataItemFullDate()))
                .putEntryByteString(PHOTO_ID_NAMESPACE, "portrait", portrait.toByteArray())
                .putEntry(PHOTO_ID_NAMESPACE, "issue_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString()))
                .putEntry(PHOTO_ID_NAMESPACE, "expiry_date",
                    Cbor.encode(expiryDate.toDataItemDateTimeString())
                )
                // TODO
                .putEntryString(PHOTO_ID_NAMESPACE, "issuing_authority_unicode",
                    issuingAuthorityName)
                .putEntryString(PHOTO_ID_NAMESPACE, "issuing_country", "ZZ")
                .putEntryString(PHOTO_ID_NAMESPACE, "document_number", "1234567890")
                .putEntryString(PHOTO_ID_NAMESPACE, "administrative_number", "123456789")
                .putEntryString(PHOTO_ID_NAMESPACE, "person_id", "24601")

                .putEntryBoolean(PHOTO_ID_NAMESPACE, "age_over_18", ageOver18)
                .putEntryBoolean(PHOTO_ID_NAMESPACE, "age_over_21", ageOver21)

                .build()
        } else {
            val icaoPassiveData = collectedEvidence["passive"]
            val icaoTunnelData = collectedEvidence["tunnel"]
            val mrtdData = if (icaoTunnelData is EvidenceResponseIcaoNfcTunnelResult)
                MrtdNfcData(icaoTunnelData.dataGroups, icaoTunnelData.securityObject)
            else if (icaoPassiveData is EvidenceResponseIcaoPassiveAuthentication)
                MrtdNfcData(icaoPassiveData.dataGroups, icaoPassiveData.securityObject)
            else
                throw IllegalStateException("Should not happen")
            val decoded = MrtdNfcDataDecoder().decode(mrtdData)
            val firstName = decoded.firstName
            val lastName = decoded.lastName
            val sex = when (decoded.gender) {
                "MALE" -> 1L
                "FEMALE" -> 2L
                else -> 0L
            }
            val timeZone = TimeZone.currentSystemDefault()
            val dateOfBirth = LocalDate.parse(input = decoded.dateOfBirth,
                format = LocalDate.Format {
                    // date of birth cannot be in future
                    yearTwoDigits(now.toLocalDateTime(timeZone).year - 99)
                    monthNumber()
                    dayOfMonth()
                })
            val dateOfBirthInstant = dateOfBirth.atStartOfDayIn(timeZone)
            // over 18/21 is calculated purely based on calendar date (not based on the birth time zone)
            val ageOver18 = now > dateOfBirthInstant.plus(18, DateTimeUnit.YEAR, timeZone)
            val ageOver21 = now > dateOfBirthInstant.plus(21, DateTimeUnit.YEAR, timeZone)
            val portrait = decoded.photo ?: resources.getRawResource("img_erika_portrait.jpg")!!
            val signatureOrUsualMark =
                decoded.signature ?: resources.getRawResource("img_erika_signature.jpg")!!

            // Make sure we set at least all the mandatory data elements
            //
            staticData = NameSpacedData.Builder().apply {
                putEntryString(ISO_23220_2_NAMESPACE, "given_name_unicode", firstName)
                putEntryString(ISO_23220_2_NAMESPACE, "given_name_latin1", firstName)
                putEntryString(ISO_23220_2_NAMESPACE, "family_name_unicode", lastName)
                putEntryString(ISO_23220_2_NAMESPACE, "family_name_latin1", lastName)
                putEntry(
                    ISO_23220_2_NAMESPACE, "birthdate",
                    Cbor.encode(dateOfBirth.toDataItemFullDate())
                )
                putEntryByteString(ISO_23220_2_NAMESPACE, "portrait", portrait.toByteArray())
                putEntryNumber(ISO_23220_2_NAMESPACE, "sex", sex)
                putEntry(
                    ISO_23220_2_NAMESPACE, "issue_date",
                    Cbor.encode(issueDate.toDataItemDateTimeString())
                )
                putEntry(
                    ISO_23220_2_NAMESPACE, "expiry_date",
                    Cbor.encode(expiryDate.toDataItemDateTimeString())
                )
                putEntryString(
                    ISO_23220_2_NAMESPACE, "issuing_authority_unicode",
                    issuingAuthorityName
                )
                putEntryString(ISO_23220_2_NAMESPACE, "issuing_country", "NL")
                putEntryBoolean(ISO_23220_2_NAMESPACE, "age_over_18", ageOver18)
                putEntryString(ISO_23220_2_NAMESPACE, "age_in_years", "38")
                putEntryString(ISO_23220_2_NAMESPACE, "age_birth_year", "1986")
                putEntryString(ISO_23220_2_NAMESPACE, "portrait_capture_date", "2020-03-14")
                putEntryString(ISO_23220_2_NAMESPACE, "birth_place", "The Netherlands, Leiden")
                putEntryString(ISO_23220_2_NAMESPACE, "name_at_birth", "Erika Mustermann")
                putEntryString(ISO_23220_2_NAMESPACE, "resident_address_unicode", "De Heyderweg 2, Leiden")
                putEntryString(ISO_23220_2_NAMESPACE, "resident_city_unicode", "Leiden")
                putEntryString(ISO_23220_2_NAMESPACE, "resident_postal_code", "2314 XZ")
                putEntryString(ISO_23220_2_NAMESPACE, "resident_country", "NL")
                putEntryString(ISO_23220_2_NAMESPACE, "nationality", "NL")
                putEntryString(ISO_23220_2_NAMESPACE, "document_number", "0123456789")
                putEntryString(PHOTO_ID_NAMESPACE, "person_id", "1234567890")
                putEntryString(PHOTO_ID_NAMESPACE, "birth_country", "NL")
                putEntryString(PHOTO_ID_NAMESPACE, "birth_state", "Zuid-Holland")
                putEntryString(PHOTO_ID_NAMESPACE, "birth_city", "Leiden")
                putEntryString(PHOTO_ID_NAMESPACE, "administrative_number", "9876543210")
                putEntryString(PHOTO_ID_NAMESPACE, "resident_street", "De Heyderweg")
                putEntryString(PHOTO_ID_NAMESPACE, "resident_house_number", "2")
                putEntryString(PHOTO_ID_NAMESPACE, "travel_document_number", "C11T002JM")
                putEntryBoolean(ISO_23220_2_NAMESPACE, "age_over_21", ageOver21)
                for (entry in mrtdData.dataGroups) {
                    putEntryByteString(
                        PhotoID.DTC_NAMESPACE,
                        "dtc_dg${entry.key}",
                        entry.value.toByteArray())
                }
                putEntryByteString(PhotoID.DTC_NAMESPACE, "dtc_sod", mrtdData.sod.toByteArray())
            }.build()
        }

        val firstName = staticData.getDataElementString(ISO_23220_2_NAMESPACE, "given_name_unicode")
        return DocumentConfiguration(
            displayName = "$firstName's Photo ID",
            typeDisplayName = "Photo ID",
            cardArt = art.toByteArray(),
            requireUserAuthenticationToViewDocument =
            settings.getBool("${prefix}.requireUserAuthenticationToViewDocument"),
            mdocConfiguration = MdocDocumentConfiguration(
                docType = PHOTO_ID_DOCTYPE,
                staticData = staticData,
            ),
            sdJwtVcDocumentConfiguration = null,
            directAccessConfiguration = null
        )
    }

    private suspend fun generatePaymentAuthDocumentConfiguration(
        env: FlowEnvironment,
        collectedEvidence: Map<String, EvidenceResponse>
    ): DocumentConfiguration {
        val now = Clock.System.now()
        val issueDate = now
        val resources = env.getInterface(Resources::class)!!
        val settings = WalletServerSettings(env.getInterface(Configuration::class)!!)
        val expiryDate = now + 365.days * 5

        val prefix = "issuingAuthority.$authorityId"
        val artPath = settings.getString("${prefix}.cardArt") ?: "default/card_art.png"
        val issuingAuthorityName = settings.getString("${prefix}.name") ?: "ES Issuer"
        val art = resources.getRawResource(artPath)!!

        val credType = documentTypeRepository.getDocumentTypeForMdoc(PAYMENT_AUTH_DOCTYPE)!!
        val staticData: NameSpacedData

        val path = (collectedEvidence["path"] as EvidenceResponseQuestionMultipleChoice).answerId

        val nsBuilder = NameSpacedData.Builder()
            .putEntryString(PAYMENT_AUTH_NAMESPACE, "payment_auth_number", "4111112014267661")
            .putEntryString(PAYMENT_AUTH_NAMESPACE, "payment_auth_expiry", "2026-09")
            .putEntryString(PAYMENT_AUTH_NAMESPACE, "payment_scheme", "V")
            .putEntryString(PAYMENT_AUTH_NAMESPACE, "payment_type", "C")
 //           .putEntryString(PAYMENT_AUTH_NAMESPACE, "merchant_name", "ShoeXYZ")
 //           .putEntryString(PAYMENT_AUTH_NAMESPACE, "transaction_amount", "00.00")
 //           .putEntryString(PAYMENT_AUTH_NAMESPACE, "transaction_currency_code", "USD")

        staticData = nsBuilder.build()

        return DocumentConfiguration(
            displayName = "My Bank Payment Authentication",
            typeDisplayName = "Payment Authentication",
            cardArt = art.toByteArray(),
            requireUserAuthenticationToViewDocument =
            settings.getBool("${prefix}.requireUserAuthenticationToViewDocument"),
            mdocConfiguration = MdocDocumentConfiguration(
                docType = PAYMENT_AUTH_DOCTYPE,
                staticData = staticData,
            ),
            sdJwtVcDocumentConfiguration = null,
            directAccessConfiguration = null
        )
    }

    private suspend fun getPersonalData(
        env: FlowEnvironment,
        germanEid: EvidenceResponseGermanEidResolved
    ): JsonObject {
        val germanEidData = germanEid.data
        if (germanEidData == null) {
            Logger.e(TAG, "No data in eId response")
            throw IllegalStateException("No personal data")
        }
        val elem = Json.parseToJsonElement(germanEidData)
        val personalData = elem.jsonObject["PersonalData"]?.jsonObject
        if (personalData == null) {
            Logger.e(TAG, "Error in German eID response data: ${germanEid.data}")
            throw IllegalStateException("No personal data")
        }
        return personalData
    }

    private fun fillInSampleData(
        resources: Resources,
        jpeg2k: Boolean,
        documentType: DocumentType
    ): NameSpacedData.Builder {
        val builder = NameSpacedData.Builder()
        for ((namespaceName, namespace) in documentType.mdocDocumentType!!.namespaces) {
            for ((dataElementName, dataElement) in namespace.dataElements) {
                if (dataElement.attribute.sampleValue != null) {
                    builder.putEntry(
                        namespaceName,
                        dataElementName,
                        Cbor.encode(dataElement.attribute.sampleValue!!)
                    )
                }
            }
        }

        if (documentType == documentTypeRepository.getDocumentTypeForMdoc(MDL_DOCTYPE)!!) {
            val portrait = resources.getRawResource(if (jpeg2k) {
                "img_erika_portrait_compressed.jpf"
            } else {
                "img_erika_portrait_compressed.jpg"
            })!!.toByteArray()
            val signatureOrUsualMark = resources.getRawResource(if (jpeg2k) {
                "img_erika_signature.jpf"
            } else {
                "img_erika_signature.jpg"
            })!!.toByteArray()
            builder
                .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
                .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark", signatureOrUsualMark)
        } else if (documentType == documentTypeRepository.getDocumentTypeForMdoc(PHOTO_ID_DOCTYPE)!!) {
            val portrait = resources.getRawResource(if (jpeg2k) {
                "img_erika_portrait.jpf"
            } else {
                "img_erika_portrait.jpg"
            })!!.toByteArray()
            builder
                .putEntryByteString(ISO_23220_2_NAMESPACE, "portrait", portrait)
        }

        return builder
    }

    private fun checkEvidence(evidence: Map<String, EvidenceResponse>): Boolean {
        return true
    }

    private fun hasCpoRequestForAuthenticationKey(
        issuerDocument: IssuerDocument,
        authenticationKey: EcPublicKey
    ) : Boolean {
        for (cpoRequest in issuerDocument.simpleCredentialRequests) {
            if (cpoRequest.authenticationKey.equals(authenticationKey)) {
                return true
            }
        }
        return false
    }

    private suspend fun issuerDocumentExists(env: FlowEnvironment, documentId: String): Boolean {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getTable(documentTableSpec)
        val encodedCbor = storage.get(partitionId = clientId, key = documentId)
        return encodedCbor != null
    }

    private suspend fun loadIssuerDocument(env: FlowEnvironment, documentId: String): IssuerDocument {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getTable(documentTableSpec)
        val encodedCbor = storage.get(partitionId = clientId, key = documentId)
        if (encodedCbor == null) {
            // TODO: replace with (new) UnknownDocumentException
            throw Error("No such document")
        }
        return IssuerDocument.fromDataItem(Cbor.decode(encodedCbor.toByteArray()))
    }

    private suspend fun createIssuerDocument(env: FlowEnvironment, document: IssuerDocument): String {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getTable(documentTableSpec)
        val bytes = Cbor.encode(document.toDataItem())
        return storage.insert(partitionId = clientId, key = null, data = ByteString(bytes))
    }

    private suspend fun deleteIssuerDocument(env: FlowEnvironment,
                                             documentId: String,
                                             emitNotification: Boolean = true) {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getTable(documentTableSpec)
        storage.delete(partitionId = clientId, key = documentId)
        if (emitNotification) {
            emit(env, IssuingAuthorityNotification(documentId))
        }
    }

    private suspend fun updateIssuerDocument(
        env: FlowEnvironment,
        documentId: String,
        document: IssuerDocument,
        emitNotification: Boolean = true,
    ) {
        if (clientId.isEmpty()) {
            throw IllegalStateException("Client not authenticated")
        }
        val storage = env.getTable(documentTableSpec)
        val bytes = Cbor.encode(document.toDataItem())
        storage.update(partitionId = clientId, key = documentId, data = ByteString(bytes))
        if (emitNotification) {
            emit(env, IssuingAuthorityNotification(documentId))
        }
    }

    private fun parseDateOfBirth(date: String): LocalDate {
        return LocalDate.parse(date.substring(0, 10), format = LocalDate.Format {
            year()
            chars("-")
            monthNumber()
            chars("-")
            dayOfMonth()
        })
    }
}

package jp.datasign.bunsin_wallet.oid

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun decodeUriAsJson(uri: String): Map<String, Any> {
    if (uri.isBlank()) {
        throw IllegalArgumentException(jp.datasign.bunsin_wallet.oid.SIOPErrors.BAD_PARAMS.message)
    }

    val queryParams = parseQueryParams(uri)

    val json = mutableMapOf<String, Any>()
    val mapper = jacksonObjectMapper()

    for (param in queryParams) {
        val key = param.key
        val value = param.value

        when {
            value.toBooleanStrictOrNull() != null -> json[key] = value.toBoolean()
            value.toIntOrNull() != null -> json[key] = value.toInt()
            value.startsWith("{") && value.endsWith("}") -> json[key] = mapper.readValue(value)
            else -> json[key] = value
        }
    }

    return json
}

fun parseQueryParams(url: String): Map<String, String> {
    return url.substringAfter("?", "")
        .split("&")
        .mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) {
                val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8)
                val value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                key to value
            } else {
                null
            }
        }
        .toMap()
}

fun parse(uri: String): Pair<String, jp.datasign.bunsin_wallet.oid.AuthorizationRequestPayload> {
    if (uri.isBlank()) {
        throw IllegalArgumentException(jp.datasign.bunsin_wallet.oid.SIOPErrors.BAD_PARAMS.message)
    }

    val scheme = Regex("^([a-zA-Z][a-zA-Z0-9-_]*)://").find(uri)?.groupValues?.get(1)
        ?: throw IllegalArgumentException(jp.datasign.bunsin_wallet.oid.SIOPErrors.BAD_PARAMS.message)

//        ?: throw IllegalArgumentException(SIOPErrors.BAD_PARAMS.message)

    val authorizationRequestPayloadMap = jp.datasign.bunsin_wallet.oid.decodeUriAsJson(uri)
    val authorizationRequestPayload =
        jp.datasign.bunsin_wallet.oid.createAuthorizationRequestPayloadFromMap(
            authorizationRequestPayloadMap
        )

    return Pair(scheme, authorizationRequestPayload)
}

class RequestObjectHandler(private val authorizationRequestPayload: jp.datasign.bunsin_wallet.oid.AuthorizationRequestPayload) {
    lateinit var requestObjectJwt: String
    private lateinit var decodedJwt: DecodedJWT

    val isSigned: Boolean
        get() = !((authorizationRequestPayload.request
            ?: authorizationRequestPayload.requestUri).isNullOrBlank())

    private suspend fun getRequestObjectJwt(): String {
        if (::requestObjectJwt.isInitialized) {
            println("request jwt: $requestObjectJwt")
            return requestObjectJwt
        }
        requestObjectJwt = withContext(Dispatchers.IO) {
            jp.datasign.bunsin_wallet.oid.fetchByReferenceOrUseByValue(
                referenceURI = authorizationRequestPayload.requestUri,
                valueObject = authorizationRequestPayload.request,
                responseType = String::class.java,
                textResponse = true
            )
        }
        println("request jwt: $requestObjectJwt")
        return requestObjectJwt
    }

    suspend fun getClaim(claimName: String): Claim? {
        if (!::decodedJwt.isInitialized) {
            decodedJwt = JWT.decode(getRequestObjectJwt())
        }
        val claim = decodedJwt.getClaim(claimName)
        return claim
    }
}

class ClientMetadataHandler(
    private val requestObjectHandler: jp.datasign.bunsin_wallet.oid.RequestObjectHandler,
    private val authorizationRequestPayload: jp.datasign.bunsin_wallet.oid.AuthorizationRequestPayload
) {
    suspend fun get(): jp.datasign.bunsin_wallet.oid.RPRegistrationMetadataPayload {
        println("get client metadata")
        val clientMetadata = if (requestObjectHandler.isSigned) {
            val clientMetadataValue = requestObjectHandler.getClaim("client_metadata")
            val mapper: ObjectMapper = jacksonObjectMapper().apply {
                propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
                configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true)
            }
            if (clientMetadataValue != null && !clientMetadataValue.isNull && !clientMetadataValue.isMissing) mapper.readValue<jp.datasign.bunsin_wallet.oid.RPRegistrationMetadataPayload>(
                clientMetadataValue.toString()
            ) else null
        } else {
            authorizationRequestPayload.clientMetadata
        }
        println("client metadata: $clientMetadata")

        val clientMetadataUri = if (requestObjectHandler.isSigned) {
            requestObjectHandler.getClaim("client_metadata_uri")?.asString()
        } else {
            authorizationRequestPayload.clientMetadataUri
        }
        println("client metadata uri: $clientMetadataUri")
        val registrationMetadata = if (clientMetadataUri == null && clientMetadata == null) {
            jp.datasign.bunsin_wallet.oid.RPRegistrationMetadataPayload()
        } else {
            jp.datasign.bunsin_wallet.oid.fetchByReferenceOrUseByValue(
                referenceURI = clientMetadataUri,
                valueObject = clientMetadata,
                responseType = jp.datasign.bunsin_wallet.oid.RPRegistrationMetadataPayload::class.java
            )
        }
        return registrationMetadata
    }
}

class PresentationDefinitionHandler(
    private val requestObjectHandler: jp.datasign.bunsin_wallet.oid.RequestObjectHandler,
    private val authorizationRequestPayload: jp.datasign.bunsin_wallet.oid.AuthorizationRequestPayload
) {
    suspend fun get(): jp.datasign.bunsin_wallet.oid.PresentationDefinition? {
        println("get presentation definition")
        val presentationDefinitionValue = if (requestObjectHandler.isSigned) {
            val claim = requestObjectHandler.getClaim("presentation_definition")
            val presentationDefinitionValue =
                if (claim != null && !claim.isMissing && !claim.isNull) jp.datasign.bunsin_wallet.oid.deserializePresentationDefinition(
                    claim.toString()
                ) else null
            presentationDefinitionValue
        } else {
            authorizationRequestPayload.presentationDefinition
        }
        val presentationDefinitionUri = if (requestObjectHandler.isSigned) {
            val claim = requestObjectHandler.getClaim("presentation_definition_uri")
            if (claim != null && !claim.isMissing && !claim.isNull) claim.asString() else null
        } else {
            authorizationRequestPayload.presentationDefinitionUri
        }
        val presentationDefinition: jp.datasign.bunsin_wallet.oid.PresentationDefinition? =
            if (presentationDefinitionValue != null || presentationDefinitionUri != null) {
                withContext(Dispatchers.IO) {
                    jp.datasign.bunsin_wallet.oid.fetchByReferenceOrUseByValue(
                        referenceURI = presentationDefinitionUri,
                        valueObject = presentationDefinitionValue,
                        responseType = jp.datasign.bunsin_wallet.oid.PresentationDefinition::class.java
                    )
                }
            } else {
                null
            }
        return presentationDefinition
    }
}

suspend fun parseAndResolve(uri: String): jp.datasign.bunsin_wallet.oid.ParseAndResolveResult {
    println("parseAndResolve: $uri")
    if (uri.isBlank()) {
        throw IllegalArgumentException(jp.datasign.bunsin_wallet.oid.SIOPErrors.BAD_PARAMS.message)
    }

    println("parse")
    val (scheme, authorizationRequestPayload) = jp.datasign.bunsin_wallet.oid.parse(uri)

    // request object
    val requestObjectHandler =
        jp.datasign.bunsin_wallet.oid.RequestObjectHandler(authorizationRequestPayload)

    // client metadata
    val clientMetadataHandler =
        jp.datasign.bunsin_wallet.oid.ClientMetadataHandler(
            requestObjectHandler,
            authorizationRequestPayload
        )
    val registrationMetadata = clientMetadataHandler.get()

    // presentation definition
    val presentationDefinitionHandler =
        jp.datasign.bunsin_wallet.oid.PresentationDefinitionHandler(
            requestObjectHandler,
            authorizationRequestPayload
        )
    val presentationDefinition = presentationDefinitionHandler.get()

    return jp.datasign.bunsin_wallet.oid.ParseAndResolveResult(
        scheme,
        authorizationRequestPayload,
        if (requestObjectHandler.isSigned) requestObjectHandler.requestObjectJwt else "",
        registrationMetadata,
        presentationDefinition,
        requestObjectHandler.isSigned
    )
}

data class ParseAndResolveResult(
    val scheme: String,
    val authorizationRequestPayload: jp.datasign.bunsin_wallet.oid.AuthorizationRequestPayload,
    val requestObjectJwt: String,
    val registrationMetadata: jp.datasign.bunsin_wallet.oid.RPRegistrationMetadataPayload,
    val presentationDefinition: jp.datasign.bunsin_wallet.oid.PresentationDefinition?,
    val requestIsSigned: Boolean
)

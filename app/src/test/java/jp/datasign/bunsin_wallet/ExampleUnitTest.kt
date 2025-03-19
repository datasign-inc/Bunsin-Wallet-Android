package jp.datasign.bunsin_wallet

import jp.datasign.bunsin_wallet.vci.CredentialIssuerMetadata
import jp.datasign.bunsin_wallet.vci.CredentialOffer
import jp.datasign.bunsin_wallet.vci.CredentialConfiguration
import jp.datasign.bunsin_wallet.vci.CredentialConfigurationJwtVcJson
import jp.datasign.bunsin_wallet.vci.CredentialConfigurationJwtVcJsonLdAndLdpVc
import jp.datasign.bunsin_wallet.vci.CredentialsSupportedDisplay
import jp.datasign.bunsin_wallet.vci.Grant
import jp.datasign.bunsin_wallet.vci.IssuerCredentialSubjectMap
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jp.datasign.bunsin_wallet.vci.TxCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    private val mapper = jacksonObjectMapper().apply {
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
    }
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun deserialize_json_display() {
        val json = this::class.java.classLoader?.getResource("display.json")?.readText()
            ?: throw IllegalArgumentException("Cannot read test_data.json")

        val display: CredentialsSupportedDisplay =
            mapper.readValue(json, CredentialsSupportedDisplay::class.java)
        assertEquals("University Credential", display.name)
        assertEquals("en-US", display.locale)
        assertEquals("https://exampleuniversity.com/public/logo.png", display.logo!!.uri)
        assertEquals("a square logo of a university", display.logo!!.altText)
        assertEquals("#12107c", display.backgroundColor)
        assertEquals("#FFFFFF", display.textColor)
    }

    @Test
    fun deserialize_json_credential_subject() {
        val json = this::class.java.classLoader?.getResource("credential_subject.json")?.readText()
            ?: throw IllegalArgumentException("Cannot read test_data.json")

        val mapper = jacksonObjectMapper()
        val typeRef = object : TypeReference<IssuerCredentialSubjectMap>() {}
        val subjectMap = mapper.readValue<IssuerCredentialSubjectMap>(json, typeRef)

        val givenNameSubject = subjectMap["given_name"]
        assertEquals(true, givenNameSubject?.mandatory)
        assertEquals("String", givenNameSubject?.valueType)

        val givenNameDisplay = givenNameSubject?.display?.firstOrNull()
        assertEquals("Given Name", givenNameDisplay?.name)
        assertEquals("en-US", givenNameDisplay?.locale)

        val lastNameSubject = subjectMap["last_name"]
        assertEquals(null, lastNameSubject?.mandatory)
        assertEquals(null, lastNameSubject?.valueType)

        val lastNameDisplay = lastNameSubject?.display?.firstOrNull()
        assertEquals("Surname", lastNameDisplay?.name)
        assertEquals("en-US", lastNameDisplay?.locale)
    }

    private fun assertCredentialSupported(data: CredentialConfiguration) {
        when (data) {
            is CredentialConfigurationJwtVcJson -> {
                // `id` を適切なフィールド名に置き換えるか、削除
                assertEquals("did", data.cryptographicBindingMethodsSupported?.firstOrNull())
                assertEquals("ES256K", data.credentialSigningAlgValuesSupported?.firstOrNull())
                // `types` フィールドの扱いを修正
                val givenNameSubject =
                    data.credentialDefinition.credentialSubject?.get("given_name")
                val givenNameDisplay = givenNameSubject?.display?.firstOrNull()
                assertEquals("Given Name", givenNameDisplay?.name)
                assertEquals("en-US", givenNameDisplay?.locale)
            }

            is CredentialConfigurationJwtVcJsonLdAndLdpVc -> {
                // `id` を適切なフィールド名に置き換えるか、削除
                assertEquals("did", data.cryptographicBindingMethodsSupported?.firstOrNull())
                assertEquals(
                    "Ed25519Signature2018",
                    data.credentialSigningAlgValuesSupported?.firstOrNull()
                )
                assertEquals("VerifiableCredential", data.credentialDefinition.type.firstOrNull())
                // `credentialSubject` フィールドの扱いを修正
                val givenNameSubject = data.credentialDefinition.credentialSubject?.get("given_name")
                val givenNameDisplay = givenNameSubject?.display?.firstOrNull()
                assertEquals("Given Name", givenNameDisplay?.name)
                assertEquals("en-US", givenNameDisplay?.locale)
                // 他のフィールドのアサーションも適宜追加または修正
            }

            else -> {
                assertFalse(true)
            }
        }
    }


    @Test
    fun deserialize_json_credential_supported_jwt_vc() {
        val json = this::class.java.classLoader?.getResource("credential_supported_jwt_vc.json")
            ?.readText()
            ?: throw IllegalArgumentException("Cannot read test_data.json")
        val parsed = mapper.readValue(json, CredentialConfiguration::class.java)
        assertCredentialSupported(parsed)
    }

    @Test
    fun deserialize_json_credential_supported_ldp_vc() {
        val json = this::class.java.classLoader?.getResource("credential_supported_ldp_vc.json")
            ?.readText()
            ?: throw IllegalArgumentException("Cannot read test_data.json")
        assertCredentialSupported(mapper.readValue(json, CredentialConfiguration::class.java))
    }

    @Test
    fun deserialize_json_credential_issuer_metadata() {
        val json =
            this::class.java.classLoader?.getResource("credential_issuer_metadata_jwt_vc.json")
                ?.readText()
                ?: throw IllegalArgumentException("Cannot read test_data.json")

        val metaData = mapper.readValue(json, CredentialIssuerMetadata::class.java)

        assertEquals("https://datasign-demo-vci.tunnelto.dev", metaData.credentialIssuer)
        assertEquals(
            "https://datasign-demo-vci.tunnelto.dev",
            metaData.authorizationServers?.get(0)
        )
        assertEquals(
            "https://datasign-demo-vci.tunnelto.dev/credentials",
            metaData.credentialEndpoint
        )
        assertEquals(
            "https://datasign-demo-vci.tunnelto.dev/batch-credentials",
            metaData.batchCredentialEndpoint
        )

        // `credentialSupported` マップから特定のキーを使用して値を取得
        val credentialSupported = metaData.credentialConfigurationsSupported["UniversityDegreeCredential"]
        credentialSupported?.let {
            assertCredentialSupported(it)
        } ?: fail("CredentialSupported not found for key 'UniversityDegreeCredential'")
    }


    @Test
    fun deserialize_json_credential_offer1() {
        val fileName = "credential_offer1.json"
        val json = this::class.java.classLoader?.getResource(fileName)
            ?.readText()
            ?: throw IllegalArgumentException("Cannot read $fileName")
        val credentialOffer = mapper.readValue(json, CredentialOffer::class.java)
        assertEquals("https://datasign-demo-vci.tunnelto.dev", credentialOffer.credentialIssuer)

        val credentials = credentialOffer.credentialConfigurationIds
        assertTrue("Credentials list should have at least one element", credentials.isNotEmpty())

        assertEquals("UniversityDegreeCredential", credentials[0])

        val grants = credentialOffer.grants
        assertEquals("eyJhbGciOiJSU0Et...FYUaBy", grants?.authorizationCode?.issuerState)
        assertEquals("adhjhdjajkdkhjhdj", grants?.urnIetfParams?.preAuthorizedCode)
        assertEquals(TxCode(null, null, null), grants?.urnIetfParams?.txCode)
    }

    @Test
    fun deserialize_json_credential_offer2() {
        val fileName = "credential_offer2.json"
        val json = this::class.java.classLoader?.getResource(fileName)
            ?.readText()
            ?: throw IllegalArgumentException("Cannot read $fileName")
        val credentialOffer = mapper.readValue(json, CredentialOffer::class.java)
        assertEquals("https://datasign-demo-vci.tunnelto.dev", credentialOffer.credentialIssuer)

        val credentials = credentialOffer.credentialConfigurationIds
        assertTrue("Credentials list should have at least one element", credentials.isNotEmpty())

        assertEquals("UniversityDegreeCredential", credentials[0])

        val grants = credentialOffer.grants
        assertEquals("eyJhbGciOiJSU0Et...FYUaBy", grants?.authorizationCode?.issuerState)
        assertEquals("adhjhdjajkdkhjhdj", grants?.urnIetfParams?.preAuthorizedCode)
        assertEquals(null, grants?.urnIetfParams?.txCode)
    }

    @Test
    fun deserialize_json() {
        val fileName = "grant.json"
        val json = this::class.java.classLoader?.getResource(fileName)
            ?.readText()
            ?: throw IllegalArgumentException("Cannot read $fileName")

        val grant: Grant = mapper.readValue(json, Grant::class.java)
        assertEquals("foo", grant.authorizationCode?.issuerState)
        assertEquals("12345", grant.urnIetfParams?.preAuthorizedCode)
        val txCodeDecoded = grant.urnIetfParams?.txCode
        assertEquals(4, txCodeDecoded?.length)
        assertEquals("numeric", txCodeDecoded?.inputMode)
        assertEquals("Please provide the one-time code that was sent via e-mail", txCodeDecoded?.description)
    }
}
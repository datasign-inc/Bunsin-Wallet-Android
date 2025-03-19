package jp.datasign.bunsin_wallet.comment

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentVcIssuerTest {
    private lateinit var commentVcIssuer: CommentVcIssuer

    @Before
    fun setUp() {
        commentVcIssuer = CommentVcIssuer("testKeyAlias")
    }

    @Test
    fun `test issueCredential generates correct CredentialData`() {
        // Arrange
        val url = "https://example.com"
        val comment = "This is a test comment"
        val contentTruth = ContentTruth.TrueContent

        // Act
        val credentialData = commentVcIssuer.issueCredential(url, comment, contentTruth)

        // Assert
        assertNotNull(credentialData)
        assertEquals("jwt_vc_json", credentialData.format)
        assertNotNull(credentialData.credential)
        assertTrue(credentialData.credential.isNotEmpty())
        assertTrue(credentialData.iss.startsWith("urn:ietf:params:oauth:jwk-thumbprint:sha-256:"))
        assertNotNull(credentialData.credentialIssuerMetadata)
    }

    @Test
    fun `test getIssuerPublicJwk returns valid jwk map`() {
        // Act
        val jwk = commentVcIssuer.getIssuerPublicJwk()

        // Assert
        assertNotNull(jwk)
        assertEquals("EC", jwk["kty"])
        assertEquals("P-256", jwk["crv"])
    }

}
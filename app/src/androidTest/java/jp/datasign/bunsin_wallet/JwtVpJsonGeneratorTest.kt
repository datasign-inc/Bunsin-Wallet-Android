package jp.datasign.bunsin_wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.datasign.bunsin_wallet.oid.HeaderOptions
import jp.datasign.bunsin_wallet.oid.JwtVpJsonPayloadOptions
import jp.datasign.bunsin_wallet.ui.shared.JwtVpJsonGeneratorImpl
import jp.datasign.bunsin_wallet.utils.KeyPairUtil
import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JwtVpJsonGeneratorTest {

    private lateinit var jwtVpJsonGenerator: JwtVpJsonGeneratorImpl
    private val keyAlias = "testKeyAlias"

    @Before
    fun setUp() {
        jwtVpJsonGenerator = JwtVpJsonGeneratorImpl(keyAlias)
        if (!KeyPairUtil.isKeyPairExist(keyAlias)) {
            KeyPairUtil.generateSignVerifyKeyPair(keyAlias)
        }
    }

    @Test
    fun testGenerateJwt() {
        val vcJwt = "testVcJwt"
        val headerOptions = HeaderOptions()
        val payloadOptions = JwtVpJsonPayloadOptions(
            iss = "issuer",
            jti = "testJti",
            aud = "testAud",
            nonce = "testNonce"
        )

        val vpToken = jwtVpJsonGenerator.generateJwt(vcJwt, headerOptions, payloadOptions)

        assert(!vpToken.isEmpty())

        val decodedJwt = KeyPairUtil.decodeJwt(vpToken)
        val header = decodedJwt.first

        val jwk = header.get("jwk") as Map<String, String>
        val result = KeyPairUtil.verifyJwt(jwk, vpToken)
        TestCase.assertTrue(result)
    }
}

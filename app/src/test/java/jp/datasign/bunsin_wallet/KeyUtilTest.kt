package jp.datasign.bunsin_wallet

import jp.datasign.bunsin_wallet.utils.KeyUtil
import jp.datasign.bunsin_wallet.utils.SigningOption
import jp.datasign.bunsin_wallet.utils.generateEcKeyPair
import junit.framework.TestCase.assertTrue
import org.junit.Test


class KeyUtilTest {
    @Test
    fun testToJwkThumbprintUri() {
        val keyPair = generateEcKeyPair()
        val jwk = KeyUtil.publicKeyToJwk(keyPair.public, SigningOption())
        val thumbPrintUri = KeyUtil.toJwkThumbprintUri(jwk)
        assertTrue(thumbPrintUri.startsWith("urn:ietf:params:oauth:jwk-thumbprint:sha-256"))

        // reversed member test
        val keyReversedJwk = mapOf(
            "y" to jwk["y"]!!,
            "x" to jwk["x"]!!,
            "kty" to jwk["kty"]!!,
            "crv" to jwk["crv"]!!
        )
        val keyReversedThumbPrintUri = KeyUtil.toJwkThumbprintUri(keyReversedJwk)
        assertTrue(thumbPrintUri == keyReversedThumbPrintUri)
    }
}
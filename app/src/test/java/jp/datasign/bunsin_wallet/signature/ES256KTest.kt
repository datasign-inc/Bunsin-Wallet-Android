package jp.datasign.bunsin_wallet.signature

import jp.datasign.bunsin_wallet.pairwise.HDKeyRing
import jp.datasign.bunsin_wallet.signature.ES256K.createJws
import jp.datasign.bunsin_wallet.signature.ES256K.sign
import jp.datasign.bunsin_wallet.signature.ES256K.verify
import jp.datasign.bunsin_wallet.signature.SignatureUtil.generateECKeyPair
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security

class ES256KTest {
    val mnemonic = "shove zero minor cute budget eye shoot fiber melt pistol pudding scout"
    init {
        Security.addProvider(BouncyCastleProvider())
    }
    @Test
    fun testSignDataWithJwk() {
        var keyRing = HDKeyRing(mnemonic)

        val jwk = keyRing.getPrivateJwk(1)
        val ecPrivateJwk = object : ECPrivateJwk {
            override val kty = jwk.kty
            override val crv = jwk.crv
            override val x = jwk.x
            override val y = jwk.y
            override val d = jwk.d
        }
        val keyPair = generateECKeyPair(ecPrivateJwk)

        val data = "test".toByteArray()
        val signedData = sign(keyPair, data)

        // 署名の検証
        val ret = verify(keyPair, data, signedData)
        assertTrue(ret)
    }
    @Test
    fun testJwsWithJwk() {
        var keyRing = HDKeyRing(mnemonic)

        val jwk = keyRing.getPrivateJwk(1)
        val ecPrivateJwk = object : ECPrivateJwk {
            override val kty = jwk.kty
            override val crv = jwk.crv
            override val x = jwk.x
            override val y = jwk.y
            override val d = jwk.d
        }
        val keyPair = generateECKeyPair(ecPrivateJwk)
        val data = "サンプルメッセージ"
        val jws = createJws(keyPair, data, false)
        assertTrue(jws.isNotEmpty())
    }
}
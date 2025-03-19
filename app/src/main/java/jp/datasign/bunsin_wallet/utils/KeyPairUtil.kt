package jp.datasign.bunsin_wallet.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jp.datasign.bunsin_wallet.Constants
import jp.datasign.bunsin_wallet.signature.JWT
import jp.datasign.bunsin_wallet.signature.toBase64Url
import org.jose4j.jwk.EllipticCurveJsonWebKey
import org.jose4j.jwk.JsonWebKey
import org.jose4j.lang.JoseException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey


object KeyPairUtil {

    private val objectMapper = jacksonObjectMapper()
    private val keyStore: KeyStore = KeyStore.getInstance(Constants.Cryptography.KEYSTORE_TYPE).apply {
        load(null)
    }

    fun generateSignVerifyKeyPair(alias: String): KeyPair {
        // キーペアジェネレータを初期化
        val keyPairGenerator =
            KeyPairGenerator.getInstance(Constants.Cryptography.KEY_ALGORITHM_EC, Constants.Cryptography.KEYSTORE_TYPE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            // 署名と検証の目的で使用
            // https://developer.android.com/reference/kotlin/android/security/keystore/KeyProperties
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            // https://developer.android.com/reference/kotlin/android/security/keystore/KeyGenParameterSpec#example:-nist-p-256-ec-key-pair-for-signingverification-using-ecdsa
            .setAlgorithmParameterSpec(ECGenParameterSpec(Constants.Cryptography.CURVE_SPEC))
            .setDigests(KeyProperties.DIGEST_SHA256).build()

        keyPairGenerator.initialize(keyGenParameterSpec)

        // キーペアを生成
        return keyPairGenerator.generateKeyPair()
    }

    fun isKeyPairExist(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    fun getPrivateKey(alias: String): PrivateKey? {
        return keyStore.getEntry(alias, null)?.let { entry ->
            if (entry is KeyStore.PrivateKeyEntry) {
                try {
                    entry.privateKey
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else null
        }
    }

    fun getPublicKey(alias: String): PublicKey? {
        return keyStore.getCertificate(alias)?.publicKey
    }

    fun getKeyPair(alias: String): KeyPair? {
        val privateKey = getPrivateKey(alias)
        val publicKey = getPublicKey(alias)
        return KeyPair(publicKey, privateKey)
    }

    fun createProofJwt(keyAlias: String, audience: String, nonce: String): String {
        // todo 関数の場所が適切じゃ無いので移動する
        val jwk = publicKeyToJwk(getPublicKey(keyAlias))
        val header = mapOf("typ" to "openid4vci-proof+jwt", "alg" to "ES256", "jwk" to jwk)
        val payload = mapOf(
            "aud" to audience,
            "iat" to (System.currentTimeMillis() / 1000).toInt(),
            "nonce" to nonce
        )

        val unsignedToken = "${
            objectMapper.writeValueAsString(header).toByteArray().toBase64Url()
        }.${objectMapper.writeValueAsString(payload).toByteArray().toBase64Url()}"

        val privateKey = getPrivateKey(keyAlias)
        val signatureBase64url = JWT.signJwt(privateKey!!, unsignedToken).toBase64Url()

        return "$unsignedToken.$signatureBase64url"
    }

    private fun encodeBase64Url(bytes: ByteArray): String {
        return Base64.getUrlEncoder().encodeToString(bytes).replace('+', '-').replace('/', '_')
            .replace("=", "")
    }

    fun decodeJwt(jwt: String): Triple<Map<String, Any>, Map<String, Any>, String> {
        return JWT.decodeJwt(jwt)
    }

    private fun jwkToX509(jwkJson: Map<String, String>): X509EncodedKeySpec {
        try {
            val jwk = JsonWebKey.Factory.newJwk(jwkJson)
            if (jwk is EllipticCurveJsonWebKey) {
                val ecPublicKey = jwk.publicKey as ECPublicKey
                val x509Data = ecPublicKey.encoded
                return X509EncodedKeySpec(x509Data)
            }
        } catch (e: JoseException) {
            e.printStackTrace()
        }
        throw Error()
    }

    private fun createPublicKey(
        jwkJson: Map<String, String>
    ): PublicKey {
        val keyFactory = KeyFactory.getInstance("EC")
        val keySpec = jwkToX509(jwkJson)

        return keyFactory.generatePublic(keySpec)
    }

    // todo move to anywhere else
    fun verifyJwt(jwkJson: Map<String, String>, jwt: String): Boolean {
        val publicKey = createPublicKey(jwkJson)
        val result = JWT.verifyJwt(jwt, publicKey)
        return result.isRight()
    }
}

object KeyStoreHelper {

    private const val KEY_ALIAS = "datastore_encryption_key"
    private val keyStore: KeyStore = KeyStore.getInstance(Constants.Cryptography.KEYSTORE_TYPE).apply {
        load(null)
    }

    fun getSecretKey(): SecretKey {
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            generateSecretKey()
        }
    }

    fun generateSecretKey(): SecretKey {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Constants.Cryptography.KEYSTORE_TYPE)
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7).setKeySize(256).build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }
}

// Common Base64 utilities
private fun ByteArray.toBase64Url() = Base64.getUrlEncoder().encodeToString(this).trimEnd('=')
private fun String.fromBase64Url() = Base64.getUrlDecoder().decode(this)
private fun BigInteger.toBase64() = Base64.getEncoder().encodeToString(this.toByteArray())
fun generateRsaKeyPair(): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    return keyPairGenerator.generateKeyPair()
}

fun generateEcKeyPair(): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    val ecSpec = ECGenParameterSpec("secp256r1")
    keyPairGenerator.initialize(ecSpec)
    return keyPairGenerator.generateKeyPair()
}

fun publicKeyToJwk(publicKey: PublicKey?): Map<String, String>? {
    return (publicKey as? ECPublicKey)?.let {
        mapOf(
            "kty" to "EC",
            "alg" to "ES256",
            "crv" to "P-256", // Consider changing based on the curve used
            "x" to it.w.affineX.toBase64(),
            "y" to it.w.affineY.toBase64()
        )
    }
}

fun privateKeyToJwk(privateKey: ECPrivateKey, publicKey: ECPublicKey): Map<String, String> {
//    val publicKey = privateKey.publicKey as ECPublicKey

    val encoder = Base64.getUrlEncoder().withoutPadding()

    return mapOf(
        "kty" to "EC",
        "alg" to "ES256",
        "crv" to "P-256", // 使用する曲線に基づいて変更してください
        "x" to encoder.encodeToString(publicKey.w.affineX.toByteArray()),
        "y" to encoder.encodeToString(publicKey.w.affineY.toByteArray()),
        "d" to encoder.encodeToString(privateKey.s.toByteArray())
    )
}


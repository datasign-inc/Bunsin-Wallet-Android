package jp.datasign.bunsin_wallet.ui.shared

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jp.datasign.bunsin_wallet.Constants
import jp.datasign.bunsin_wallet.oid.HeaderOptions
import jp.datasign.bunsin_wallet.oid.JwtVpJsonGenerator
import jp.datasign.bunsin_wallet.oid.JwtVpJsonPayloadOptions
import jp.datasign.bunsin_wallet.oid.JwtVpJsonPresentation
import jp.datasign.bunsin_wallet.signature.JWT
import jp.datasign.bunsin_wallet.utils.SigningOption
import jp.datasign.bunsin_wallet.utils.KeyPairUtil
import jp.datasign.bunsin_wallet.utils.KeyUtil
import jp.datasign.bunsin_wallet.utils.KeyUtil.toJwkThumbprintUri
import java.security.PublicKey

class JwtVpJsonGeneratorImpl(private val keyAlias: String = Constants.Cryptography.KEY_PAIR_ALIAS_FOR_KEY_JWT_VP_JSON) :
    JwtVpJsonGenerator {
    override fun generateJwt(
        vcJwt: String,
        headerOptions: HeaderOptions,
        payloadOptions: JwtVpJsonPayloadOptions
    ): String {
        val jwk = getJwk()
        val header =
            mapOf("alg" to headerOptions.alg, "typ" to headerOptions.typ, "jwk" to jwk)
        val sub = toJwkThumbprintUri(jwk)
        payloadOptions.iss = sub
        val jwtPayload = JwtVpJsonPresentation.genVpJwtPayload(vcJwt, payloadOptions)
        val objectMapper = jacksonObjectMapper()
        val vpTokenPayload =
            objectMapper.convertValue(jwtPayload, Map::class.java) as Map<String, Any>
        return JWT.sign(keyAlias, header, vpTokenPayload)
    }

    override fun getJwk(): Map<String, String> {
        if (!KeyPairUtil.isKeyPairExist(keyAlias)) {
            KeyPairUtil.generateSignVerifyKeyPair(keyAlias)
        }
        val publicKey: PublicKey = KeyPairUtil.getPublicKey(keyAlias)
            ?: throw IllegalStateException("Public key not found for alias: $keyAlias")
        val jwk = KeyUtil.publicKeyToJwk(publicKey, SigningOption())
        return jwk
    }
}
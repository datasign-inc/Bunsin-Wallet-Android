package jp.datasign.bunsin_wallet.ui.shared

import jp.datasign.bunsin_wallet.oid.KeyBinding
import jp.datasign.bunsin_wallet.oid.SdJwtVcPresentation
import jp.datasign.bunsin_wallet.signature.JWT
import jp.datasign.bunsin_wallet.utils.SDJwtUtil
import jp.datasign.bunsin_wallet.Constants

class KeyBindingImpl(val keyAlias: String) : KeyBinding {
    override fun generateJwt(
        sdJwt: String,
        selectedDisclosures: List<SDJwtUtil.Disclosure>,
        aud: String,
        nonce: String
    ): String {
        val (header, payload) = SdJwtVcPresentation.genKeyBindingJwtParts(
            sdJwt,
            selectedDisclosures,
            aud,
            nonce
        )
        return JWT.sign(Constants.Cryptography.KEY_BINDING, header, payload)
    }
}
package jp.datasign.bunsin_wallet.oid

import jp.datasign.bunsin_wallet.utils.SDJwtUtil

interface KeyBinding {
    fun generateJwt(
        sdJwt: String,
        selectedDisclosures: List<SDJwtUtil.Disclosure>, // todo 一段階抽象的な型を指定する
        aud: String,
        nonce: String
    ): String
}
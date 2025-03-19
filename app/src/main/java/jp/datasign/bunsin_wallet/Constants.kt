package jp.datasign.bunsin_wallet

object Constants {
    object VC {
        object CommentVC {
            const val RELYING_PARTY_DOMAIN = "boolcheck.com"

            const val COMMENT_VC_INPUT_DESCRIPTOR_ID = "true_false_comment"
            const val COMMENT_VC_TYPE_VALUE = "CommentCredential"
            const val TEXT_PATH = "$.vc.credentialSubject.comment"
            const val URL_PATH = "$.vc.credentialSubject.url"
            const val BOOL_VALUE_PATH = "$.vc.credentialSubject.bool_value"
        }
    }

    // boolcheck.comへの投稿に関する鍵の使い方については、以下の4スライド目も参照
    // https://docs.google.com/presentation/d/1f_F4s0xyXGTJ-MvOVyDpEGFhic-Zx0KzX-xshfRUlCk/edit?usp=sharing
    object Cryptography {
        const val KEY_BINDING = "bindingKey"
        const val KEY_PAIR_ALIAS_FOR_KEY_JWT_VP_JSON = "jwtVpJsonKey"

        const val KEYSTORE_TYPE = "AndroidKeyStore"
        const val KEY_ALGORITHM_EC = "EC"
        const val SIGNING_ALGORITHM = "SHA256withECDSA"
        const val CURVE_SPEC = "secp256r1"
    }
}
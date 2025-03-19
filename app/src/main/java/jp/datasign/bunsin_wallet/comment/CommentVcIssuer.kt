package jp.datasign.bunsin_wallet.comment

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jp.datasign.bunsin_wallet.Constants
import jp.datasign.bunsin_wallet.datastore.CredentialData
import jp.datasign.bunsin_wallet.signature.JWT
import jp.datasign.bunsin_wallet.utils.KeyPairUtil
import jp.datasign.bunsin_wallet.utils.KeyUtil
import jp.datasign.bunsin_wallet.utils.publicKeyToJwk
import jp.datasign.bunsin_wallet.vci.CredentialIssuerMetadata

// todo: improve
class CommentIssuerException(message: String) : Exception(message)

enum class ContentTruth(val value: Int) {
    // See below for value definitions.
    // https://www.notion.so/bool-check-app-0b1a6e0618134dacbdeae7d35e6f01cf?pvs=4#97ffcd4a031c43e881199d8f4529c35e
    TrueContent(1),
    FalseContent(0),
    IndeterminateContent(2);

    companion object {
        fun fromValue(value: Int): ContentTruth? {
            return entries.find { it.value == value }
        }
    }
}

data class Comment(
    val url: String,
    val comment: String,
    val boolValue: ContentTruth // ContentTruth は別途定義してください
)

data class CommentVcHeaderOptions(
    var alg: String = "ES256",
    var typ: String = "JWT"
)

data class CommentVcPayloadOptions(
    val iss: String,
    val nbf: Long
)

fun convertSnakeToCamelCase(jsonString: String): String {
    val snakeCaseMapper = jacksonObjectMapper()
    snakeCaseMapper.propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
    val map = snakeCaseMapper.readValue(jsonString, CredentialIssuerMetadata::class.java)


    val camelCaseMapper = ObjectMapper()
    camelCaseMapper.propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
    return camelCaseMapper.writeValueAsString(map)
}

class CommentVcIssuer(private val keyAlias: String) {
    init {
        if (!KeyPairUtil.isKeyPairExist(this.keyAlias)) {
            Log.d("CommentVcIssuer", "Generating key ${this.keyAlias} for comment signing")
            KeyPairUtil.generateSignVerifyKeyPair(this.keyAlias)
        }
    }

    fun issueCredential(
        url: String,
        comment: String,
        contentTruth: ContentTruth
    ): CredentialData {
        // 公開鍵を取得
        val issuerPublicKey = getIssuerPublicJwk()

        // 公開鍵情報を取得
        val kty = issuerPublicKey["kty"]
        val crv = issuerPublicKey["crv"]
        val x = issuerPublicKey["x"]
        val y = issuerPublicKey["y"]

        if (kty == null || crv == null || x == null || y == null) {
            throw CommentIssuerException("Unable to get comment issuer's public key")
        }

        // 公開鍵の thumbprint を生成
        val iss = KeyUtil.toJwkThumbprintUri(issuerPublicKey)

        // 現在時刻（秒単位）を取得
        val currentTimeSeconds = System.currentTimeMillis() / 1000

        // コメントオブジェクトを作成
        val commentObj = Comment(url, comment, contentTruth)

        // JWT を生成
        val jwt = generateJwt(
            commentObj,
            CommentVcHeaderOptions(),
            CommentVcPayloadOptions(iss = iss, nbf = currentTimeSeconds)
        )

        // CredentialData を構築
        val credentialMetadata = """
        {
            "credential_issuer": "https://self-issued.boolcheck.com/$iss",
            "credential_endpoint": "https://self-issued.boolcheck.com/$iss",
            "display": [{
                "name": "Boolcheck Comment VC Issuer",
                "locale": "en-US",
                "logo": {
                    "uri": "https://boolcheck.com/public/issuer-logo.png",
                    "alt_text": "a square logo of a comment credential issuer"
                }
            }],
            "credential_configurations_supported": {
                "${Constants.VC.CommentVC.COMMENT_VC_TYPE_VALUE}": {
                    "format": "jwt_vc_json",
                    "scope": "CommentCredential",
                    "credential_definition": {
                        "type": [
                            "${Constants.VC.CommentVC.COMMENT_VC_TYPE_VALUE}",
                            "VerifiableCredential"
                        ],
                        "credentialSubject": {
                            "url": {
                                "display": [
                                    {
                                        "name": "Url",
                                        "locale": "en-US"
                                    },
                                    {
                                        "name": "Url",
                                        "locale": "ja-JP"
                                    }
                                ]
                            },
                            "comment": {
                                "display": [
                                    {
                                        "name": "Comment",
                                        "locale": "en-US"
                                    },
                                    {
                                        "name": "コメント",
                                        "locale": "ja-JP"
                                    }
                                ]
                            },
                            "bool_value": {
                                "display": [
                                    {
                                        "name": "真偽値",
                                        "locale": "ja-JP"
                                    }
                                ]
                            }
                        }
                    },
                    "display": [
                        {
                            "name": "${Constants.VC.CommentVC.COMMENT_VC_TYPE_VALUE}",
                            "locale": "ja_JP",
                            "logo": {
                                "uri": "https://boolcheck.com/public/credential-logo.png",
                                "alt_text": "a square logo of a credential"
                            },
                            "background_color": "#12107c",
                            "text_color": "#FFFFFF"
                        }
                    ]
                }
            }
        }
    """.trimIndent()
        val convertedJson = convertSnakeToCamelCase(credentialMetadata)

        // CredentialData を作成して返却
        return CredentialData.newBuilder()
            .setId(java.util.UUID.randomUUID().toString())
            .setFormat("jwt_vc_json")
            .setCredential(jwt)
            .setIss(iss)
            .setIat(currentTimeSeconds)
            .setExp(currentTimeSeconds + 86400 * 365)
            .setType(Constants.VC.CommentVC.COMMENT_VC_TYPE_VALUE)
            .setCNonce("")
            .setCNonceExpiresIn(3600)
            .setAccessToken("")
            .setCredentialIssuerMetadata(convertedJson)
            .build()
    }


    fun generateJwt(
        comment: Comment,
        headerOptions: CommentVcHeaderOptions,
        payloadOptions: CommentVcPayloadOptions
    ): String {
        val header = mapOf(
            "alg" to headerOptions.alg,
            "typ" to headerOptions.typ,
            "jwk" to getIssuerPublicJwk() // 公開鍵を取得
        )

        val payload = mapOf(
            "iss" to payloadOptions.iss,
            "sub" to payloadOptions.iss,
            "nbf" to payloadOptions.nbf,
            "vc" to mapOf(
                "@context" to listOf("https://www.w3.org/2018/credentials/v1"),
                "type" to listOf(
                    "VerifiableCredential",
                    Constants.VC.CommentVC.COMMENT_VC_TYPE_VALUE
                ),
                "credentialSubject" to mapOf(
                    "url" to comment.url,
                    "comment" to comment.comment,
                    "bool_value" to comment.boolValue.value
                )
            )
        )

        val result = JWT.sign(keyAlias, header, payload)
        return result
    }

    fun getIssuerPublicJwk(): Map<String, String> {
        val publicKey = KeyPairUtil.getPublicKey(this.keyAlias)
        val jwk = publicKeyToJwk(publicKey)
            ?: throw CommentIssuerException("Unable to get public key as jwk")
        return jwk
    }
}
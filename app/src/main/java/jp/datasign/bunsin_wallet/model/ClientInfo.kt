package jp.datasign.bunsin_wallet.model

data class ClientInfo(
    var name: String = "",
    var url: String = "",
    var logoUrl: String = "",
    var policyUrl: String = "",
    var tosUrl: String = "",
    var jwkThumbprint: String = "",
    var identiconHash: Int = 0,
    var certificateInfo: CertificateInfo? = CertificateInfo(null, null, null, null, null, null)
)
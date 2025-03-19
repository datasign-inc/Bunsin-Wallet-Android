package jp.datasign.bunsin_wallet.model

data class RequestInfo(
    var responseType: String = "",
    var title: String = "",
    var name: String = "",
    var url: String = "",
    var comment: String = "",
    var boolValue: Int = 0,
    var clientInfo: ClientInfo,
)

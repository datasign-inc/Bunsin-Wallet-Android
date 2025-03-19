package jp.datasign.bunsin_wallet.oid

enum class SIOPErrors(val message: String) {
    RESPONSE_STATUS_UNEXPECTED("Received unexpected response status"),
    BAD_PARAMS("Wrong parameters provided."),
    REG_PASS_BY_REFERENCE_INCORRECTLY("Request error")
}

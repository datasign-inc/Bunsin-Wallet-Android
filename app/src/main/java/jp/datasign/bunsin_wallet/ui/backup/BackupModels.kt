package jp.datasign.bunsin_wallet.ui.backup

data class IdTokenSharingHistory(
    val rp: String,
    val accountIndex: Int,
    val createdAt: String,
    val accountUseCase: String,
    val thumbprint: String,
)

data class CredentialSharingHistory(
    val rp: String,
    val accountIndex: Int,
    val createdAt: String,
    val credentialID: String,
    var claims: List<Claim>,
    var rpName: String,

    var location: String?,
    var privacyPolicyUrl: String,
    var logoUrl: String
)

data class Claim(
    val claimKey: String,
    val claimValue: String,
    val purpose: String
)

data class BackupData(
    val seed: String,
    val idTokenSharingHistories: List<IdTokenSharingHistory>,
    val credentialSharingHistories: List<CredentialSharingHistory>
)

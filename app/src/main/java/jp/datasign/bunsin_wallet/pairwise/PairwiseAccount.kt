package jp.datasign.bunsin_wallet.pairwise

import android.content.Context
import arrow.core.Either
import jp.datasign.bunsin_wallet.datastore.IdTokenSharingHistoryStore
import jp.datasign.bunsin_wallet.signature.ECPublicJwk
import com.google.protobuf.Timestamp
import jp.datasign.bunsin_wallet.datastore.IdTokenSharingHistory
import jp.datasign.bunsin_wallet.utils.KeyUtil.toJwkThumbprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Base64

val TAG: String = ::PairwiseAccount.javaClass.simpleName

enum class AccountUseCase(val value: String) {
    DEFAULT_ANONYMOUS_ACCOUNT("defaultAnonymousAccount"),
    DEFAULT_IDENTIFIED_ACCOUNT("defaultIdentifiedAccount"),
    USER_CREATED_ALTER_EGO("userCreatedAlterEgo"),
}

class PairwiseAccount(
    private val context: Context,
    private val mnemonicWords: String,
    private val store: IdTokenSharingHistoryStore = IdTokenSharingHistoryStore.getInstance(
        context
    )
) {
    private val keyring: HDKeyRing = HDKeyRing(mnemonicWords)

    companion object {
        fun toECPublicJwk(jwk: PublicJwk): ECPublicJwk {
            return object : ECPublicJwk {
                override val kty = jwk.kty
                override val crv = jwk.crv
                override val x = jwk.x
                override val y = jwk.y
            }
        }
    }

    init {
    }

    suspend fun nextAccount(): Account {
        return withContext(Dispatchers.IO) {
            val accounts = store.getAll()

            val latestIndex = accounts.lastOrNull()?.accountIndex ?: -1
            val nextIndex = latestIndex + 1

            val publicJwk = keyring.getPublicJwk(nextIndex)
            val privateJwk = keyring.getPrivateJwk(nextIndex)
            val thumbprint = toJwkThumbprint(toECPublicJwk(publicJwk))
            val hash = thumbprintToInt(thumbprint)
            return@withContext Account(nextIndex, publicJwk, privateJwk, thumbprint, hash)
        }
    }

    suspend fun newAccount(
        rp: String,
        useCase: AccountUseCase = AccountUseCase.DEFAULT_IDENTIFIED_ACCOUNT,
    ): Either<String, Account> {
        return withContext(Dispatchers.IO) {
            val accounts = store.getAll()

            if (accounts.any { it.rp == rp }) {
                return@withContext Either.Left("the rp is already shared account")
            }

            val latestIndex = accounts.lastOrNull()?.accountIndex ?: -1
            val nextIndex = latestIndex + 1

            val publicJwk = keyring.getPublicJwk(nextIndex)
            val privateJwk = keyring.getPrivateJwk(nextIndex)
            val thumbprint = toJwkThumbprint(toECPublicJwk(publicJwk))
            val hash = thumbprintToInt(thumbprint)

            val currentInstant = Instant.now()
            val history = IdTokenSharingHistory.newBuilder()
                .setRp(rp)
                .setAccountIndex(nextIndex)
                .setAccountUseCase(useCase.value)
                .setCreatedAt(
                    Timestamp.newBuilder().setSeconds(currentInstant.epochSecond)
                        .setNanos(currentInstant.nano).build()
                ).build();
            store.save(history)
            return@withContext Either.Right(
                Account(
                    nextIndex,
                    publicJwk,
                    privateJwk,
                    thumbprint,
                    hash
                )
            )
        }
    }

    suspend fun getAccount(
        rp: String,
        index: Int = -1,
        useCase: AccountUseCase = AccountUseCase.DEFAULT_IDENTIFIED_ACCOUNT,
    ): Account? {
        return withContext(Dispatchers.IO) {
            val accounts = store.getAll().sortedByDescending { it.accountIndex }

            val matchingAccounts = if (index > -1) {
                // specificIndexが-1より大きい場合、accountIndexでフィルタ
                accounts.filter { it.rp == rp && it.accountIndex == index && it.accountUseCase == useCase.value }
            } else {
                // specificIndexが-1以下の場合、rpだけでフィルタ
                // indexの降順でソート済みなので、新しいインデックスが優先される
                accounts.filter { it.rp == rp && it.accountUseCase == useCase.value }
            }
            val matchingAccount = matchingAccounts.firstOrNull() ?: return@withContext null

            val index = matchingAccount.accountIndex

            val publicJwk = keyring.getPublicJwk(index)
            val privateJwk = keyring.getPrivateJwk(index)
            val thumbprint = toJwkThumbprint(toECPublicJwk(publicJwk))
            val hash = thumbprintToInt(thumbprint)

            return@withContext Account(index, publicJwk, privateJwk, thumbprint, hash)
        }
    }
}

data class Account(
    val index: Int,
    val publicJwk: PublicJwk,
    val privateJwk: Jwk,
    val thumbprint: String,
    val hash: Int
)

@OptIn(ExperimentalStdlibApi::class)
fun thumbprintToInt(thumbprint: String): Int {
    val bytes = Base64.getUrlDecoder().decode(thumbprint)
    val hexString = bytes.toHexString().substring(0, 4)
    return hexString.toInt(16)

}

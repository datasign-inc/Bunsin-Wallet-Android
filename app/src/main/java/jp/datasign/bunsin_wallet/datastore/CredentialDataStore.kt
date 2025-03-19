package jp.datasign.bunsin_wallet.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import jp.datasign.bunsin_wallet.utils.CredentialResponse
import jp.datasign.bunsin_wallet.utils.EncryptionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

open class CredentialDataStore(
    context: Context,
    storeFile: String,
) {
    companion object {
        @Volatile
        private var instance: CredentialDataStore? = null
        fun getInstance(
            context: Context,
            storeFile: String = "credential_data.pb",
        ): CredentialDataStore {
            return instance ?: synchronized(this) {
                instance ?: CredentialDataStore(context, storeFile).also { instance = it }
            }
        }

        fun resetInstance() {
            instance = null
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val credentialDataStore: DataStore<jp.datasign.bunsin_wallet.datastore.CredentialDataList> =
        DataStoreFactory.create(
            serializer = EncryptedCredentialDataListSerializer,
            produceFile = { context.dataDir.resolve(storeFile) },
            scope = scope
        )

    val credentialDataListFlow: Flow<jp.datasign.bunsin_wallet.datastore.CredentialDataList> =
        credentialDataStore.data

    fun responseToSchema(
        format: String,
        credentialResponse: CredentialResponse,
        credentialBasicInfo: Map<String, Any>,
        credentialIssuerMetadata: String,
    ): jp.datasign.bunsin_wallet.datastore.CredentialData {
        val builder = jp.datasign.bunsin_wallet.datastore.CredentialData.newBuilder()

        credentialResponse.cNonceExpiresIn?.let {
            builder.setCNonceExpiresIn(it)
        }

        credentialResponse.cNonce?.let {
            builder.setCNonce(it)
        }

        builder.setId(UUID.randomUUID().toString()).setFormat(format)
            .setCredential(credentialResponse.credential)
            .setIss(credentialBasicInfo["iss"] as String).setIat(credentialBasicInfo["iat"] as Long)
            .setExp(credentialBasicInfo["exp"] as Long)
            .setType(credentialBasicInfo["typeOrVct"] as String).credentialIssuerMetadata =
            credentialIssuerMetadata
        return builder.build()
    }

    // 保存
    suspend fun saveCredentialData(credentialData: jp.datasign.bunsin_wallet.datastore.CredentialData) {
        credentialDataStore.updateData { currentList ->
            currentList.toBuilder().addItems(credentialData).build()
        }
    }

    // 全件取得
    suspend fun getAllCredentials(): List<jp.datasign.bunsin_wallet.datastore.CredentialData> {
        val credentialDataList = credentialDataStore.data.first()
        return credentialDataList.itemsList
    }

    // 指定削除
    suspend fun deleteCredentialById(id: String) {
        credentialDataStore.updateData { currentList ->
            val updatedList = currentList.itemsList.filter { it.id != id }
            currentList.toBuilder().clearItems().addAllItems(updatedList).build()
        }
    }

    // 全件削除
    suspend fun deleteAllCredentials() {
        credentialDataStore.updateData { currentList ->
            currentList.toBuilder().clearItems().build()
        }
    }

    // 指定取得
    open suspend fun getCredentialById(id: String): jp.datasign.bunsin_wallet.datastore.CredentialData? {
        val credentialDataList = credentialDataStore.data.first()
        return credentialDataList.itemsList.firstOrNull { it.id == id }
    }

    object EncryptedCredentialDataListSerializer :
        Serializer<jp.datasign.bunsin_wallet.datastore.CredentialDataList> {

        override val defaultValue: jp.datasign.bunsin_wallet.datastore.CredentialDataList =
            jp.datasign.bunsin_wallet.datastore.CredentialDataList.getDefaultInstance()

        override suspend fun readFrom(input: InputStream): jp.datasign.bunsin_wallet.datastore.CredentialDataList {
            // Here, read the IV and then the encrypted data
            val ivSize = withContext(Dispatchers.IO) {
                input.read()
            }
            val iv = ByteArray(ivSize)
            withContext(Dispatchers.IO) {
                input.read(iv)
            }

            val encryptedData = input.readBytes()

            try {
                val decryptedData = EncryptionHelper.decrypt(encryptedData, iv)
                return jp.datasign.bunsin_wallet.datastore.CredentialDataList.parseFrom(
                    decryptedData
                )
            } catch (e: Exception) {
                return jp.datasign.bunsin_wallet.datastore.CredentialDataList.getDefaultInstance()
            }
        }

        override suspend fun writeTo(
            t: jp.datasign.bunsin_wallet.datastore.CredentialDataList,
            output: OutputStream,
        ) {
            val data = t.toByteArray()
            val (encryptedData, iv) = EncryptionHelper.encrypt(data)

            // Write the IV size, then the IV, and then the encrypted data
            withContext(Dispatchers.IO) {
                output.write(iv.size)
            }
            withContext(Dispatchers.IO) {
                output.write(iv)
            }
            withContext(Dispatchers.IO) {
                output.write(encryptedData)
            }
        }
    }
}
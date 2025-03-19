package jp.datasign.bunsin_wallet.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

class IdTokenSharingHistoryStore(
    context: Context,
    storeFile: String,
) {
    companion object {
        @Volatile
        private var instance: IdTokenSharingHistoryStore? = null
        fun getInstance(
            context: Context,
            storeFile: String = "id_token_sharing_history.pb",
        ): IdTokenSharingHistoryStore {
            return instance ?: synchronized(this) {
                instance ?: IdTokenSharingHistoryStore(context, storeFile).also { instance = it }
            }
        }

        fun resetInstance() {
            instance = null
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val historyDataStore: DataStore<IdTokenSharingHistories> =
        DataStoreFactory.create(
            serializer = IdTokenSharingHistoriesSerializer,
            produceFile = { context.dataDir.resolve(storeFile) },
            scope = scope
        )

    suspend fun save(history: IdTokenSharingHistory) {
        historyDataStore.updateData { currentList ->
            currentList.toBuilder().addItems(history).build()
        }
    }

    suspend fun getAll(): List<IdTokenSharingHistory> {
        return historyDataStore.data.first().itemsList
    }

    suspend fun findAllByRp(rp: String): List<IdTokenSharingHistory> {
        return getAll().filter { it.rp == rp }
    }

    object IdTokenSharingHistoriesSerializer :
        Serializer<IdTokenSharingHistories> {

        override val defaultValue: IdTokenSharingHistories =
            IdTokenSharingHistories.getDefaultInstance()

        override suspend fun readFrom(input: InputStream): IdTokenSharingHistories {
            try {
                return IdTokenSharingHistories.parseFrom(
                    input
                )
            } catch (exception: InvalidProtocolBufferException) {
                throw CorruptionException("Cannot read proto.", exception)
            }
        }

        override suspend fun writeTo(
            t: IdTokenSharingHistories,
            output: OutputStream
        ) {
            t.writeTo(output)
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
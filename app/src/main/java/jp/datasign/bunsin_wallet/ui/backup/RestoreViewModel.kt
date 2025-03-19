package jp.datasign.bunsin_wallet.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jp.datasign.bunsin_wallet.R
import jp.datasign.bunsin_wallet.datastore.CredentialSharingHistoryStore
import jp.datasign.bunsin_wallet.datastore.IdTokenSharingHistoryStore
import jp.datasign.bunsin_wallet.datastore.PreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class RestoreViewModel : ViewModel() {
    // ファイルURL
    private val _url = MutableLiveData<Uri?>()
    val uri: LiveData<Uri?> = _url

    // クローズ要求通知
    private val _shouldClose = MutableLiveData<Boolean>()
    val shouldClose: LiveData<Boolean> = _shouldClose

    // トーストメッセージ
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    fun setUri(uri: Uri) {
        _url.value = uri
    }

    fun clearUri() {
        _url.value = null
    }

    private fun requestClose() {
        _shouldClose.value = true
    }

    fun selectFile(context: Context) {
        if (uri.value != null) {
            val uri: Uri = uri.value!!
            viewModelScope.launch(Dispatchers.IO) {
                // zipファイルを解凍
                val unzipResult = unzipTextEntries(uri, context)
                val entries = unzipResult.getOrElse {
                    // zipファイルが想定と異なる場合はトースト表示して終了
                    println(it.message)
                    withContext(Dispatchers.Main) {
                        _message.value = context.getString(R.string.select_invalid_backup_file)
                    }
                    return@launch
                }
                val backup = try {
                    val backupString = entries[0]
                    val objectMapper = jacksonObjectMapper()
                    objectMapper.readValue(backupString, BackupData::class.java)
                } catch (e: Exception) {
                    // デシリアライズできない場合はトースト表示して終了
                    println(e)
                    withContext(Dispatchers.Main) {
                        _message.value = context.getString(R.string.select_invalid_backup_file)
                    }
                    return@launch
                }
                println(backup)

                println("restore seed")
                val dataStore = PreferencesDataStore(context)
                dataStore.saveSeed(backup.seed)

                println("restore history of id_token sharing")
                val store: IdTokenSharingHistoryStore =
                    IdTokenSharingHistoryStore.getInstance(context)
                backup.idTokenSharingHistories.forEach {
                    val history =
                        jp.datasign.bunsin_wallet.datastore.IdTokenSharingHistory.newBuilder()
                            .setRp(it.rp)
                            .setAccountIndex(it.accountIndex)
                            .setAccountUseCase(it.accountUseCase)
                            .setThumbprint(it.thumbprint)
                            .setCreatedAt(it.createdAt.toDateFromISO8601().toGoogleTimestamp())
                            .build();
                    store.save(history)
                }

                println("restore history of credential sharing")
                val store2 = CredentialSharingHistoryStore.getInstance(context)
                backup.credentialSharingHistories.forEach {
                    val builder =
                        jp.datasign.bunsin_wallet.datastore.CredentialSharingHistory.newBuilder()
                            .setRp(it.rp)
                            .setAccountIndex(it.accountIndex)
                            .setCreatedAt(it.createdAt.toDateFromISO8601().toGoogleTimestamp())
                            .setCredentialID(it.credentialID)
                            .setRpName(it.rpName)
                            .setRpLocation(it.location ?: "")
                            .setRpPrivacyPolicyUrl(it.privacyPolicyUrl)
                            .setRpLogoUrl(it.logoUrl)
                    it.claims.forEach { claim ->
                        val tmp =
                            jp.datasign.bunsin_wallet.datastore.Claim.newBuilder()
                                .setClaimKey(claim.claimKey)
                                .setClaimValue(claim.claimValue)
                                .setPurpose(claim.purpose)
                                .build()
                        builder.addClaims(tmp)
                    }
                    val history = builder.build()
                    store2.save(history)
                }
                withContext(Dispatchers.Main) {
                    // 処理成功通知
                    _message.value = context.getString(R.string.restored_data)
                    _shouldClose.value = true
                }
            }
        }

    }

    private fun unzipTextEntries(zipFileUri: Uri, context: Context): Result<Array<String>> {
        val contentResolver = context.applicationContext.contentResolver
        return try {
            val textEntries = mutableListOf<String>()

            contentResolver.openInputStream(zipFileUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var zipEntry = zipInputStream.nextEntry
                    while (zipEntry != null) {
                        if (!zipEntry.isDirectory) {
                            // ストリームから直接バイトを読み込み、それをテキストに変換
                            val buffer = ByteArrayOutputStream()
                            val dataBuffer = ByteArray(1024) // 1KBのバッファ
                            var count: Int
                            while (zipInputStream.read(dataBuffer).also { count = it } != -1) {
                                buffer.write(dataBuffer, 0, count)
                            }
                            val text = buffer.toString("UTF-8")
                            // textEntries.add("File: ${zipEntry.name}\nContent:$text")
                            textEntries.add(text)
                        }
                        zipInputStream.closeEntry() // エントリを正しく閉じる
                        zipEntry = zipInputStream.nextEntry
                    }
                }
            }
            Result.success(textEntries.toTypedArray())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
package jp.datasign.bunsin_wallet.ui.certificate

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.datasign.bunsin_wallet.datastore.CredentialDataStore
import kotlinx.coroutines.launch

class CertificateViewModel(private val credentialDataStore: CredentialDataStore) : ViewModel() {
    private val _text = MutableLiveData<String>().apply {
        value = "証明書がありません"
    }
    val text: LiveData<String> = _text

    // リストデータを保持するLiveData
    private val _credentialDataList =
        MutableLiveData<jp.datasign.bunsin_wallet.datastore.CredentialDataList?>()
    val credentialDataList: LiveData<jp.datasign.bunsin_wallet.datastore.CredentialDataList?> =
        _credentialDataList

    private fun setCredentialData(schema: jp.datasign.bunsin_wallet.datastore.CredentialDataList) {
        _credentialDataList.value = schema
    }

    init {
        viewModelScope.launch {
            credentialDataStore.credentialDataListFlow.collect { schema ->
                setCredentialData(schema)
            }
        }
    }
}
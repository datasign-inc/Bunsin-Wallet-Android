package jp.datasign.bunsin_wallet.ui.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import jp.datasign.bunsin_wallet.model.RequestInfo
import jp.datasign.bunsin_wallet.oid.OpenIdProvider
import jp.datasign.bunsin_wallet.oid.PresentationDefinition
import jp.datasign.bunsin_wallet.oid.SubmissionCredential
import jp.datasign.bunsin_wallet.vci.CredentialIssuerMetadata

class CredentialSharingViewModel : ViewModel() {
    val selectedCredential = MutableLiveData<SubmissionCredential?>()

    val _presentationDefinition = MutableLiveData<PresentationDefinition?>()
    val presentationDefinition: LiveData<PresentationDefinition?> = _presentationDefinition

    lateinit var credentialIssuerMetadata: CredentialIssuerMetadata
    lateinit var credentialType: String

    fun reset() {
        selectedCredential.value = null
        setPresentationDefinition(null)
    }

    fun setSelectedCredential(
        type: String,
        data: SubmissionCredential,
        metaData: CredentialIssuerMetadata
    ) {
        credentialType = type
        selectedCredential.value = data
        credentialIssuerMetadata = metaData
    }

    fun setPresentationDefinition(data: PresentationDefinition?) {
        _presentationDefinition.value = data
    }
}
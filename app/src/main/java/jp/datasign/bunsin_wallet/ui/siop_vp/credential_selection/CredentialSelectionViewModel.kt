package jp.datasign.bunsin_wallet.ui.siop_vp.credential_selection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jp.datasign.bunsin_wallet.datastore.CredentialDataStore
import jp.datasign.bunsin_wallet.oid.OpenIdProvider
import jp.datasign.bunsin_wallet.oid.PresentationDefinition
import jp.datasign.bunsin_wallet.utils.MetadataUtil
import jp.datasign.bunsin_wallet.vci.CredentialIssuerMetadata
import kotlinx.coroutines.launch

open class CertificateSelectionViewModel() : ViewModel() {
    private val _credentialDataList = MutableLiveData<List<CredentialInfo>?>()
    val credentialDataList: LiveData<List<CredentialInfo>?> = _credentialDataList

    private var _credentialDataStore: CredentialDataStore? = null
    open fun setCredentialDataStore(credentialDataStore: CredentialDataStore) {
        _credentialDataStore = credentialDataStore
    }

    open fun getData(presentationDefinition: PresentationDefinition) {
        viewModelScope.launch {
            _credentialDataStore?.credentialDataListFlow?.collect { schema ->
                val objectMapper = jacksonObjectMapper()
                val items = schema?.itemsList
                if (items != null) {
                    val infos = items.mapNotNull {
                        val credential = it.credential
                        val format = it.format
                        val types =
                            MetadataUtil.extractTypes(format, credential)
                        if (types.contains("CommentCredential")) {
                            // filter comment vc
                            null
                        } else {
                            if (format == "vc+sd-jwt") {
                                val selectedClaims = OpenIdProvider.selectRequestedClaims(
                                    credential,
                                    presentationDefinition.inputDescriptors
                                )
                                if (selectedClaims.satisfied) {
                                    val metadata: CredentialIssuerMetadata = objectMapper.readValue(
                                        it.credentialIssuerMetadata,
                                        CredentialIssuerMetadata::class.java
                                    )
                                    val credentialSupported =
                                        metadata.credentialConfigurationsSupported[types.firstOrNull()]
                                    // val displayData = credentialSupported?.display?.firstOrNull()
                                    val displayData = credentialSupported?.display?.let { display ->
                                        MetadataUtil.getLocalizedDisplayName(display)
                                    }
                                    CredentialInfo(
                                        id = it.id,
                                        name = displayData ?: "Metadata not found",
                                        issuer = it.iss
                                    )
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        }
                    }.toMutableList()
                    infos.add(CredentialInfo(useCredential = false))
                    setCredentialData(infos)
                }
            }
        }
    }

    open fun setCredentialData(data: List<CredentialInfo>) {
        _credentialDataList.value = data
    }

}

data class CredentialInfo(
    var id: String = "",
    var name: String = "",
    var issuer: String = "",
    var useCredential: Boolean = true,
)

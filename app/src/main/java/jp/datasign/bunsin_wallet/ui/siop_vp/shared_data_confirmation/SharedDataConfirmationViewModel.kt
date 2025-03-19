package jp.datasign.bunsin_wallet.ui.siop_vp.shared_data_confirmation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jp.datasign.bunsin_wallet.datastore.CredentialData
import jp.datasign.bunsin_wallet.datastore.CredentialDataStore
import jp.datasign.bunsin_wallet.oid.InputDescriptor
import jp.datasign.bunsin_wallet.oid.OpenIdProvider
import jp.datasign.bunsin_wallet.oid.PresentationDefinition
import jp.datasign.bunsin_wallet.oid.RequestedClaim
import jp.datasign.bunsin_wallet.oid.SubmissionCredential
import jp.datasign.bunsin_wallet.model.RequestInfo
import jp.datasign.bunsin_wallet.utils.MetadataUtil
import jp.datasign.bunsin_wallet.utils.SDJwtUtil
import jp.datasign.bunsin_wallet.vci.CredentialIssuerMetadata
import jp.datasign.bunsin_wallet.vci.Display
import kotlinx.coroutines.launch

class SharedDataConfirmationViewModel : ViewModel() {

    private val _errorMessage = MutableLiveData<String?>()

    private var _credentialDataStore: CredentialDataStore? = null
    open fun setCredentialDataStore(credentialDataStore: CredentialDataStore) {
        _credentialDataStore = credentialDataStore
    }

    private val _requestInfo = MutableLiveData<RequestInfo>()
    val requestInfo: LiveData<RequestInfo> = _requestInfo
    public fun setRequestInfo(requestInfo: RequestInfo) {
        _requestInfo.value = requestInfo
    }

    private val _subJwk = MutableLiveData<String?>()
    val subJwk: LiveData<String?> = _subJwk
    fun setSubJwk(value: String) {
        _subJwk.value = value
    }

    private var _credential = MutableLiveData<CredentialData?>()
    var credential: LiveData<CredentialData?> = _credential

    var inputDescriptor: InputDescriptor? = null

    private val _claims = MutableLiveData<List<RequestedClaim>?>()
    val claims: LiveData<List<RequestedClaim>?> = _claims

    private val _submissionCredentials =
        MutableLiveData<MutableList<SubmissionCredential>>(mutableListOf())
    val submissionCredentials: LiveData<MutableList<SubmissionCredential>> = _submissionCredentials

    fun resetErrorMessage() {
        _errorMessage.value = null
    }

    fun setEmptyClaims() {
        _claims.value = listOf()
    }

    lateinit var displayMap: Map<String, List<Display>>
    lateinit var claimOrder: List<String>

    open fun getData(credentialId: String, presentationDefinition: PresentationDefinition) {
        viewModelScope.launch {
            _credentialDataStore?.credentialDataListFlow?.collect { schema ->
                val objectMapper = jacksonObjectMapper()
                val items = schema?.itemsList
                if (items != null) {
                    val cred = items.find {
                        it.id == credentialId
                    }
                    if (cred != null) {
                        _credential.value = cred
                        if (cred.format == "vc+sd-jwt") {
                            val types =
                                MetadataUtil.extractTypes(cred.format, cred.credential)
                            val selectedClaims = OpenIdProvider.selectRequestedClaims(
                                cred.credential,
                                presentationDefinition.inputDescriptors
                            )
                            if (selectedClaims.satisfied) {
                                val metadata: CredentialIssuerMetadata = objectMapper.readValue(
                                    cred.credentialIssuerMetadata,
                                    CredentialIssuerMetadata::class.java
                                )
                                val credentialSupported =
                                    metadata.credentialConfigurationsSupported[types.firstOrNull()]
                                credentialSupported?.let { cs ->
                                    displayMap = MetadataUtil.extractDisplayByClaim(cs)
                                    claimOrder = MetadataUtil.extractOrder(cs)
                                }
                                inputDescriptor = selectedClaims.matchedInputDescriptor
                                _claims.value = selectedClaims.selectedClaims
                            }
                        }
                    }
                }
            }
        }
    }

    open fun saveData(commentVc: CredentialData) {
        viewModelScope.launch {
            _credentialDataStore?.saveCredentialData(commentVc)
        }
    }
}

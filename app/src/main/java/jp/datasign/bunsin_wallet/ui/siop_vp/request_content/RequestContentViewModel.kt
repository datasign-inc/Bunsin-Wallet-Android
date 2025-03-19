package jp.datasign.bunsin_wallet.ui.siop_vp.request_content

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import jp.datasign.bunsin_wallet.Constants
import jp.datasign.bunsin_wallet.R
import jp.datasign.bunsin_wallet.datastore.CredentialSharingHistoryStore
import jp.datasign.bunsin_wallet.datastore.IdTokenSharingHistoryStore
import jp.datasign.bunsin_wallet.pairwise.Account
import jp.datasign.bunsin_wallet.pairwise.PairwiseAccount
import jp.datasign.bunsin_wallet.datastore.PreferencesDataStore
import jp.datasign.bunsin_wallet.pairwise.HDKeyRing
import jp.datasign.bunsin_wallet.oid.OpenIdProvider
import jp.datasign.bunsin_wallet.oid.PresentationDefinition
import jp.datasign.bunsin_wallet.utils.SigningOption
import jp.datasign.bunsin_wallet.oid.SubmissionCredential
import jp.datasign.bunsin_wallet.signature.ECPrivateJwk
import jp.datasign.bunsin_wallet.signature.SignatureUtil
import jp.datasign.bunsin_wallet.ui.shared.KeyBindingImpl
import jp.datasign.bunsin_wallet.utils.CertificateUtil.getCertificateInformation
import com.google.protobuf.Timestamp
import jp.datasign.bunsin_wallet.datastore.IdTokenSharingHistory
import jp.datasign.bunsin_wallet.model.CertificateInfo
import jp.datasign.bunsin_wallet.model.ClientInfo
import jp.datasign.bunsin_wallet.model.RequestInfo
import jp.datasign.bunsin_wallet.oid.TokenSendResult
import jp.datasign.bunsin_wallet.oid.mergeOAuth2AndOpenIdInRequestPayload
import jp.datasign.bunsin_wallet.pairwise.AccountUseCase
import jp.datasign.bunsin_wallet.ui.shared.JwtVpJsonGeneratorImpl
import jp.datasign.bunsin_wallet.utils.KeyUtil
import jp.datasign.bunsin_wallet.utils.KeyUtil.toJwkThumbprintUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

val TAG = TokenSharingViewModel::class.simpleName

class TokenSharingViewModel : ViewModel() {
    var isInitialized = false

    private lateinit var openIdProvider: OpenIdProvider

    private val _clientInfo = MutableLiveData<ClientInfo>()
    val clientInfo: LiveData<ClientInfo> = _clientInfo

    private val _subJwk = MutableLiveData<String?>()
    val subJwk: LiveData<String?> = _subJwk
    fun setSubJwk(value: String) {
        _subJwk.value = value
    }

    private val _presentationDefinition = MutableLiveData<PresentationDefinition>()
    val presentationDefinition: LiveData<PresentationDefinition> = _presentationDefinition
    fun setPresentationDefinition(data: PresentationDefinition) {
        _presentationDefinition.value = data
    }

    private val _requestInfo = MutableLiveData<RequestInfo>()
    val requestInfo: LiveData<RequestInfo> = _requestInfo
    public fun setRequestInfo(requestInfo: RequestInfo) {
        _requestInfo.value = requestInfo
    }

    // リクエスト処理完了通知
    private val _initDone = MutableLiveData<Boolean>()
    val initDone: LiveData<Boolean> = _initDone

    // レスポンス処理完了通知
    private val _doneSuccessfully = MutableLiveData<Boolean>()
    val doneSuccessfully: LiveData<Boolean> = _doneSuccessfully

    // 提供結果
    private val _tokenSendResult = MutableLiveData<TokenSendResult>()
    val tokenSendResult: LiveData<TokenSendResult> = _tokenSendResult

    // クローズ要求通知
    private val _shouldClose = MutableLiveData<Boolean>()
    val shouldClose: LiveData<Boolean> = _shouldClose

    //エラー表示用のLiveData
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var index: Int = -1
    private fun setIndex(index: Int) {
        this.index = index
    }

    private var accountUseCase: AccountUseCase = AccountUseCase.DEFAULT_IDENTIFIED_ACCOUNT
    fun setAccountUseCase(accountUseCase: AccountUseCase) {
        this.accountUseCase = accountUseCase
    }

    private var seed: String? = null
    fun setSeed(seed: String) {
        this.seed = seed
    }

    fun requestClose() {
        _shouldClose.value = true
    }

    fun resetCloseRequest() {
        _shouldClose.value = false
    }

    fun resetErrorMessage() {
        _errorMessage.value = null
    }

    fun setClientName(value: String) {
        _clientInfo.value = _clientInfo.value?.copy(name = value)
    }

    fun setClientUrl(value: String) {
        _clientInfo.value = _clientInfo.value?.copy(url = value)
    }

    fun setClientLogoUrl(value: String) {
        _clientInfo.value = _clientInfo.value?.copy(logoUrl = value)
    }

    fun setClientPolicyUrl(value: String) {
        _clientInfo.value = _clientInfo.value?.copy(policyUrl = value)
    }

    fun setClientTosUrl(value: String) {
        _clientInfo.value = _clientInfo.value?.copy(tosUrl = value)
    }

    fun setJwkThumbprint(value: String) {
        _clientInfo.value = _clientInfo.value?.copy(jwkThumbprint = value)
        _subJwk.value = value
    }

    fun setIdenticon(value: Int) {
        _clientInfo.value = _clientInfo.value?.copy(identiconHash = value)
    }

    fun setCertificateInfo(value: CertificateInfo) {
        _clientInfo.value = _clientInfo.value?.copy(certificateInfo = value)
    }

    fun accessPairwiseAccountManager(fragment: Fragment, url: String, index: Int = -1) {
        viewModelScope.launch() {
            // SIOP要求を処理する前にペアワイズアカウントのキーペアの使用準備をする
            // 生体認証をpassできたらSIOP要求を処理する
            val dataStore = PreferencesDataStore(fragment.requireContext())
            val seedState = dataStore.getSeed(fragment)
            if (seedState != null) {
                if (seedState.isRight()) {
                    var seed = (seedState as Either.Right).value
                    Log.d(TAG, "accessed seed successfully")
                    if (seed.isNullOrEmpty()) {
                        // 初回のシード生成
                        val hdKeyRing = HDKeyRing(null)
                        seed = hdKeyRing.getMnemonicString()
                        dataStore.saveSeed(seed)
                    }
                    setSeed(seed)
                    // SIOP要求処理(一度フラグメント側に制御を返す構造の方が望ましい)
                    processAuthRequest(fragment.requireContext(), url, seed, index)
                } else {
                    val biometricStatus = (seedState as Either.Left).value
                    Log.d(TAG, "BiometricStatus: $biometricStatus")
                    withContext(Dispatchers.Main) {
                        requestClose()
                    }
                }
            }
        }
    }

    private fun processAuthRequest(context: Context, url: String, seed: String, index: Int) {
        Log.d(TAG, "processSiopRequest")
        val opt = SigningOption(signingCurve = "secp256k1", signingAlgo = "ES256K")
        val idProvider = OpenIdProvider(url, opt)
        openIdProvider = idProvider
        viewModelScope.launch(Dispatchers.IO) {
            val result = idProvider.processAuthorizationRequest()
            result.fold(
                onFailure = { value ->
                    Log.e(TAG, "エラーが発生しました: ${value}")
                    withContext(Dispatchers.Main) {
                        _initDone.value = true
                        _errorMessage.value = value.message
                    }
                },
                onSuccess = { siopRequest ->
                    Log.d(TAG, "processSiopRequest success")
                    val (scheme, requestObject, authorizationRequestPayload, requestObjectJwt, registrationMetadata, presentationDefinition, certs) = siopRequest
                    val certificateInfo = getCertificateInformation(certs)
                    val authRequest = mergeOAuth2AndOpenIdInRequestPayload(
                        authorizationRequestPayload,
                        requestObject
                    )
                    val responseType = authRequest.responseType!!

                    val keyBinding = KeyBindingImpl(Constants.Cryptography.KEY_BINDING)
                    idProvider.setKeyBinding(keyBinding)

                    val jwtVpJsonGenerator =
                        JwtVpJsonGeneratorImpl(Constants.Cryptography.KEY_BINDING)
                    idProvider.setJwtVpJsonGenerator(jwtVpJsonGenerator)

                    // getServerCertificates("https://datasign.jp/")
                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "update ui")
                        _clientInfo.value = ClientInfo()
                        if (registrationMetadata.clientName != null) {
                            setClientName(registrationMetadata.clientName)
                        }
                        val clientId =
                            requestObject?.clientId ?: authorizationRequestPayload.clientId
                        if (clientId != null) {
                            setClientUrl(clientId)
                        }
                        if (registrationMetadata.logoUri != null) {
                            setClientLogoUrl(registrationMetadata.logoUri)
                        }
                        if (registrationMetadata.policyUri != null) {
                            setClientPolicyUrl(registrationMetadata.policyUri)
                        }
                        if (registrationMetadata.tosUri != null) {
                            setClientTosUrl(registrationMetadata.tosUri)
                        }
                        if (certificateInfo != null) {
                            setCertificateInfo(certificateInfo)
                        }
                        if (responseType == "id_token") {
                            val requestInfo = RequestInfo(
                                responseType = responseType,
                                clientInfo = _clientInfo.value!!
                            )
                            _requestInfo.value = requestInfo
                        } else if (presentationDefinition != null) {
                            _presentationDefinition.value = presentationDefinition!!
                            val fields =
                                presentationDefinition.inputDescriptors[0].constraints.fields
                            val url = fields?.get(1)?.filter?.get("const")
                            val comment = fields?.get(2)?.filter?.get("const")
                            val boolValue = fields?.get(3)?.filter?.get("maximum")
                            val requestInfo = RequestInfo(
                                responseType = responseType,
                                title = "真偽情報に署名を行い、その情報をBoolcheckに送信します",
                                boolValue = Integer.parseInt(boolValue.toString()),
                                comment = comment.toString(),
                                url = url.toString(),
                                clientInfo = _clientInfo.value!!
                            )
                            _requestInfo.value = requestInfo
                        }
                        _initDone.value = true
                    }
                }
            )
        }
    }

    fun setAccount(fragment: Fragment, useCase: AccountUseCase) {
        val seed = this.seed!!
        val clientId = clientInfo.value!!.url
        viewModelScope.launch(Dispatchers.IO) {
            val accountManager = PairwiseAccount(fragment.requireContext(), seed)
            var account: Account? = accountManager.getAccount(clientId, index, useCase)
            if (account == null) {
                account = accountManager.nextAccount()
            } else if (useCase == AccountUseCase.DEFAULT_ANONYMOUS_ACCOUNT) {
                // Generate a new account each time if anonymous
                account = accountManager.nextAccount()
            }
            setIndex(account.index)
            setAccountUseCase(useCase)

            val jwk = account!!.privateJwk
            val privateJwk = object : ECPrivateJwk {
                override val kty = jwk.kty
                override val crv = jwk.crv
                override val x = jwk.x
                override val y = jwk.y
                override val d = jwk.d
            }
            val keyPair = SignatureUtil.generateECKeyPair(privateJwk)
            openIdProvider.setKeyPair(keyPair)
            val option = SigningOption(signingAlgo = "ES256K")
            val subJwk = KeyUtil.keyPairToPublicJwk(keyPair, option)
            val sub = toJwkThumbprintUri(subJwk)

            withContext(Dispatchers.Main) {
                setJwkThumbprint(sub)
                setIdenticon(account.hash)
            }
        }
    }

    fun shareToken(fragment: Fragment, credentials: List<SubmissionCredential>?) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = openIdProvider.respondToken(credentials)
            result.fold(
                onFailure = { value ->
                    Log.e(TAG, value.message, value)
                    withContext(Dispatchers.Main) {
                        val context = fragment.requireContext()
                        val msg = "${context.getString(R.string.error_occurred)} ${value.message}"
                        Toast.makeText(
                            context,
                            msg,
                            Toast.LENGTH_SHORT
                        ).show()
                        requestClose()
                    }
                },
                onSuccess = { tokenSendResult ->
                    if (tokenSendResult.sharedIdToken != null) {
                        // postに成功したらログイン履歴を記録
                        Log.d(TAG, "store id_token sharing history")
                        val store: IdTokenSharingHistoryStore =
                            IdTokenSharingHistoryStore.getInstance(fragment.requireContext())
                        val currentInstant = Instant.now()
                        val history =
                            IdTokenSharingHistory.newBuilder()
                                .setRp(clientInfo.value!!.url)
                                .setAccountIndex(index)
                                .setAccountUseCase(accountUseCase.value)
                                .setThumbprint(subJwk.value)
                                .setCreatedAt(
                                    Timestamp.newBuilder()
                                        .setSeconds(currentInstant.epochSecond)
                                        .setNanos(currentInstant.nano)
                                        .build()
                                )
                                .build();
                        store.save(history)
                    }
                    if (tokenSendResult.sharedCredentials != null) {
                        val store =
                            CredentialSharingHistoryStore.getInstance(fragment.requireContext())
                        val currentInstant = Instant.now()
                        tokenSendResult.sharedCredentials?.forEach { it ->
                            val openIdProviderSiopRequest =
                                openIdProvider.getProcessedRequestData()
                            val registrationPayload = openIdProviderSiopRequest.registrationMetadata
                            val builder =
                                jp.datasign.bunsin_wallet.datastore.CredentialSharingHistory.newBuilder()
                                    .setRp(openIdProviderSiopRequest.authorizationRequestPayload.clientId)
                                    .setAccountIndex(index)
                                    .setCreatedAt(
                                        Timestamp.newBuilder()
                                            .setSeconds(currentInstant.epochSecond)
                                            .setNanos(currentInstant.nano)
                                            .build()
                                    )
                                    .setRpName(registrationPayload.clientName)
                                    .setRpPrivacyPolicyUrl(registrationPayload.policyUri)
                                    .setRpLogoUrl(registrationPayload.logoUri)
                                    .setCredentialID(it.id)
                            it.sharedClaims.forEach { claim ->
                                val tmp =
                                    jp.datasign.bunsin_wallet.datastore.Claim.newBuilder()
                                        .setClaimKey(claim.name)
                                        .setPurpose("") // todo: The definition of DisclosedClaim needs to be revised to set this value.
                                        .setClaimValue(claim.value)
                                builder.addClaims(tmp)
                            }
                            val history = builder.build()
                            store.save(history)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _tokenSendResult.value = tokenSendResult
                        _doneSuccessfully.value = true
                    }
                }
            )
        }
    }
}
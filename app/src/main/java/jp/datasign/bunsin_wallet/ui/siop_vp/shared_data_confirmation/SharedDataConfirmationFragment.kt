package jp.datasign.bunsin_wallet.ui.siop_vp.shared_data_confirmation

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import jp.datasign.bunsin_wallet.Constants
import jp.datasign.bunsin_wallet.MainActivity
import jp.datasign.bunsin_wallet.R
import jp.datasign.bunsin_wallet.comment.CommentVcIssuer
import jp.datasign.bunsin_wallet.comment.ContentTruth
import jp.datasign.bunsin_wallet.datastore.CredentialDataStore
import jp.datasign.bunsin_wallet.oid.SubmissionCredential
import jp.datasign.bunsin_wallet.oid.TokenSendResult
import jp.datasign.bunsin_wallet.pairwise.AccountUseCase
import jp.datasign.bunsin_wallet.ui.shared.CredentialSharingViewModel
import jp.datasign.bunsin_wallet.ui.siop_vp.TokenSharingFragmentMenuProvider
import jp.datasign.bunsin_wallet.ui.siop_vp.request_content.TokenSharingViewModel
import jp.datasign.bunsin_wallet.utils.MetadataUtil



class SharedDataConfirmationFragment : Fragment() {

    companion object {
        val tag = SharedDataConfirmationFragment::class.simpleName
    }

    private val args: SharedDataConfirmationFragmentArgs by navArgs()
    val viewModel: SharedDataConfirmationViewModel by viewModels()

    // val viewModel2: TokenSharingViewModel by viewModels()
    val viewModel2: TokenSharingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val activity = requireActivity()
        val menuProvider = TokenSharingFragmentMenuProvider(this, activity.menuInflater)
        activity.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewModel2.tokenSendResult.observe(viewLifecycleOwner, ::onPostResult)
        viewModel2.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.resetErrorMessage()
            }
        }
        viewModel2.doneSuccessfully.observe(viewLifecycleOwner, ::onUpdateProcessCompletion)
        viewModel2.subJwk.observe(viewLifecycleOwner) {
            println("subjwk: $it")
        }

        val credentialDataStore = CredentialDataStore.getInstance(requireContext())
        viewModel.setCredentialDataStore(credentialDataStore)

        val fragment = this
        return ComposeView(requireContext()).apply {
            val credentialId = args.credentialId
            Log.d(SharedDataConfirmationFragment.tag, "credentialId:$credentialId")
            viewModel2.requestInfo.observe(viewLifecycleOwner) {
                viewModel.setRequestInfo(it)
            }
            viewModel2.subJwk.observe(viewLifecycleOwner) {
                if (!it.isNullOrBlank()) {
                    viewModel.setSubJwk(it)
                }
            }
            viewModel2.presentationDefinition.observe(viewLifecycleOwner) { it ->
                if (it != null) {
                    if (credentialId != null) {
                        viewModel.getData(credentialId, it)
                        viewModel2.setAccount(fragment, AccountUseCase.DEFAULT_IDENTIFIED_ACCOUNT)
                    } else {
                        viewModel.setEmptyClaims()
                        viewModel2.setAccount(fragment, AccountUseCase.DEFAULT_ANONYMOUS_ACCOUNT)
                    }
                }
            }
            setContent {
                SharedDataConfirmationView(
                    viewModel = viewModel,
                    linkOpener = { url ->
                        val builder = CustomTabsIntent.Builder()
                        val customTabsIntent = builder.build()
                        // Custom Tabs(アプリ内ブラウザ)のキャンセル時にロックさせないため(暫定)
                        // MainActivityのisLockingをfalseにセット
                        (activity as? MainActivity)?.setIsLocking(true)
                        customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
                    }) { selected ->
                    Log.d(SharedDataConfirmationFragment.tag, "size:${selected.size}")
                    val cred = viewModel.credential.value
                    val requestInfo = viewModel2.requestInfo.value
                    if (cred == null) {
                        // comment vc
                        val keyAlias = "randomKeyAlias_${System.currentTimeMillis()}"
                        val commentVcIssuer = CommentVcIssuer(keyAlias)
                        val commentVc = commentVcIssuer.issueCredential(
                            requestInfo!!.url,
                            requestInfo.comment,
                            ContentTruth.fromValue(requestInfo.boolValue)!!
                        )
                        viewModel.saveData(commentVc)
                        val types = MetadataUtil.extractTypes(commentVc.format, commentVc.credential)
                        val sc = SubmissionCredential(
                            id = commentVc.id,
                            format = commentVc.format,
                            types = types,
                            credential = commentVc.credential,
                            inputDescriptor = viewModel2.presentationDefinition.value!!.inputDescriptors[0],
                        )
                        viewModel2.shareToken(fragment, listOf(sc))
                    } else {
                        // comment vc
                        val keyAlias = Constants.Cryptography.KEY_BINDING
                        val commentVcIssuer = CommentVcIssuer(keyAlias)
                        val commentVc = commentVcIssuer.issueCredential(
                            requestInfo!!.url,
                            requestInfo.comment,
                            ContentTruth.fromValue(requestInfo.boolValue)!!
                        )
                        viewModel.saveData(commentVc)
                        val types = MetadataUtil.extractTypes(commentVc.format, commentVc.credential)
                        val sc = SubmissionCredential(
                            id = commentVc.id,
                            format = commentVc.format,
                            types = types,
                            credential = commentVc.credential,
                            inputDescriptor = viewModel2.presentationDefinition.value!!.inputDescriptors[0],
                        )
                        // organization vc
                        val types2 = MetadataUtil.extractTypes(cred.format, cred.credential)
                        val sc2 = SubmissionCredential(
                            id = cred.id,
                            format = cred.format,
                            types = types2,
                            credential = cred.credential,
                            inputDescriptor = viewModel.inputDescriptor!!,
                            selectedDisclosures = selected
                        )
                        viewModel2.shareToken(fragment, listOf(sc, sc2))
                    }
                }
            }
        }
    }

    private fun onUpdateProcessCompletion(done: Boolean) {
        if (done) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(getString(R.string.sharing_credential_done))
            builder.setMessage(getString(R.string.sharing_credential_done_support_text))
            builder.setPositiveButton(R.string.close) { dialog, id ->
                onUpdateCloseFragment(true)
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }

    private fun onUpdateCloseFragment(close: Boolean) {
        if (close) {
            requireActivity().finish()
        }
    }

    private fun onPostResult(postResult: TokenSendResult) {
        if (postResult.location != null) {
            val url = postResult.location
//            val action =
//                SharedDataConfirmationFragmentDirections.actionIdTokenSharringFlow3ToWebViewFragment(url)
//            findNavController().navigate(action)
//             val url: /*@@onzxnp@@*/kotlin.String? = "https://developers.android.com"
            val intent: CustomTabsIntent = CustomTabsIntent.Builder()
                .build()
            intent.launchUrl(requireContext(), android.net.Uri.parse(url))
        }
    }
}
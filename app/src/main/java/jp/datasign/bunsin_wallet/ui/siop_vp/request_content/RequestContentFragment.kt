package jp.datasign.bunsin_wallet.ui.siop_vp.request_content

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import jp.datasign.bunsin_wallet.MainActivity
import jp.datasign.bunsin_wallet.R
import jp.datasign.bunsin_wallet.oid.TokenSendResult
import jp.datasign.bunsin_wallet.pairwise.AccountUseCase
import jp.datasign.bunsin_wallet.ui.shared.CredentialSharingViewModel
import jp.datasign.bunsin_wallet.ui.siop_vp.TokenSharingFragmentMenuProvider
import jp.datasign.bunsin_wallet.utils.DisplayUtil

class RequestContentFragment : Fragment() {
    companion object {
        private val tag = RequestContentFragment::class.simpleName
    }

    private val args: RequestContentFragmentArgs by navArgs()
    private val sharedViewModel by activityViewModels<CredentialSharingViewModel>()
    private val viewModel: TokenSharingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
        }
        DisplayUtil.setFragmentTitle(
            activity as? AppCompatActivity,
            ""
        )
        val fragment = this
        return ComposeView(requireContext()).apply {
            viewModel.requestInfo.observe(viewLifecycleOwner) {
                if (it != null && it.responseType == "id_token") {
                    viewModel.setAccount(fragment, AccountUseCase.DEFAULT_IDENTIFIED_ACCOUNT)
                }
            }
            setContent {
                RequestContentView(viewModel = viewModel, linkOpener = { url ->
                    val builder = CustomTabsIntent.Builder()
                    val customTabsIntent = builder.build()
                    // Custom Tabs(アプリ内ブラウザ)のキャンセル時にロックさせないため(暫定)
                    // MainActivityのisLockingをfalseにセット
                    (activity as? MainActivity)?.setIsLocking(true)
                    customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
                }, closeHandler = {
                    onUpdateCloseFragment(true)
                }) {
                    // todo Permanently display the ID selection UI, then add information on the selected ID and connect it to the ID provision process.
                    if (viewModel.requestInfo.value?.responseType == "id_token") {
                        viewModel.shareToken(fragment, listOf())
                    } else {
                        val action =
                            RequestContentFragmentDirections.actionIdTokenSharringToFlow2()
                        findNavController().navigate(action)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()
        val menuProvider = TokenSharingFragmentMenuProvider(this, activity.menuInflater)
        activity.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewModel.tokenSendResult.observe(viewLifecycleOwner, ::onPostResult)

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.resetErrorMessage()
            }
        }

        if (!viewModel.isInitialized) {
            viewModel.isInitialized = true
            val url = args.siopRequest
            val index = args.index
            viewModel.accessPairwiseAccountManager(this, url, index)
        } else {
            Log.d(tag, "onViewCreated finish")
        }

        // 画面クローズ要求処理
        viewModel.shouldClose.observe(viewLifecycleOwner, ::onUpdateCloseFragment)

        // 処理成功通知
        viewModel.doneSuccessfully.observe(viewLifecycleOwner, ::onUpdateProcessCompletion)
    }

    private fun onUpdateProcessCompletion(done: Boolean) {
        if (done) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(getString(R.string.sharing_credential_done))
            builder.setMessage(getString(R.string.sharing_credential_done_support_text))
            builder.setPositiveButton(R.string.close) { dialog, id ->
                sharedViewModel.reset()
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

    private fun onPostResult(tokenSendResult: TokenSendResult) {
        if (tokenSendResult.location != null) {
            val url = tokenSendResult.location
            val intent: CustomTabsIntent = CustomTabsIntent.Builder()
                .build()
            intent.launchUrl(requireContext(), android.net.Uri.parse(url))
        }
    }
}
package jp.datasign.bunsin_wallet.ui.siop_vp.credential_selection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import jp.datasign.bunsin_wallet.datastore.CredentialDataStore
import jp.datasign.bunsin_wallet.ui.shared.CredentialSharingViewModel
import jp.datasign.bunsin_wallet.ui.siop_vp.TokenSharingFragmentMenuProvider
import jp.datasign.bunsin_wallet.ui.siop_vp.request_content.TokenSharingViewModel

class CredentialSelectionFragment : Fragment() {
    val sharedViewModel by activityViewModels<CredentialSharingViewModel>()
    val viewModel: CertificateSelectionViewModel by viewModels()
    val viewModel2: TokenSharingViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val activity = requireActivity()
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
        val menuProvider = TokenSharingFragmentMenuProvider(this, activity.menuInflater)
        activity.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val credentialDataStore = CredentialDataStore.getInstance(requireContext())
        viewModel.setCredentialDataStore(credentialDataStore)
        viewModel2.presentationDefinition.observe(viewLifecycleOwner) {
            if (it != null) {
                viewModel.getData(it)
            }
        }
        return ComposeView(requireContext()).apply {
            setContent {
                CertificateSelectionView(viewModel = viewModel) { credentialId ->
                    val action =
                        CredentialSelectionFragmentDirections.actionIdTokenSharringToFlow3(
                            credentialId = credentialId
                        )
                    findNavController().navigate(action)
                }
            }
        }
    }
}
package jp.datasign.bunsin_wallet.ui.recipient

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import jp.datasign.bunsin_wallet.R
import jp.datasign.bunsin_wallet.databinding.FragmentRecipientBinding
import jp.datasign.bunsin_wallet.datastore.CredentialSharingHistoryStore

import jp.datasign.bunsin_wallet.utils.DisplayUtil

class RecipientFragment : Fragment() {

    private var _binding: FragmentRecipientBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        DisplayUtil.setFragmentTitle(
            activity as? AppCompatActivity, getString(R.string.title_recipient)
        )

        val credentialSharingHistoryStore =
            CredentialSharingHistoryStore.getInstance(requireContext())
        val recipientViewModel =
            ViewModelProvider(
                requireActivity(),
                RecipientViewModelFactory(credentialSharingHistoryStore)
            ).get(RecipientViewModel::class.java)

        _binding = FragmentRecipientBinding.inflate(inflater, container, false)

        val root: View = binding.root

        recipientViewModel.text.observe(viewLifecycleOwner) {
            binding.textDefault.text = it
        }

        recipientViewModel.sharingHistories.observe(viewLifecycleOwner) { histories ->
            if (histories != null) {
                val latest = getLatestHistoriesByRp(histories)
                val itemList = latest.itemsList

                if (itemList.isNullOrEmpty()) {
                    binding.textDefault.visibility = View.VISIBLE
                    binding.recipientList.visibility = View.GONE
                } else {
                    binding.textDefault.visibility = View.GONE
                    binding.recipientList.visibility = View.VISIBLE

                    val hasAnyLogo = itemList.any { it.rpLogoUrl != "" }
                    val adapter = CredentialHistoryAdapter(itemList, hasAnyLogo)
                    binding.recipientList.layoutManager = LinearLayoutManager(context)
                    binding.recipientList.adapter = adapter
                }
            } else {
                Log.d(tag, "empty sharing histories")
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


class CredentialHistoryAdapter(
    private val histories: List<jp.datasign.bunsin_wallet.datastore.CredentialSharingHistory>,
    private val hasAnyLogo: Boolean
) :
    RecyclerView.Adapter<CredentialHistoryAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rpNameTextView: TextView = view.findViewById(R.id.rp_name)
        val lastProvidedDateView: TextView = view.findViewById(R.id.last_provided_date)
        val imageView = view.findViewById<ImageView>(R.id.image_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipient, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val history = histories[position]
        holder.rpNameTextView.text = history.rpName
        holder.lastProvidedDateView.text = timestampToString(history.createdAt)

        if (hasAnyLogo) {
            holder.imageView.visibility = View.INVISIBLE
        }

        if (history.rpLogoUrl != "") {
            holder.imageView.visibility = View.VISIBLE
            Glide.with(holder.itemView)
                .load(history.rpLogoUrl)
                .into(holder.imageView)
        }

        holder.itemView.setOnClickListener {
            val action = RecipientFragmentDirections.actionToRecipientDetail(
                history.rp,
                history.rpName,
                history.rpLocation,
                history.rpPrivacyPolicyUrl,
                history.rpLogoUrl
            )
            it.findNavController().navigate(action)
        }
    }

    private fun Bundle.putCredentialSharingHistory(
        key: String,
        credentialSharingHistory: jp.datasign.bunsin_wallet.datastore.CredentialSharingHistory,
    ) {
        putByteArray(key, credentialSharingHistory.toByteArray())
    }

    override fun getItemCount() = histories.size
}


class RecipientViewModelFactory(private val credentialSharingHistoryStore: CredentialSharingHistoryStore) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecipientViewModel::class.java)) {
            return RecipientViewModel(credentialSharingHistoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}